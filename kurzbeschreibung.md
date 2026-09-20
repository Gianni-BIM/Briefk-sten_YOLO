# Kurzbeschreibung – Briefkasten Scout

**Name:** Ioannis Svolos
**Matrikelnummer:** 906758
**Modul:** Automatisierte Geodatenprozessierung

**Repository (Quellcode & ausführbarer Code):** https://github.com/Gianni-BIM/Briefk-sten_YOLO

---

## Motivation

In Session 1 des Moduls wurde gefragt: *"Wie viele Sitzbänke gibt es in der OSM-Welt?"* – Schätzungen lagen zwischen 40 Mio. und 1,5 Mrd., tatsächlich sind laut taginfo nur ca. 3,2 Mio. Sitzbänke kartiert. Diese riesige Lücke zwischen realem Bestand an Stadtmöblierung und dem tatsächlichen OSM-Datenbestand ist die Motivation für dieses Projekt – hier experimentell am Beispiel von **Briefkästen** (`amenity=post_box`) durchgespielt.

## Projektidee

**Briefkasten Scout** ist eine Android-App, die den kompletten Prozess von der Vor-Ort-Erfassung bis zur automatisierten Validierung gegen OpenStreetMap abbildet:

1. Foto + präzise GPS-Position werden vor Ort aufgenommen.
2. Eine automatisierte **Overpass-API-Abfrage** prüft im Hintergrund, ob an dieser Position bereits ein Briefkasten in OSM erfasst ist (MATCH / MISSING / ERROR).
3. Ein selbst trainiertes **YOLO-Modell** läuft direkt auf dem Gerät (TensorFlow Lite) und prüft unabhängig davon, ob auf dem Foto tatsächlich ein Briefkasten zu sehen ist.
4. Die aussagekräftigste Kombination – **MISSING + visuell bestätigt** – markiert eine mit Foto belegte, echte Kartierungslücke in OSM.

## Die KI-gestützte, automatisierte Prozesskette

Der Schwerpunkt dieses Experiments lag bewusst auf der **Automatisierung des gesamten Entwicklungs- und Datenprozesses mittels KI-Werkzeugen** statt manueller Handarbeit:

- **App-Entwicklung per KI-Agent (Google Antigravity):** Die Android-App wurde nicht Zeile für Zeile von Hand programmiert, sondern iterativ über präzise Prompt-Spezifikationen (siehe `Prompt 1/`, `Prompt 2/`, `Prompt 3/` im Repository) durch den agentenbasierten KI-Programmierassistenten **Google Antigravity** erzeugt: von der MapLibre-Kartenintegration über GPS/Kamera-Anbindung bis zur automatisierten Overpass-API-Query-Generierung und der finalen TensorFlow-Lite-Modellintegration.
- **Automatisierte Trainingsdaten-Erhebung:** Ein Python-Skript (`M3_YOLO_Bootstrap/bootstrap.py`) fragt vollautomatisch bekannte Briefkästen aus OpenStreetMap ab und verknüpft sie mit passenden Streetview-Fotos der **Mapillary-API** – ganz ohne manuelles Fotografieren im Feld.
- **KI-gestütztes Modelltraining:** Annotation der Kandidaten-Fotos über **Roboflow**, Training eines **Ultralytics YOLO**-Objekterkennungsmodells in **Google Colab**, automatisierter Export nach TensorFlow Lite (LiteRT) für die On-Device-Ausführung.
- **Stadtweite automatisierte Anwendung:** `M3_YOLO_Bootstrap/city_scan.py` wendet das trainierte Modell automatisiert auf flächendeckend gesammelte Mapillary-Fotos eines Berliner Stadtgebiets an und gleicht Treffer live gegen OSM ab – als Machbarkeitsnachweis für eine vollautomatisierte, KI-gestützte Kartierungslücken-Erkennung im großen Maßstab, ganz ohne persönlichen Vor-Ort-Einsatz.

Damit deckt das Projekt die komplette in der Aufgabenstellung beschriebene Kette ab: von der KI-gestützten Erzeugung einer GeoIT-Mobile-Anwendung (Antigravity + Overpass API) bis zur KI-gestützten automatisierten Objekterkennung/Klassifizierung (YOLO on-device).

## Ergebnis & Grenzen (siehe `doku.md`, Kapitel 8)

Die Pipeline funktioniert nachweislich End-to-End (siehe Screenshots unten): korrektes Erkennen bereits kartierter Briefkästen, korrektes Melden fehlender Briefkästen, und korrekte visuelle Bestätigung per selbst trainiertem Modell (98,7 % Konfidenz bei einem echten Testfoto). Bei nur ~130 Trainingsbildern ist die Generalisierung des Modells auf beliebige Straßenfotos noch begrenzt (dokumentiert in `doku.md`) – ein realistisches, ehrlich reflektiertes Ergebnis für den Umfang dieses Experiments.

## Screenshots

**Ausgangsmaterial – echtes Foto eines Briefkastens (Datenbasis für Training & Test):**
![Echtes Briefkasten-Foto](screenshots/Bildschirmfoto%202026-09-20%20um%2003.37.40.png)

**Erfolgreicher Testfall – OSM-Treffer UND visuelle KI-Erkennung stimmen überein (98,7 % Konfidenz):**
![MATCH + visuell erkannt](screenshots/Bildschirmfoto%202026-09-20%20um%2004.21.20.png)

**Vergleich zweier Testfälle nebeneinander – MATCH (grün) vs. MISSING + nicht erkannt (rot), inkl. GPS-Steuerung im Emulator:**
![Vergleich zweier Testfälle](screenshots/Bildschirmfoto%202026-09-20%20um%2004.22.40.png)

Alle weiteren Screenshots (inkl. Kartenansicht, Fehlerfall mit Retry-Funktion) liegen im Ordner [`screenshots/`](screenshots/).

## Ausführbarer Code

Vollständiger Quellcode: Ordner `BriefkastenScout/` im Repository.
Fertig kompilierte, installierbare Debug-APK: siehe [GitHub Release](https://github.com/Gianni-BIM/Briefk-sten_YOLO/releases) im Repository (Upload-Limit 50 MB dieser Abgabe wird durch die Repository-URL umgangen).
