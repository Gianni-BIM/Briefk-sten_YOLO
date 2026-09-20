# Walkthrough – Android-App "CourtScout"

Die Android-App **CourtScout** wurde vollständig in reinem **Java** mit **XML-Layouts** und **minSdk 26** implementiert.

## Erstellte Komponenten

### 1. Build- und Projektstruktur
- build.gradle & settings.gradle: Standard-Gradle-Setup mit Repositories (Google, MavenCentral).
- app/build.gradle: Konfiguriert mit `minSdk 26`, `compileSdk 34`, `Java 8/1.8` Kompatibilität und Abhängigkeiten:
  - `org.maplibre.gl:android-sdk:11.5.1` (MapLibre Native)
  - `com.google.android.gms:play-services-location:21.1.0` (Fused Location)
  - `com.github.bumptech.glide:glide:4.16.0` (Bildlade-Bibliothek)
  - AndroidX Material 3, RecyclerView, ConstraintLayout

### 2. Manifest & Berechtigungen
- AndroidManifest.xml:
  - Deklarierte Permissions: `INTERNET`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `CAMERA`.
  - `androidx.core.content.FileProvider` mit Authority `org.courtscout.fileprovider`.
  - file_paths.xml für den sicheren Zugriff auf das `Pictures`-Verzeichnis.

### 3. Java-Klassen
- CourtRecord.java: Datenmodell für ID, Dateipfad, Latitude, Longitude, Zeitstempel mit Formatierungshelfern.
- CourtDbHelper.java: `SQLiteOpenHelper`-Implementierung mit vollständigen CRUD-Methoden (`insertRecord`, `getAllRecords`, `getRecordById`, `deleteRecord`, `getRecordCount`).
- CourtRecordAdapter.java: `RecyclerView.Adapter` mit Bildanzeige (Glide), Koordinaten, Zeitstempel, "Auf Karte zeigen"- und "Löschen"-Aktionen.
- MainActivity.java:
  - Initialisierung von MapLibre GL mit Vektor-Tiles (OpenStreetMap Demo Style).
  - Volles MapView-Lifecycle-Management (`onStart`, `onResume`, `onPause`, `onStop`, `onDestroy`, etc.).
  - Ortungs-FAB mit `FusedLocationProviderClient.getCurrentLocation` (High Accuracy) und Kartenanimation.
  - Kamera-FAB mit `ActivityResultContracts.TakePicture()`, FileProvider und Verknüpfung mit Aufnahme-GPS-Position.
  - Speicherung in SQLite und Live-Aktualisierung der RecyclerView sowie der Karten-Marker.
  - Interaktives BottomSheet für die Fotoliste.

### 4. Layouts & UI-Design
- activity_main.xml: MapView als Vollbild, Floating Action Buttons für Ortung, Kamera und Listenansicht, BottomSheet für die RecyclerView.
- item_record.xml: Listenelement mit Thumbnail, formatierten Koordinaten, Zeitstempel und Aktions-Buttons.
- dialog_record_detail.xml: Detaildialog für Großansicht.
- Vektor-Icons: ic_my_location.xml, ic_camera.xml, ic_court_marker.xml, etc.
- Vollständige Projektdokumentation in doku.md.

## Überprüfung & Ausführung
Das Projekt ist als direkt in Android Studio importierbares Projekt unter `Projekt/CourtScout` angelegt.
In Android Studio:
1. Menü: **File -> Open...** -> Ordner `CourtScout` auswählen.
2. Das Projekt synchronisiert Gradle automatisch und kann direkt auf einem Gerät oder Emulator gestartet werden.
