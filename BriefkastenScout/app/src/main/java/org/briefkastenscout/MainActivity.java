package org.briefkastenscout;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.briefkastenscout.adapter.BriefkastenRecordAdapter;
import org.briefkastenscout.db.BriefkastenDbHelper;
import org.briefkastenscout.ml.BriefkastenDetector;
import org.briefkastenscout.model.BriefkastenRecord;
import org.briefkastenscout.net.OverpassApiClient;
import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.Icon;
import org.maplibre.android.annotations.IconFactory;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Haupt-Activity der App "BriefkastenScout".
 * Integriert MapLibre GL Vektorkarte, GPS via FusedLocationProviderClient,
 * native Kameraaufnahme mit FileProvider, SQLite-Persistierung,
 * automatische Overpass-API-Prüfung gegen OpenStreetMap (Briefkasten / amenity=post_box)
 * und RecyclerView-Übersicht mit farbcodierten Status-Badges und Markern.
 */
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, BriefkastenRecordAdapter.OnRecordActionListener {

    // Freier OpenStreetMap-basierter Vektor-Kartenstil (OpenFreeMap, kein API-Key nötig)
    private static final String MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";

    // UI Komponenten
    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private FloatingActionButton fabLocate;
    private FloatingActionButton fabCamera;
    private FloatingActionButton fabToggleList;
    private Chip chipRecordsCount;
    private TextView tvStatusInfo;
    private TextView tvEmptyState;
    private RecyclerView recyclerViewRecords;
    private BottomSheetBehavior<View> bottomSheetBehavior;

    // Daten & Adapter
    private BriefkastenDbHelper dbHelper;
    private BriefkastenRecordAdapter recordAdapter;
    private final Map<Long, Marker> briefkastenMarkersMap = new HashMap<>();
    private Marker userLocationMarker;

    // Location & Kamera
    private FusedLocationProviderClient fusedLocationClient;
    private String currentPhotoPath;
    private Uri currentPhotoUri;

    // Kombinierte Statusanzeige: OSM- und Visual-Check laufen parallel und unabhängig
    // voneinander ab; beide Texte werden getrennt gehalten, damit keiner der beiden
    // Werte den anderen überschreibt, egal welche Prüfung zuerst fertig wird.
    private long statusDisplayRecordId = -1;
    private String lastOsmStatusText = "";
    private String lastVisualStatusText = "";

    // Overpass API Client & On-Device YOLO Briefkasten-Detector
    private OverpassApiClient overpassApiClient;
    private BriefkastenDetector briefkastenDetector;

    // Permission Launcher
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private ActivityResultLauncher<String[]> cameraAndLocationPermissionLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. MapLibre Native Instanz initialisieren
        MapLibre.getInstance(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 2. Datenbank-Helper, Overpass Client & YOLO Detector initialisieren
        dbHelper = new BriefkastenDbHelper(this);
        overpassApiClient = new OverpassApiClient();
        briefkastenDetector = new BriefkastenDetector(this);

        // 3. Location Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 4. Views referenzieren
        initViews(savedInstanceState);

        // 5. Activity Result Launchers registrieren
        initLaunchers();

        // 6. Listeners konfigurieren
        initListeners();

        // 7. RecyclerView initialisieren
        initRecyclerView();
    }

    private void initViews(Bundle savedInstanceState) {
        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        fabLocate = findViewById(R.id.fab_locate);
        fabCamera = findViewById(R.id.fab_camera);
        fabToggleList = findViewById(R.id.fab_toggle_list);
        chipRecordsCount = findViewById(R.id.chip_records_count);
        tvStatusInfo = findViewById(R.id.tv_status_info);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        recyclerViewRecords = findViewById(R.id.recycler_view_records);

        View bottomSheetView = findViewById(R.id.bottom_sheet_records);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetView);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    private void initRecyclerView() {
        recyclerViewRecords.setLayoutManager(new LinearLayoutManager(this));
        recordAdapter = new BriefkastenRecordAdapter(this, this);
        recyclerViewRecords.setAdapter(recordAdapter);
    }

    private void initLaunchers() {
        // Launcher für Ortungsberechtigung
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fineLocation = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseLocation = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                    if ((fineLocation != null && fineLocation) || (coarseLocation != null && coarseLocation)) {
                        getCurrentLocationAndCenterMap();
                    } else {
                        Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // Launcher für Foto- und Standortberechtigungen
        cameraAndLocationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean cameraGranted = result.getOrDefault(Manifest.permission.CAMERA, false);
                    Boolean fineLoc = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseLoc = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

                    if (cameraGranted != null && cameraGranted) {
                        launchCameraCapture();
                    } else {
                        Toast.makeText(this, R.string.permission_camera_rationale, Toast.LENGTH_LONG).show();
                    }
                }
        );

        // Launcher für das Aufnehmen eines Fotos mit der nativen Kamera-App
        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                isSuccess -> {
                    if (isSuccess && currentPhotoPath != null) {
                        onPhotoCapturedSuccess();
                    } else {
                        // Bei Abbruch temporäre unvollständige Datei aufräumen
                        if (currentPhotoPath != null) {
                            File file = new File(currentPhotoPath);
                            if (file.exists() && file.length() == 0) {
                                file.delete();
                            }
                        }
                        Toast.makeText(this, R.string.photo_capture_failed, Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // Launcher für den Bild-Import aus der Galerie (Alternative zur Live-Kamera,
        // z. B. für ein bereits vorhandenes Foto eines echten Briefkastens)
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        importImageFromGallery(uri);
                    }
                }
        );
    }

    private void initListeners() {
        // Ortungs-Button Klick
        fabLocate.setOnClickListener(v -> checkAndRequestLocationPermission());

        // Kamera-Button Klick
        fabCamera.setOnClickListener(v -> checkAndRequestCameraPermissions());
        // Kamera-Button lang gedrückt: Bild aus Galerie importieren statt live fotografieren
        fabCamera.setOnLongClickListener(v -> {
            checkAndRequestGalleryPick();
            return true;
        });

        // Toggle Listenansicht (Bottom Sheet)
        fabToggleList.setOnClickListener(v -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        // Klick auf den Header des BottomSheets klappt es auf/zu
        View bottomSheetHeader = findViewById(R.id.bottom_sheet_header);
        if (bottomSheetHeader != null) {
            bottomSheetHeader.setOnClickListener(v -> {
                if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                } else {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }
            });
        }
    }

    // ==========================================
    // MAPLIBRE GL KARTEN-INITIALISIERUNG
    // ==========================================

    @Override
    public void onMapReady(@NonNull MapLibreMap map) {
        this.mapLibreMap = map;

        // Vektor-Kartenstil laden
        map.setStyle(new Style.Builder().fromUri(MAP_STYLE_URL), style -> {
            tvStatusInfo.setText("Karte bereit (OSM Vector Tiles)");

            // Startposition z. B. auf Deutschland zentrieren (Zoom 11)
            map.setCameraPosition(new CameraPosition.Builder()
                    .target(new LatLng(48.137154, 11.576124))
                    .zoom(11.0)
                    .build());

            // Alle bisher in der Datenbank gespeicherten Briefkästen auf der Karte markieren
            loadRecordsAndRefreshUI();

            // Automatische Erstortung versuchen, wenn Berechtigung bereits vorliegt
            if (hasLocationPermission()) {
                getCurrentLocationAndCenterMap();
            }
        });
    }

    /**
     * Lädt alle Datensätze aus SQLite, aktualisiert die RecyclerView und platziert
     * farbcodierte Marker auf der MapLibre-Karte (Grün = MATCH, Rot = MISSING, Grau = ERROR).
     */
    private void loadRecordsAndRefreshUI() {
        List<BriefkastenRecord> records = dbHelper.getAllRecords();

        // RecyclerView aktualisieren
        recordAdapter.setRecords(records);
        chipRecordsCount.setText(records.size() + (records.size() == 1 ? " Foto" : " Fotos"));

        if (records.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerViewRecords.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerViewRecords.setVisibility(View.VISIBLE);
        }

        // Marker auf der MapLibre-Karte aktualisieren
        if (mapLibreMap != null) {
            // Vorherige Briefkasten-Marker entfernen
            for (Marker marker : briefkastenMarkersMap.values()) {
                mapLibreMap.removeMarker(marker);
            }
            briefkastenMarkersMap.clear();

            for (BriefkastenRecord record : records) {
                if (record.getLatitude() != 0.0 || record.getLongitude() != 0.0) {
                    Icon markerIcon = getMarkerIconForRecord(record);
                    String snippetText = getSnippetTextForRecord(record);

                    MarkerOptions options = new MarkerOptions()
                            .position(new LatLng(record.getLatitude(), record.getLongitude()))
                            .title("Briefkasten #" + record.getId())
                            .snippet(snippetText);

                    if (markerIcon != null) {
                        options.icon(markerIcon);
                    }

                    Marker marker = mapLibreMap.addMarker(options);
                    briefkastenMarkersMap.put(record.getId(), marker);
                }
            }
        }
    }

    private String getSnippetTextForRecord(BriefkastenRecord record) {
        StringBuilder sb = new StringBuilder();
        if (record.isOsmMatched()) {
            sb.append("✓ In OSM erfasst");
        } else if (record.isOsmMissing()) {
            sb.append("✗ Nicht in OSM erfasst (Neu)");
        } else if (record.isOsmError()) {
            sb.append("⚠ OSM-Prüfung fehlgeschlagen");
        } else {
            sb.append("⟳ Prüfe OSM-Status...");
        }
        sb.append("\n").append(record.getFormattedTimestamp());
        return sb.toString();
    }

    /**
     * Erzeugt ein MapLibre Icon abhängig vom OSM-Status:
     * - MATCH: Grün (ic_marker_match)
     * - MISSING: Rot (ic_marker_missing)
     * - ERROR: Grau (ic_marker_error)
     * - PENDING: Standard Orange (ic_briefkasten_marker)
     */
    private Icon getMarkerIconForRecord(BriefkastenRecord record) {
        int drawableId;
        if (record.isOsmMatched()) {
            drawableId = R.drawable.ic_marker_match;
        } else if (record.isOsmMissing()) {
            drawableId = R.drawable.ic_marker_missing;
        } else if (record.isOsmError()) {
            drawableId = R.drawable.ic_marker_error;
        } else {
            drawableId = R.drawable.ic_briefkasten_marker;
        }
        return drawableToIcon(drawableId);
    }

    private Icon drawableToIcon(int drawableId) {
        try {
            Drawable drawable = ContextCompat.getDrawable(this, drawableId);
            if (drawable == null) return null;

            int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 72;
            int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 72;

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);

            return IconFactory.getInstance(this).fromBitmap(bitmap);
        } catch (Exception e) {
            return null;
        }
    }

    // ==========================================
    // STANDORT / GPS VIA FUSED LOCATION CLIENT
    // ==========================================

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void checkAndRequestLocationPermission() {
        if (hasLocationPermission()) {
            getCurrentLocationAndCenterMap();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void getCurrentLocationAndCenterMap() {
        if (!hasLocationPermission()) return;

        tvStatusInfo.setText("GPS-Position wird ermittelt...");

        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            onLocationReceived(location);
                        } else {
                            fusedLocationClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                                if (lastLoc != null) {
                                    onLocationReceived(lastLoc);
                                } else {
                                    tvStatusInfo.setText("Kein GPS-Fix verfügbar");
                                    Toast.makeText(this, R.string.location_not_available, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    })
                    .addOnFailureListener(e -> {
                        tvStatusInfo.setText("Fehler bei Ortung");
                        Toast.makeText(this, R.string.location_not_available, Toast.LENGTH_SHORT).show();
                    });
        } catch (SecurityException se) {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
        }
    }

    private void onLocationReceived(Location location) {
        LatLng userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        tvStatusInfo.setText(String.format(Locale.US, "GPS: %.4f, %.4f (Genauigkeit: %.1fm)",
                location.getLatitude(), location.getLongitude(), location.getAccuracy()));

        if (mapLibreMap != null) {
            mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 16.0));

            if (userLocationMarker != null) {
                mapLibreMap.removeMarker(userLocationMarker);
            }
            userLocationMarker = mapLibreMap.addMarker(new MarkerOptions()
                    .position(userLatLng)
                    .title("Mein Standort")
                    .snippet("Genauigkeit: " + String.format(Locale.US, "%.1fm", location.getAccuracy()))
            );
        }
    }

    // ==========================================
    // KAMERA & FOTO-AUFNAHME MIT FILEPROVIDER
    // ==========================================

    private void checkAndRequestCameraPermissions() {
        boolean hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean hasLoc = hasLocationPermission();

        if (hasCamera && hasLoc) {
            launchCameraCapture();
        } else {
            cameraAndLocationPermissionLauncher.launch(new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void launchCameraCapture() {
        try {
            File picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (picturesDir == null) {
                picturesDir = getFilesDir();
            }

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File photoFile = new File(picturesDir, "BRIEFKASTEN_" + timeStamp + ".jpg");
            currentPhotoPath = photoFile.getAbsolutePath();

            currentPhotoUri = FileProvider.getUriForFile(
                    this,
                    "org.briefkastenscout.fileprovider",
                    photoFile
            );

            takePictureLauncher.launch(currentPhotoUri);

        } catch (Exception ex) {
            Toast.makeText(this, "Fehler beim Vorbereiten der Kamera: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Prüft die Standort-Berechtigung (für den GPS-Tag des importierten Bildes) und
     * öffnet danach den System-Bildauswähler. Keine Kamera-Berechtigung nötig.
     */
    private void checkAndRequestGalleryPick() {
        if (hasLocationPermission()) {
            pickImageLauncher.launch("image/*");
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            pickImageLauncher.launch("image/*");
        }
    }

    /**
     * Kopiert ein aus der Galerie gewähltes Bild in den App-eigenen Bilderordner (analog zur
     * Kameraaufnahme) und stößt danach denselben GPS-/Speicher-/Prüf-Ablauf wie bei einem
     * echten Foto an. Nützlich, um die visuelle Erkennung mit einem bereits vorhandenen,
     * echten Foto eines Briefkastens zu testen, wenn gerade keine Live-Aufnahme möglich ist.
     */
    private void importImageFromGallery(Uri sourceUri) {
        try {
            File picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (picturesDir == null) {
                picturesDir = getFilesDir();
            }
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File destFile = new File(picturesDir, "BRIEFKASTEN_IMPORT_" + timeStamp + ".jpg");

            try (java.io.InputStream in = getContentResolver().openInputStream(sourceUri);
                 java.io.OutputStream out = new java.io.FileOutputStream(destFile)) {
                if (in == null) {
                    Toast.makeText(this, "Konnte gewähltes Bild nicht öffnen.", Toast.LENGTH_SHORT).show();
                    return;
                }
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            currentPhotoPath = destFile.getAbsolutePath();
            onPhotoCapturedSuccess();

        } catch (Exception ex) {
            Toast.makeText(this, "Fehler beim Importieren: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Foto wurde aufgenommen -> GPS-Position ermitteln und mit Status PENDING speichern,
     * danach sofort Overpass API Abfrage starten.
     */
    private void onPhotoCapturedSuccess() {
        if (!hasLocationPermission()) {
            saveRecordWithLocation(0.0, 0.0);
            return;
        }

        tvStatusInfo.setText("Ermittle GPS für Foto...");
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            saveRecordWithLocation(location.getLatitude(), location.getLongitude());
                        } else {
                            fusedLocationClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                                if (lastLoc != null) {
                                    saveRecordWithLocation(lastLoc.getLatitude(), lastLoc.getLongitude());
                                } else {
                                    saveRecordWithLocation(0.0, 0.0);
                                }
                            });
                        }
                    })
                    .addOnFailureListener(e -> saveRecordWithLocation(0.0, 0.0));
        } catch (SecurityException e) {
            saveRecordWithLocation(0.0, 0.0);
        }
    }

    private void saveRecordWithLocation(double latitude, double longitude) {
        long timestamp = System.currentTimeMillis();
        BriefkastenRecord record = new BriefkastenRecord(currentPhotoPath, latitude, longitude, timestamp);
        record.setOsmStatus(BriefkastenRecord.STATUS_PENDING);
        record.setVisualStatus(BriefkastenRecord.VISUAL_PENDING);

        // 1. In SQLite speichern
        long newId = dbHelper.insertRecord(record);

        // 2. UI & Karte synchronisieren
        loadRecordsAndRefreshUI();

        // 3. Karte auf den neu erfassten Briefkasten zentrieren
        if (mapLibreMap != null && (latitude != 0.0 || longitude != 0.0)) {
            LatLng briefkastenLatLng = new LatLng(latitude, longitude);
            mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(briefkastenLatLng, 16.5));
        }

        Toast.makeText(this, R.string.photo_saved_success, Toast.LENGTH_SHORT).show();

        // Kombinierte Statusanzeige für diesen neuen Datensatz zurücksetzen
        statusDisplayRecordId = newId;
        lastOsmStatusText = "OSM: wird geprüft...";
        lastVisualStatusText = "Visuell: wird analysiert...";
        updateCombinedStatusDisplay(newId);

        // 4. Parallele Prüfung 1: Overpass API Hintergrundabfrage (wenn Koordinaten vorhanden)
        if (latitude != 0.0 || longitude != 0.0) {
            startOsmCheckForRecord(record);
        }

        // 5. Parallele Prüfung 2: Visuelle On-Device YOLO-Bilderkennung (TensorFlow Lite)
        startVisualDetectionForRecord(record);
    }

    /**
     * Rendert OSM- und Visual-Status gemeinsam in einer Zeile. Beide Prüfungen laufen
     * unabhängig und asynchron ab; welche zuerst fertig wird, überschreibt hier nie
     * das Ergebnis der anderen, da jede nur ihr eigenes Text-Feld aktualisiert.
     */
    private void updateCombinedStatusDisplay(long recordId) {
        if (recordId != statusDisplayRecordId) return;
        tvStatusInfo.setText("Briefkasten #" + recordId + " – " + lastOsmStatusText + " • " + lastVisualStatusText);
    }

    // ==========================================
    // VISUELLE ON-DEVICE YOLO-ERKENNUNG (TFLITE)
    // ==========================================

    private void startVisualDetectionForRecord(BriefkastenRecord record) {
        if (record == null || record.getImagePath() == null) return;

        new Thread(() -> {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(record.getImagePath());
                if (bitmap != null) {
                    briefkastenDetector.detect(record.getId(), bitmap, (recordId, visualStatus, confidence, errorMessage) -> {
                        // 1. In SQLite persistieren
                        dbHelper.updateVisualStatus(recordId, visualStatus, confidence, errorMessage);

                        // 2. UI & Karte synchronisieren
                        loadRecordsAndRefreshUI();

                        // 3. Nutzer-Feedback (für alle Ergebnisse, nicht nur Treffer)
                        if (BriefkastenRecord.VISUAL_DETECTED.equals(visualStatus)) {
                            String confStr = confidence != null ? String.format(Locale.getDefault(), "%.1f%%", confidence * 100f) : "";
                            Toast.makeText(MainActivity.this, "Visuell: Briefkasten im Foto erkannt (" + confStr + ")", Toast.LENGTH_SHORT).show();
                            lastVisualStatusText = "Visuell: Briefkasten erkannt (" + confStr + ")";
                        } else if (BriefkastenRecord.VISUAL_NOT_DETECTED.equals(visualStatus)) {
                            Toast.makeText(MainActivity.this, "Visuell: Kein Briefkasten im Foto erkannt", Toast.LENGTH_SHORT).show();
                            lastVisualStatusText = "Visuell: Kein Briefkasten erkannt";
                        } else if (BriefkastenRecord.VISUAL_ERROR.equals(visualStatus)) {
                            Toast.makeText(MainActivity.this, "Visuell: Bildanalyse fehlgeschlagen", Toast.LENGTH_SHORT).show();
                            lastVisualStatusText = "Visuell: Analyse fehlgeschlagen";
                        }
                        updateCombinedStatusDisplay(recordId);
                    });
                } else {
                    dbHelper.updateVisualStatus(record.getId(), BriefkastenRecord.VISUAL_ERROR, null, "Konnte Bilddatei nicht dekodieren.");
                    loadRecordsAndRefreshUI();
                }
            } catch (Throwable t) {
                dbHelper.updateVisualStatus(record.getId(), BriefkastenRecord.VISUAL_ERROR, null, "Fehler beim Laden des Bildes: " + t.getMessage());
                loadRecordsAndRefreshUI();
            }
        }).start();
    }

    // ==========================================
    // OVERPASS API OSM-ABGLEICH (HINTERGRUND)
    // ==========================================

    private void startOsmCheckForRecord(BriefkastenRecord record) {
        overpassApiClient.checkBriefkastenInOsm(record, (recordId, status, osmId, distance, errorMsg) -> {
            // 1. In SQLite persistieren
            dbHelper.updateOsmStatus(recordId, status, osmId, distance, errorMsg);

            // 2. UI & Karte mit neuem Status (Grün/Rot/Grau) aktualisieren
            loadRecordsAndRefreshUI();

            // 3. Statusmeldung anzeigen
            if (BriefkastenRecord.STATUS_MATCH.equals(status)) {
                lastOsmStatusText = "OSM: Bereits erfasst";
                Toast.makeText(MainActivity.this, "OSM: Bereits erfasst", Toast.LENGTH_SHORT).show();
            } else if (BriefkastenRecord.STATUS_MISSING.equals(status)) {
                lastOsmStatusText = "OSM: Nicht erfasst";
                Toast.makeText(MainActivity.this, "OSM: Nicht erfasst", Toast.LENGTH_SHORT).show();
            } else {
                lastOsmStatusText = "OSM: Prüfung fehlgeschlagen";
                Toast.makeText(MainActivity.this, "OSM-Prüfung fehlgeschlagen", Toast.LENGTH_SHORT).show();
            }
            updateCombinedStatusDisplay(recordId);
        });
    }

    // ==========================================
    // RECYCLERVIEW AKTIONEN (ADAPTER CALLBACKS)
    // ==========================================

    @Override
    public void onShowOnMap(BriefkastenRecord record) {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        if (mapLibreMap != null && (record.getLatitude() != 0.0 || record.getLongitude() != 0.0)) {
            LatLng latLng = new LatLng(record.getLatitude(), record.getLongitude());
            mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17.0));

            Marker marker = briefkastenMarkersMap.get(record.getId());
            if (marker != null) {
                mapLibreMap.selectMarker(marker);
            }
        } else {
            Toast.makeText(this, "Keine gültigen GPS-Koordinaten für diesen Briefkasten vorhanden.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDelete(BriefkastenRecord record) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.delete_record_confirm)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    File file = new File(record.getImagePath());
                    if (file.exists()) {
                        file.delete();
                    }
                    dbHelper.deleteRecord(record.getId());
                    loadRecordsAndRefreshUI();
                    Toast.makeText(this, "Datensatz gelöscht", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    public void onRetryOsmCheck(BriefkastenRecord record) {
        // Status zurück auf PENDING setzen und erneut abfragen
        dbHelper.updateOsmStatus(record.getId(), BriefkastenRecord.STATUS_PENDING, null, null, null);
        loadRecordsAndRefreshUI();

        statusDisplayRecordId = record.getId();
        lastOsmStatusText = "OSM: wird geprüft...";
        lastVisualStatusText = record.isVisualDetected()
                ? "Visuell: Briefkasten erkannt"
                : record.isVisualNotDetected() ? "Visuell: Kein Briefkasten erkannt" : "Visuell: " + record.getVisualStatus();
        updateCombinedStatusDisplay(record.getId());

        startOsmCheckForRecord(record);
    }

    @Override
    public void onItemClick(BriefkastenRecord record) {
        // Detail-Dialog mit Großansicht des Bildes und OSM-Informationen
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_record_detail, null);

        ImageView dialogImage = dialogView.findViewById(R.id.dialog_image);
        TextView tvCoords = dialogView.findViewById(R.id.dialog_coords);
        TextView tvOsmInfo = dialogView.findViewById(R.id.dialog_osm_info);
        TextView tvVisualInfo = dialogView.findViewById(R.id.dialog_visual_info);
        TextView tvFilepath = dialogView.findViewById(R.id.dialog_filepath);
        ImageButton btnClose = dialogView.findViewById(R.id.dialog_btn_close);

        tvCoords.setText(getString(R.string.latitude_label) + " " + record.getLatitude() + "\n" +
                getString(R.string.longitude_label) + " " + record.getLongitude() + "\n(" + record.getFormattedCoordinates() + ")");

        // OSM-Status im Detail-Dialog
        if (record.isOsmMatched()) {
            tvOsmInfo.setTextColor(ContextCompat.getColor(this, R.color.osm_match));
            tvOsmInfo.setText("OSM: Bereits erfasst");
        } else if (record.isOsmMissing()) {
            tvOsmInfo.setTextColor(ContextCompat.getColor(this, R.color.osm_missing));
            tvOsmInfo.setText("OSM: Nicht erfasst (Fehlt in OSM)");
        } else if (record.isOsmError()) {
            tvOsmInfo.setTextColor(ContextCompat.getColor(this, R.color.osm_error));
            tvOsmInfo.setText("OSM: Prüfung fehlgeschlagen (" + (record.getOsmErrorMessage() != null ? record.getOsmErrorMessage() : "Fehler") + ")");
        } else {
            tvOsmInfo.setTextColor(ContextCompat.getColor(this, R.color.osm_pending));
            tvOsmInfo.setText("OSM: Prüfung läuft...");
        }

        // Visueller YOLO-Status im Detail-Dialog
        if (tvVisualInfo != null) {
            if (record.isVisualDetected()) {
                tvVisualInfo.setTextColor(ContextCompat.getColor(this, R.color.osm_match));
                String confStr = record.getFormattedVisualConfidence();
                tvVisualInfo.setText("Visuell: Briefkasten erkannt" + (!confStr.isEmpty() ? "\nKonfidenz: " + confStr : ""));
            } else if (record.isVisualNotDetected()) {
                tvVisualInfo.setTextColor(ContextCompat.getColor(this, R.color.osm_missing));
                tvVisualInfo.setText("Visuell: Kein Briefkasten erkannt");
            } else if (record.isVisualError()) {
                tvVisualInfo.setTextColor(ContextCompat.getColor(this, R.color.osm_error));
                String err = record.getVisualErrorMessage() != null ? record.getVisualErrorMessage() : "Fehlgeschlagen";
                tvVisualInfo.setText("Visuell: Bildanalyse fehlgeschlagen (" + err + ")");
            } else {
                tvVisualInfo.setTextColor(ContextCompat.getColor(this, R.color.osm_pending));
                tvVisualInfo.setText("Visuell: Bildanalyse läuft...");
            }
        }

        tvFilepath.setText(record.getImagePath());

        File imgFile = new File(record.getImagePath());
        if (imgFile.exists()) {
            Glide.with(this).load(imgFile).into(dialogImage);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // ==========================================
    // MAPLIBRE MAPVIEW LIFECYCLE MANAGEMENT
    // ==========================================

    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mapView != null) mapView.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapView != null) mapView.onDestroy();
        if (dbHelper != null) dbHelper.close();
        if (overpassApiClient != null) overpassApiClient.shutdown();
        if (briefkastenDetector != null) briefkastenDetector.shutdown();
    }
}
