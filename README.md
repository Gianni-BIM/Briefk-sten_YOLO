# Briefkasten Scout

Automatisierte Erkennung fehlender Briefkästen (`amenity=post_box`) in OpenStreetMap – eine Android-App mit GPS-gestütztem OSM-Abgleich und On-Device-KI-Bilderkennung (YOLO/TensorFlow Lite).

**Modul:** Automatisierte Geodatenprozessierung
**Autor:** Ioannis Svolos
**Matrikelnummer:** 906758

---

## Motivation

In Session 1 des Moduls wurde gefragt: *"Wie viele Sitzbänke gibt es in der OSM-Welt?"* – Schätzungen lagen zwischen 40 Mio. und 1,5 Mrd., tatsächlich sind laut taginfo nur ca. 3,2 Mio. Sitzbänke kartiert. Diese riesige Lücke zwischen realem Bestand an Stadtmöblierung und dem tatsächlichen OSM-Datenbestand ist die Motivation für dieses Projekt – hier am Beispiel von Briefkästen.

Briefkasten Scout verbindet drei Techniken aus dem Modul zu einer durchgängigen Pipeline:

1. **Mobile Datenerfassung**: Foto + präzise GPS-Position direkt vor Ort.
2. **Automatisierter Geodatenabgleich**: Live-Abfrage der Overpass-API, ob an dieser Position bereits ein Briefkasten in OSM erfasst ist.
3. **On-Device-Objekterkennung**: Ein selbst trainiertes YOLO-Modell prüft zusätzlich direkt auf dem Gerät, ob im Foto tatsächlich ein Briefkasten zu sehen ist – unabhängig vom OSM-Status.

## Projektstruktur

```
├── BriefkastenScout/        # Android-App (Java, minSdk 26)
│   └── app/src/main/        # Quellcode, Layouts, trainiertes TFLite-Modell
├── M3_YOLO_Bootstrap/        # Trainingsdaten-Pipeline für das YOLO-Modell
│   ├── bootstrap.py          # Sammelt Kandidaten-Fotos (OSM + Mapillary)
│   ├── filter_by_bearing.py  # Filtert Fotos nach Kamera-Blickrichtung
│   ├── city_scan.py          # Stadtweiter Modell-Anwendungstest
│   └── output/                # Manifeste (CSV) der gesammelten Trainingsdaten
├── Prompt 1/, Prompt 2/, Prompt 3/  # Spezifikationen & Implementierungspläne je Ausbaustufe
├── screenshots/               # Screenshots aller App-Zustände (MATCH/MISSING/ERROR/Visuell)
├── doku.md                    # Vollständige technische Dokumentation
└── kurzbeschreibung.md        # Kurzbeschreibung / Motivation
```

## Funktionsweise

| Prüfung | Basis | Ergebnis |
|---|---|---|
| **OSM-Abgleich** | GPS-Position (Overpass API, 30 m Radius) | MATCH (grün) / MISSING (rot) / ERROR (grau) |
| **Visuelle Erkennung** | Bildinhalt des Fotos (On-Device YOLO/TFLite) | Briefkasten erkannt / nicht erkannt / Fehler |

Beide Prüfungen laufen unabhängig und parallel; die aussagekräftigste Kombination ist **MISSING + visuell erkannt** = eine mit Foto belegte, echte Kartierungslücke.

Vollständige Details zu Architektur, Overpass-Query, Modell-Training und Grenzen der visuellen Erkennung: siehe [doku.md](doku.md).

## Ausführbare APK

Eine fertig kompilierte, installierbare Debug-APK steht unter [GitHub Releases](https://github.com/Gianni-BIM/Briefk-sten_YOLO/releases) zum Download bereit (nicht im Git-Repository selbst, da > 50 MB).

## Bauen & Ausführen (aus dem Quellcode)

```bash
cd BriefkastenScout
./gradlew assembleDebug
```

Projekt kann auch direkt in Android Studio geöffnet werden (`BriefkastenScout/` als Projektordner auswählen).

## KI-gestützte, automatisierte Entwicklungs-Prozesskette

Dieses Projekt setzt bewusst auf eine durchgängig KI-gestützte Automatisierung statt manueller Handarbeit:

- **Google Antigravity** (agentenbasierte KI-IDE) generierte die Android-App iterativ aus präzisen Prompt-Spezifikationen (siehe `Prompt 1/`–`Prompt 3/`) – inklusive automatisierter Overpass-API-Query-Erstellung.
- Die YOLO-Trainingsdaten wurden vollautomatisch über die **OpenStreetMap**- und **Mapillary-APIs** gesammelt (`M3_YOLO_Bootstrap/bootstrap.py`), Annotation über **Roboflow**, Training via **Google Colab** + **Ultralytics YOLO**, Export nach **TensorFlow Lite**.
- Details und Ergebnisse dieses Experiments: siehe [kurzbeschreibung.md](kurzbeschreibung.md).

## YOLO-Trainingsdaten-Pipeline

Der Ordner `M3_YOLO_Bootstrap/` enthält die Skripte, mit denen die Trainingsdaten für das On-Device-Modell automatisiert gesammelt wurden: Abfrage bekannter Briefkästen aus OpenStreetMap, Suche nach passenden Mapillary-Streetview-Fotos, Filterung nach Kamera-Blickrichtung, und ein Skript zur stadtweiten Anwendung des trainierten Modells. Für die Ausführung wird ein eigener Mapillary-Zugriffstoken benötigt (siehe `M3_YOLO_Bootstrap/mapillary_token.txt.example`).

## Screenshots

Alle vier Statuspfade (MATCH, MISSING, ERROR + Retry, visuelle Erkennung) sind im Ordner [`screenshots/`](screenshots/) dokumentiert.
