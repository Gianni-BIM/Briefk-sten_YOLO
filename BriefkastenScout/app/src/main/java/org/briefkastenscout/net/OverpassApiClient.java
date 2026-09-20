package org.briefkastenscout.net;

import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.briefkastenscout.model.BriefkastenRecord;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client für Overpass-API-Abfragen zur Ermittlung von Briefkästen in OpenStreetMap.
 * Ausführung im Hintergrund via ExecutorService mit automatischer Fallback-Mirror-Logik
 * und sauberer Trennung von MATCH, MISSING und ERROR.
 */
public class OverpassApiClient {

    private static final String TAG = "OverpassApiClient";

    // Endpunkte
    private static final String PRIMARY_ENDPOINT = "https://overpass-api.de/api/interpreter";
    private static final String FALLBACK_ENDPOINT = "https://overpass.kumi.systems/api/interpreter";

    // Timeouts: 10s Verbindung, 15s Lesen
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    public interface OnOsmCheckListener {
        void onOsmCheckCompleted(long recordId, String status, String osmId, Double distance, String errorMsg);
    }

    private final ExecutorService executorService;
    private final Handler mainHandler;

    public OverpassApiClient() {
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Startet die asynchrone Overpass-Prüfung für einen Datensatz.
     *
     * @param record   Der Briefkasten-Datensatz mit Koordinaten.
     * @param listener Callback für das Ergebnis (wird garantiert auf dem Main Thread aufgerufen).
     */
    public void checkBriefkastenInOsm(BriefkastenRecord record, OnOsmCheckListener listener) {
        final long recordId = record.getId();
        final double lat = record.getLatitude();
        final double lon = record.getLongitude();

        executorService.execute(() -> {
            // Overpass QL Query erstellen: Suche nach Briefkästen im Umkreis von 30 Metern
            String query = String.format(Locale.US,
                    "[out:json][timeout:25];\n" +
                            "(\n" +
                            "  nwr[\"amenity\"=\"post_box\"](around:30,%.7f,%.7f);\n" +
                            ");\n" +
                            "out center;", lat, lon);

            // 1. Versuch: Primärer Server
            OsmQueryResult result = executeQuery(PRIMARY_ENDPOINT, query, lat, lon);

            // 2. Versuch: Bei Fehler automatischer Fallback auf zweiten Mirror
            if (result.isError) {
                Log.w(TAG, "Primärer Overpass-Server fehlgeschlagen (" + result.errorMessage + "). Versuche Fallback...");
                result = executeQuery(FALLBACK_ENDPOINT, query, lat, lon);
            }

            // Ergebnis vorbereiten
            final OsmQueryResult finalResult = result;
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onOsmCheckCompleted(
                            recordId,
                            finalResult.status,
                            finalResult.osmId,
                            finalResult.distance,
                            finalResult.errorMessage
                    );
                }
            });
        });
    }

    /**
     * Führt die HTTP-POST-Anfrage gegen den angegebenen Overpass-Endpunkt aus.
     */
    private OsmQueryResult executeQuery(String endpoint, String query, double userLat, double userLon) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("User-Agent", "BriefkastenScout-Android-App/1.0 (Geodata Master Project)");

            // POST-Body mit data= URL-encodieren
            String postData = "data=" + URLEncoder.encode(query, "UTF-8");
            byte[] postDataBytes = postData.getBytes(StandardCharsets.UTF_8);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(postDataBytes);
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Antwort einlesen
                String responseBody = readStream(connection.getInputStream());
                return parseJsonResponse(responseBody, userLat, userLon);
            } else {
                String errorBody = readStream(connection.getErrorStream());
                return OsmQueryResult.error("HTTP-Fehler " + responseCode + ": " + (errorBody.isEmpty() ? connection.getResponseMessage() : errorBody));
            }

        } catch (Exception ex) {
            Log.e(TAG, "Fehler bei Abfrage an " + endpoint + ": " + ex.getMessage(), ex);
            return OsmQueryResult.error(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Parst das JSON der Overpass-Antwort.
     * Nur wenn der HTTP-Call 200 liefert und das JSON valide ist, wird zwischen MATCH und MISSING entschieden.
     */
    private OsmQueryResult parseJsonResponse(String jsonString, double userLat, double userLon) {
        try {
            JSONObject root = new JSONObject(jsonString);
            JSONArray elements = root.optJSONArray("elements");

            if (elements == null) {
                return OsmQueryResult.error("Ungültige Antwort: Kein 'elements'-Array im JSON");
            }

            if (elements.length() == 0) {
                // Erfolgreiche Antwort, aber kein Briefkasten im 30m-Radius gefunden -> MISSING
                return OsmQueryResult.missing();
            }

            // Mindestens 1 Treffer gefunden -> Nächstgelegenes Objekt ermitteln
            String bestOsmId = null;
            double minDistance = Double.MAX_VALUE;

            for (int i = 0; i < elements.length(); i++) {
                JSONObject elem = elements.getJSONObject(i);
                String type = elem.optString("type", "osm");
                long id = elem.optLong("id", 0);
                String fullId = type + "/" + id;

                double elemLat = 0;
                double elemLon = 0;
                boolean coordsFound = false;

                if (elem.has("lat") && elem.has("lon")) {
                    elemLat = elem.getDouble("lat");
                    elemLon = elem.getDouble("lon");
                    coordsFound = true;
                } else if (elem.has("center")) {
                    JSONObject center = elem.getJSONObject("center");
                    elemLat = center.getDouble("lat");
                    elemLon = center.getDouble("lon");
                    coordsFound = true;
                }

                if (coordsFound) {
                    float[] distResult = new float[1];
                    Location.distanceBetween(userLat, userLon, elemLat, elemLon, distResult);
                    double dist = distResult[0];

                    if (dist < minDistance) {
                        minDistance = dist;
                        bestOsmId = fullId;
                    }
                } else if (bestOsmId == null) {
                    bestOsmId = fullId;
                    minDistance = 0.0;
                }
            }

            return OsmQueryResult.match(bestOsmId, minDistance);

        } catch (Exception e) {
            return OsmQueryResult.error("JSON-Parse-Fehler: " + e.getMessage());
        }
    }

    private String readStream(InputStream is) {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Interner Ergebnis-Container
     */
    private static class OsmQueryResult {
        final String status;
        final String osmId;
        final Double distance;
        final String errorMessage;
        final boolean isError;

        private OsmQueryResult(String status, String osmId, Double distance, String errorMessage, boolean isError) {
            this.status = status;
            this.osmId = osmId;
            this.distance = distance;
            this.errorMessage = errorMessage;
            this.isError = isError;
        }

        static OsmQueryResult match(String osmId, double distance) {
            return new OsmQueryResult(BriefkastenRecord.STATUS_MATCH, osmId, distance, null, false);
        }

        static OsmQueryResult missing() {
            return new OsmQueryResult(BriefkastenRecord.STATUS_MISSING, null, null, null, false);
        }

        static OsmQueryResult error(String message) {
            return new OsmQueryResult(BriefkastenRecord.STATUS_ERROR, null, null, message, true);
        }
    }

    public void shutdown() {
        executorService.shutdown();
    }
}
