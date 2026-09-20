Prompt 2 – Overpass API OSM-Abgleich (M2: Automatischer Geodaten-Abgleich)

Erweitere die bestehende Android-App "Briefkasten Scout" um eine automatische
Hintergrundprüfung gegen die Overpass API, ohne die bestehende Foto-/GPS-/
SQLite-Logik zu verändern.

1. Erstelle eine Klasse `OverpassApiClient` (Package `net`), die nach jeder
   Fotoaufnahme im Hintergrund (ExecutorService, nicht UI-Thread) folgende
   Overpass-QL-Query gegen die GPS-Position des Datensatzes ausführt:

   [out:json][timeout:25];
   (
     nwr["amenity"="post_box"](around:30,{lat},{lon});
   );
   out center;

2. Werte die Antwort strikt in drei Zustände aus:
   - `MATCH`: HTTP 200 und mindestens 1 Element im 30m-Radius. OSM-ID
     (node/way/relation) und die mit `Location.distanceBetween` berechnete
     Distanz werden im Datensatz gespeichert.
   - `MISSING`: HTTP 200, aber `elements` ist leer. Der Briefkasten fehlt in
     OSM.
   - `ERROR`: Timeout, Verbindungsfehler, HTTP-Status ungleich 200 oder
     ungültiges JSON. Ein Fehler darf **niemals** fälschlich als `MISSING`
     interpretiert werden.
3. Timeouts: Connect 10s, Read 15s. Bei Fehlschlag automatischer Fallback auf
   einen zweiten Overpass-Mirror-Server, bevor endgültig auf `ERROR`
   geschaltet wird.
4. Erweitere das Datenmodell und die SQLite-Datenbank (Schema-Migration von
   Version 1 auf 2) um die Felder `osmStatus`, `osmId`, `osmDistance`,
   `osmErrorMessage`.
5. Zeige den Status farblich in der Liste und auf der Karte:
   - Grün = `MATCH`, Rot = `MISSING`, Grau = `ERROR`, Orange = Prüfung läuft.
   - Bei `ERROR` einen Retry-Button anzeigen, der die Prüfung für diesen
     einzelnen Datensatz erneut anstößt.

Ziel: Java-only, minSdk 26, keine bestehende Logik aus M1 entfernen – nur
additive Erweiterung um den automatisierten OSM-Abgleich.
