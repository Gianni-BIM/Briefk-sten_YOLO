# Implementation Plan: Overpass API OSM Check mit robuster Fehlerbehandlung & Fallback (CourtScout)

Erweiterung der CourtScout-App um eine automatische Hintergrundprüfung gegen die Overpass API nach der Fotoaufnahme mit sauberer Trennung zwischen Treffer (`MATCH`), Nicht erfasst (`MISSING`), Fehler (`ERROR`) und In Bearbeitung (`PENDING`), inklusive Fallback-Mirror, Timeouts, Retry-Funktion und farbcodierten Karten-Markern.

## User Review Required

> [!IMPORTANT]
> - **Exakte Status-Trennung**:
>   - `MATCH` (Grün): Overpass antwortet mit HTTP 200 und mindestens 1 Treffer im 30m-Radius.
>   - `MISSING` (Rot): Overpass antwortet mit HTTP 200 und 0 Treffern (Court fehlt in OSM).
>   - `ERROR` (Grau/Orange): Timeout, HTTP != 200, kein Netz, Parse-Fehler – **niemals** fälschlicherweise als `MISSING` gewertet.
>   - `PENDING`: Prüfung läuft gerade im Hintergrund.
> - **Fallback-Mirror & Timeouts**:
>   - Connect Timeout: 10s, Read Timeout: 15s.
>   - Primärer Endpunkt: `https://overpass-api.de/api/interpreter`
>   - Sekundärer Fallback: `https://overpass.kumi.systems/api/interpreter` (wird automatisch versucht, bevor auf `ERROR` geschaltet wird).
> - **Retry-Mechanismus**: In der RecyclerView wird bei `ERROR` ein Retry-Icon/Button angezeigt, der die Prüfung für diesen Datensatz gezielt wiederholt.

## Geplante Änderungen

### 1. Datenmodell & Datenbank
- **[MODIFY] CourtRecord.java**
  - Konstanten definieren: `STATUS_PENDING = "PENDING"`, `STATUS_MATCH = "MATCH"`, `STATUS_MISSING = "MISSING"`, `STATUS_ERROR = "ERROR"`
  - Felder: `String osmStatus`, `String osmId`, `Double osmDistance`, `String osmErrorMessage`
  - Hilfsmethoden: `isOsmMatched()`, `isOsmMissing()`, `isOsmError()`, `isOsmPending()`
- **[MODIFY] CourtDbHelper.java**
  - Schema-Upgrade auf Version 2: Hinzufügen von `osm_status`, `osm_id`, `osm_distance`, `osm_error`
  - Methoden `updateOsmStatus(long id, String status, String osmId, Double distance, String errorMsg)`

### 2. Overpass API Client mit Fallback-Mirror
- **[NEW] OverpassApiClient.java**
  - Request-Logik mit `HttpURLConnection`:
    - Connect Timeout: 10.000 ms, Read Timeout: 15.000 ms
    - Primär: `https://overpass-api.de/api/interpreter`
    - Fallback: `https://overpass.kumi.systems/api/interpreter`
  - Query:
    ```overpass
    [out:json][timeout:25];
    (
      nwr["leisure"="pitch"]["sport"="basketball"](around:30,{lat},{lon});
    );
    out center;
    ```
  - Parse-Logik:
    - Bei HTTP 200 + validem JSON: Elemente auswerten. Wenn `elements.length() > 0`: `MATCH`, sonst `MISSING`.
    - Bei HTTP != 200, Timeout oder Parse-Fehler auf beiden Servern: Callback mit `ERROR` und Fehlermeldung.

### 3. UI-Ressourcen & Visualisierung
- **[NEW] ic_marker_match.xml**: Grüner Marker (#10B981)
- **[NEW] ic_marker_missing.xml**: Roter Marker (#EF4444)
- **[NEW] ic_marker_error.xml**: Grauer Marker (#64748B)
- **[NEW] ic_retry.xml**: Wiederholen-Icon für fehlgeschlagene Abfragen
- **[MODIFY] item_record.xml**: Einbau von Status-Chip / Badge und Retry-Button
- **[MODIFY] CourtRecordAdapter.java**:
  - Darstellung der Badges (Grün mit Distanz/ID, Rot "Fehlt in OSM", Grau/Orange "Prüfung fehlgeschlagen" + Retry-Klick)
  - Neuer Callback `onRetryOsmCheck(CourtRecord record)`

### 4. Integration in MainActivity
- **[MODIFY] MainActivity.java**
  - Nach Bildaufnahme: Datensatz sofort mit Status `PENDING` anlegen.
  - Hintergrundprüfung anstoßen: `overpassApiClient.checkCourtInOsm(record, callback)`
  - Nach Antwort: SQLite aktualisieren, Adapter benachrichtigen, Kartenmarker farblich aktualisieren (Grün / Rot / Grau).
  - Bei Klick auf Retry in der Liste: Erneute Abfrage starten.

## Verification Plan

### Manuelle & Code-Verifikation
- Code-Review des Fallback-Mechanismus (Primär -> Sekundär -> Fehler)
- Überprüfung der Timeouts (10s Connect, 15s Read)
- Validierung, dass bei Netzwerkunterbrechung der Status garantiert `ERROR` und nicht `MISSING` wird
- Aktualisierung der Projektdokumentation in `doku.md`
