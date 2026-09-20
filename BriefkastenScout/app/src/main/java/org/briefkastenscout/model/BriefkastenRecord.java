package org.briefkastenscout.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Datenmodell für einen erfassten Briefkasten (amenity=post_box) bzw. Point of Interest.
 * Enthält ID, Dateipfad des Fotos, GPS-Koordinaten, Zeitstempel,
 * die Ergebnisse der Overpass-API-Prüfung gegen OpenStreetMap sowie
 * die Ergebnisse der On-Device YOLO-Bilderkennung (TensorFlow Lite).
 */
public class BriefkastenRecord {

    // Status-Konstanten für den OSM-Abgleich
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_MATCH   = "MATCH";    // Bereits in OSM erfasst (min. 1 Objekt im 30m-Radius)
    public static final String STATUS_MISSING = "MISSING";  // Overpass 200 OK, aber keine Objekte im 30m-Radius
    public static final String STATUS_ERROR   = "ERROR";    // Timeout, HTTP != 200, kein Netz oder ungültige Antwort

    // Status-Konstanten für die visuelle On-Device YOLO-Bilderkennung
    public static final String VISUAL_PENDING      = "VISUAL_PENDING";
    public static final String VISUAL_DETECTED     = "VISUAL_DETECTED";     // Briefkasten auf Foto erkannt
    public static final String VISUAL_NOT_DETECTED = "VISUAL_NOT_DETECTED"; // Kein Briefkasten auf Foto erkannt
    public static final String VISUAL_ERROR        = "VISUAL_ERROR";        // Modelllade- oder Inferenzfehler

    private long id;
    private String imagePath;
    private double latitude;
    private double longitude;
    private long timestamp;

    // Overpass / OSM-Ergebnisdaten
    private String osmStatus;        // PENDING, MATCH, MISSING, ERROR
    private String osmId;            // z. B. "way/12345678"
    private Double osmDistance;      // Distanz in Metern zum gefundenen Objekt
    private String osmErrorMessage;  // Fehlermeldung bei STATUS_ERROR

    // Visuelle YOLO / TFLite-Ergebnisdaten
    private String visualStatus;         // VISUAL_PENDING, VISUAL_DETECTED, VISUAL_NOT_DETECTED, VISUAL_ERROR
    private Float visualConfidence;      // z. B. 0.942f (Konfidenz 0.0 - 1.0)
    private String visualErrorMessage;   // Fehlermeldung bei VISUAL_ERROR

    public BriefkastenRecord() {
        this.osmStatus = STATUS_PENDING;
        this.visualStatus = VISUAL_PENDING;
    }

    public BriefkastenRecord(String imagePath, double latitude, double longitude, long timestamp) {
        this.imagePath = imagePath;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.osmStatus = STATUS_PENDING;
        this.visualStatus = VISUAL_PENDING;
    }

    public BriefkastenRecord(long id, String imagePath, double latitude, double longitude, long timestamp,
                       String osmStatus, String osmId, Double osmDistance, String osmErrorMessage) {
        this.id = id;
        this.imagePath = imagePath;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.osmStatus = osmStatus != null ? osmStatus : STATUS_PENDING;
        this.osmId = osmId;
        this.osmDistance = osmDistance;
        this.osmErrorMessage = osmErrorMessage;
        this.visualStatus = VISUAL_PENDING;
    }

    public BriefkastenRecord(long id, String imagePath, double latitude, double longitude, long timestamp,
                       String osmStatus, String osmId, Double osmDistance, String osmErrorMessage,
                       String visualStatus, Float visualConfidence, String visualErrorMessage) {
        this.id = id;
        this.imagePath = imagePath;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.osmStatus = osmStatus != null ? osmStatus : STATUS_PENDING;
        this.osmId = osmId;
        this.osmDistance = osmDistance;
        this.osmErrorMessage = osmErrorMessage;
        this.visualStatus = visualStatus != null ? visualStatus : VISUAL_PENDING;
        this.visualConfidence = visualConfidence;
        this.visualErrorMessage = visualErrorMessage;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    // --- OSM Getter / Setter ---

    public String getOsmStatus() {
        return osmStatus;
    }

    public void setOsmStatus(String osmStatus) {
        this.osmStatus = osmStatus;
    }

    public String getOsmId() {
        return osmId;
    }

    public void setOsmId(String osmId) {
        this.osmId = osmId;
    }

    public Double getOsmDistance() {
        return osmDistance;
    }

    public void setOsmDistance(Double osmDistance) {
        this.osmDistance = osmDistance;
    }

    public String getOsmErrorMessage() {
        return osmErrorMessage;
    }

    public void setOsmErrorMessage(String osmErrorMessage) {
        this.osmErrorMessage = osmErrorMessage;
    }

    // --- Visual YOLO Getter / Setter ---

    public String getVisualStatus() {
        return visualStatus;
    }

    public void setVisualStatus(String visualStatus) {
        this.visualStatus = visualStatus;
    }

    public Float getVisualConfidence() {
        return visualConfidence;
    }

    public void setVisualConfidence(Float visualConfidence) {
        this.visualConfidence = visualConfidence;
    }

    public String getVisualErrorMessage() {
        return visualErrorMessage;
    }

    public void setVisualErrorMessage(String visualErrorMessage) {
        this.visualErrorMessage = visualErrorMessage;
    }

    // ==========================================
    // STATUS-HILFSMETHODEN (OSM)
    // ==========================================

    public boolean isOsmMatched() {
        return STATUS_MATCH.equals(osmStatus);
    }

    public boolean isOsmMissing() {
        return STATUS_MISSING.equals(osmStatus);
    }

    public boolean isOsmError() {
        return STATUS_ERROR.equals(osmStatus);
    }

    public boolean isOsmPending() {
        return STATUS_PENDING.equals(osmStatus);
    }

    // ==========================================
    // STATUS-HILFSMETHODEN (Visuelle YOLO-Erkennung)
    // ==========================================

    public boolean isVisualDetected() {
        return VISUAL_DETECTED.equals(visualStatus);
    }

    public boolean isVisualNotDetected() {
        return VISUAL_NOT_DETECTED.equals(visualStatus);
    }

    public boolean isVisualError() {
        return VISUAL_ERROR.equals(visualStatus);
    }

    public boolean isVisualPending() {
        return VISUAL_PENDING.equals(visualStatus);
    }

    /**
     * Gibt die formatierte Konfidenz in Prozent zurück (z. B. "94.2%").
     */
    public String getFormattedVisualConfidence() {
        if (visualConfidence == null) return "";
        return String.format(Locale.getDefault(), "%.1f%%", visualConfidence * 100f);
    }

    /**
     * Formatiert den Zeitstempel für die Benutzeroberfläche (z. B. "17.09.2026 14:35:10").
     */
    public String getFormattedTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    /**
     * Gibt die Koordinaten formatiert mit 5 Nachkommastellen zurück.
     */
    public String getFormattedCoordinates() {
        String latHemisphere = latitude >= 0 ? "N" : "S";
        String lngHemisphere = longitude >= 0 ? "E" : "W";
        return String.format(Locale.US, "%.5f° %s, %.5f° %s",
                Math.abs(latitude), latHemisphere,
                Math.abs(longitude), lngHemisphere);
    }

    /**
     * Gibt die gerundete Distanz in Metern zurück (z. B. "14.2 m").
     */
    public String getFormattedOsmDistance() {
        if (osmDistance == null) return "";
        return String.format(Locale.getDefault(), "%.1f m", osmDistance);
    }
}
