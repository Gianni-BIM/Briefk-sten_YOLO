Prompt 3 – On-Device YOLO-Bilderkennung (M3: Visuelle Briefkasten-Erkennung im Foto)

Erweitere die bestehende Android-App "Briefkasten Scout" (Java, minSdk 26,
siehe Projekt/BriefkastenScout/) um eine on-device Objekterkennung mittels
eines TensorFlow-Lite-YOLO-Modells, OHNE die bestehende Overpass-API-Prüfung
zu verändern oder zu ersetzen. Die visuelle Erkennung ist eine zusätzliche,
unabhängige zweite Prüfung, ob auf dem Foto tatsächlich ein Briefkasten
(amenity=post_box) zu sehen ist.

1. Lege ein per Ultralytics YOLO trainiertes und nach TensorFlow Lite
   exportiertes Modell `briefkasten_detector.tflite` unter
   `app/src/main/assets/briefkasten_detector.tflite` ab (Platzhalter-Pfad,
   Modell wird separat bereitgestellt) und lade es über
   `org.tensorflow:tensorflow-lite` + `org.tensorflow:tensorflow-lite-support`
   (aktuelle stabile Version) als Gradle-Dependency.
2. Erstelle eine neue Klasse `BriefkastenDetector` (Package
   `org.briefkastenscout.ml`), die:
   - Das `.tflite`-Modell einmalig beim App-Start lädt (Interpreter mit
     GPU-Delegate falls verfügbar, sonst CPU-Fallback).
   - Eine Methode `detect(Bitmap photo)` bereitstellt, die Inferenz auf einem
     Hintergrund-Thread (ExecutorService, analog zu OverpassApiClient)
     ausführt und NICHT den UI-Thread blockiert.
   - Das Eingabebild auf die vom Modell erwartete Größe skaliert/normalisiert
     (Tensor-Layout zur Laufzeit dynamisch erkennen, da Exportpfade
     unterschiedliche Formate wie NCHW oder NHWC liefern können) und die
     YOLO-Rohausgabe (Boxen, Klassen-Scores) per Non-Max-Suppression zu einer
     Liste erkannter Objekte (Klasse, Confidence, Bounding Box)
     nachverarbeitet.
   - Bei Modell-Ladefehler oder Inferenzfehler NIEMALS crasht, sondern einen
     Fehlerzustand zurückgibt (analog zum ERROR-Handling in
     OverpassApiClient – Fehler dürfen nie fälschlich als "nicht erkannt"
     interpretiert werden).
3. Erweitere `BriefkastenRecord` (Model) um:
   - `visualStatus` mit den Konstanten `VISUAL_DETECTED`,
     `VISUAL_NOT_DETECTED`, `VISUAL_ERROR`, `VISUAL_PENDING`.
   - `visualConfidence` (Float, nullable).
4. Erweitere `BriefkastenDbHelper` um eine Migration auf Datenbankversion 3
   (zusätzliche Spalten `visual_status`, `visual_confidence`), analog zur
   bestehenden Migration von Version 1 auf 2.
5. Rufe `BriefkastenDetector.detect(...)` in `MainActivity` unmittelbar nach
   der Fotoaufnahme auf (parallel zur bereits bestehenden
   `OverpassApiClient.checkBriefkastenInOsm(...)`-Prüfung, beide Prüfungen
   laufen unabhängig voneinander und aktualisieren den Datensatz getrennt).
6. UI-Erweiterungen (nur additiv, bestehendes Layout/Verhalten nicht
   entfernen):
   - Neuer visueller Status im Detail-Dialog (Grün = erkannt, Rot = nicht
     erkannt, Grau = Fehler, analog zu den bestehenden OSM-Badges) inklusive
     Konfidenz-Wert bei Erkennung.
   - Kombinierte Statusanzeige nach Fotoaufnahme, die OSM- und visuelles
     Ergebnis unabhängig voneinander anzeigt, sobald sie jeweils fertig sind.
7. Dokumentation: `Projekt/doku.md` um einen Abschnitt "Visuelle
   On-Device-Erkennung (YOLO/TFLite)" ergänzen, der Architektur, Modell-Input/
   Output-Format und Zusammenspiel mit der Overpass-Prüfung beschreibt.

Ziel: Java-only, minSdk 26, keine Kotlin-Dateien, keine bestehende Logik
(Overpass-Check, DB-Schema v1/v2, UI) entfernen oder inkompatibel verändern –
nur additive Erweiterung um die visuelle Erkennung.
