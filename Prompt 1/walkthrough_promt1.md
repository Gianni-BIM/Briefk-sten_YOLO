# Walkthrough – Android-App "CourtScout"

Die Android-App **CourtScout** wurde vollständig in reinem **Java** mit **XML-Layouts** und **minSdk 26** implementiert.

## Erstellte Komponenten

### 1. Build- und Projektstruktur
- [build.gradle](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/build.gradle) & [settings.gradle](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/settings.gradle): Standard-Gradle-Setup mit Repositories (Google, MavenCentral).
- [app/build.gradle](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/build.gradle): Konfiguriert mit `minSdk 26`, `compileSdk 34`, `Java 8/1.8` Kompatibilität und Abhängigkeiten:
  - `org.maplibre.gl:android-sdk:11.5.1` (MapLibre Native)
  - `com.google.android.gms:play-services-location:21.1.0` (Fused Location)
  - `com.github.bumptech.glide:glide:4.16.0` (Bildlade-Bibliothek)
  - AndroidX Material 3, RecyclerView, ConstraintLayout

### 2. Manifest & Berechtigungen
- [AndroidManifest.xml](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/AndroidManifest.xml):
  - Deklarierte Permissions: `INTERNET`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `CAMERA`.
  - `androidx.core.content.FileProvider` mit Authority `org.courtscout.fileprovider`.
  - [file_paths.xml](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/res/xml/file_paths.xml) für den sicheren Zugriff auf das `Pictures`-Verzeichnis.

### 3. Java-Klassen
- [CourtRecord.java](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/java/org/courtscout/model/CourtRecord.java): Datenmodell für ID, Dateipfad, Latitude, Longitude, Zeitstempel mit Formatierungshelfern.
- [CourtDbHelper.java](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/java/org/courtscout/db/CourtDbHelper.java): `SQLiteOpenHelper`-Implementierung mit vollständigen CRUD-Methoden (`insertRecord`, `getAllRecords`, `getRecordById`, `deleteRecord`, `getRecordCount`).
- [CourtRecordAdapter.java](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/java/org/courtscout/adapter/CourtRecordAdapter.java): `RecyclerView.Adapter` mit Bildanzeige (Glide), Koordinaten, Zeitstempel, "Auf Karte zeigen"- und "Löschen"-Aktionen.
- [MainActivity.java](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/java/org/courtscout/MainActivity.java):
  - Initialisierung von MapLibre GL mit Vektor-Tiles (OpenStreetMap Demo Style).
  - Volles MapView-Lifecycle-Management (`onStart`, `onResume`, `onPause`, `onStop`, `onDestroy`, etc.).
  - Ortungs-FAB mit `FusedLocationProviderClient.getCurrentLocation` (High Accuracy) und Kartenanimation.
  - Kamera-FAB mit `ActivityResultContracts.TakePicture()`, FileProvider und Verknüpfung mit Aufnahme-GPS-Position.
  - Speicherung in SQLite und Live-Aktualisierung der RecyclerView sowie der Karten-Marker.
  - Interaktives BottomSheet für die Fotoliste.

### 4. Layouts & UI-Design
- [activity_main.xml](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/res/layout/activity_main.xml): MapView als Vollbild, Floating Action Buttons für Ortung, Kamera und Listenansicht, BottomSheet für die RecyclerView.
- [item_record.xml](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/res/layout/item_record.xml): Listenelement mit Thumbnail, formatierten Koordinaten, Zeitstempel und Aktions-Buttons.
- [dialog_record_detail.xml](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/res/layout/dialog_record_detail.xml): Detaildialog für Großansicht.
- Vektor-Icons: [ic_my_location.xml](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/res/drawable/ic_my_location.xml), [ic_camera.xml](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/res/drawable/ic_camera.xml), [ic_court_marker.xml](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/res/drawable/ic_court_marker.xml), etc.
- Vollständige Projektdokumentation in [doku.md](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/doku.md).

## Überprüfung & Ausführung
Das Projekt ist als direkt in Android Studio importierbares Projekt unter `Projekt/CourtScout` angelegt.
In Android Studio:
1. Menü: **File -> Open...** -> Ordner `CourtScout` auswählen.
2. Das Projekt synchronisiert Gradle automatisch und kann direkt auf einem Gerät oder Emulator gestartet werden.
