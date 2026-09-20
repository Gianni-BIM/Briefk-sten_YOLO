# Walkthrough – CourtScout: Overpass API OSM-Integration

Die App **CourtScout** wurde um die automatische Hintergrundprüfung gegen die Overpass API erweitert.

## Neue Komponenten & Änderungen

### 1. Datenmodell & SQLite-Migration
- [CourtRecord.java](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/java/org/courtscout/model/CourtRecord.java):
  - Konstanten: `STATUS_MATCH`, `STATUS_MISSING`, `STATUS_ERROR`, `STATUS_PENDING`.
  - Felder: `osmStatus`, `osmId`, `osmDistance`, `osmErrorMessage`.
  - Hilfsmethoden: `isOsmMatched()`, `isOsmMissing()`, `isOsmError()`, `isOsmPending()`, `getFormattedOsmDistance()`.
- [CourtDbHelper.java](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/java/org/courtscout/db/CourtDbHelper.java):
  - Upgrade auf Datenbankversion 2 mit automatischer `ALTER TABLE`-Migration.
  - Methode `updateOsmStatus(id, status, osmId, distance, errorMsg)`.

### 2. Overpass API Client mit Fallback-Mirror
- [OverpassApiClient.java](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/java/org/courtscout/net/OverpassApiClient.java):
  - Führt die Overpass-QL-Query im Hintergrund über einen `ExecutorService` aus.
  - Query:
    ```overpass
    [out:json][timeout:25];
    (
      nwr["leisure"="pitch"]["sport"="basketball"](around:30,{lat},{lon});
    );
    out center;
    ```
  - **Timeouts**: Connect 10s, Read 15s.
  - **Fallback-Mirror**: Bei Fehlschlag von `https://overpass-api.de/api/interpreter` wird automatisch ein zweiter Versuch gegen `https://overpass.kumi.systems/api/interpreter` durchgeführt.
  - **Strikte Trennung**:
    - Treffer vorhanden -> `MATCH` (mit ID & Distanz)
    - Keine Treffer bei HTTP 200 -> `MISSING`
    - Timeout, HTTP != 200, kein Netz -> `ERROR` (niemals fälschlich als `MISSING`)

### 3. UI-Ressourcen & Adapter
- [item_record.xml](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/res/layout/item_record.xml): Status-Badge mit farbigem Indikator-Dot und Retry-Button.
- [CourtRecordAdapter.java](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/java/org/courtscout/adapter/CourtRecordAdapter.java):
  - Grüne Badge für `MATCH`
  - Rote Badge für `MISSING`
  - Graue Badge für `ERROR` mit sichtbarem Retry-Icon
- [MainActivity.java](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/java/org/courtscout/MainActivity.java):
  - Farbcodierte MapLibre Marker:
    - 🟢 [ic_marker_match.xml](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/res/drawable/ic_marker_match.xml)
    - 🔴 [ic_marker_missing.xml](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/res/drawable/ic_marker_missing.xml)
    - ⚪ [ic_marker_error.xml](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/CourtScout/app/src/main/res/drawable/ic_marker_error.xml)
  - Automatischer Start der Prüfung nach Bildaufnahme & Georeferenzierung.
  - Retry-Handling bei Klick auf das Wiederholen-Symbol.
- Dokumentation in [doku.md](file:///Users/ioannissvolos/Desktop/Master/Automatisierte%20Geodatenprozessierung/Projekt/doku.md) aktualisiert.
