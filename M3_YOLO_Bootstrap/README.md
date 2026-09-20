# M3 – YOLO-Trainingsdaten-Bootstrap

Sammelt Kandidaten-Fotos für ein YOLO-Objekterkennungsmodell (Briefkästen,
`amenity=post_box`), indem bereits in OpenStreetMap erfasste Briefkästen mit
nahegelegenen Mapillary-Streetview-Fotos verknüpft werden.

**Wichtig:** Dieses Skript liefert nur **Rohdaten + Metadaten**, keine fertigen
YOLO-Bounding-Box-Labels. Ob ein Briefkasten auf dem jeweiligen Foto
tatsächlich zu sehen ist (und wo im Bild), muss danach manuell annotiert
werden (z. B. mit [Roboflow](https://roboflow.com) oder
[LabelImg](https://github.com/HumanSignal/labelImg)) – automatisches Labeling
ist hier nicht zuverlässig möglich, da die Overpass-/Mapillary-Nähe nur
"Briefkasten existiert in der Gegend" bestätigt, nicht "Briefkasten ist im
Bildausschnitt sichtbar".

## Ablauf

1. **Overpass API**: Fragt alle `amenity=post_box` Objekte innerhalb der
   administrativen Grenze von Berlin ab.
2. **Mapillary Graph API**: Sucht für jeden Briefkasten Fotos im Umkreis von
   30 m (max. 3 pro Objekt) und lädt sie herunter.
3. **Manifest**: Schreibt `output/manifest.csv` mit Zuordnung
   Briefkasten ↔ Foto ↔ GPS ↔ Blickrichtung (`compass_angle`) ↔ Aufnahmezeit.
4. **Vorfilter** (optional, `filter_by_bearing.py`): Behält nur Fotos, bei
   denen die Kamera-Blickrichtung ungefähr zum Briefkasten zeigt, um den
   manuellen Aussortier-Aufwand zu reduzieren.

## Nutzung

```bash
python3 bootstrap.py
python3 filter_by_bearing.py        # optional, Standard-Toleranz ±60°
```

Voraussetzung: `mapillary_token.txt` enthält einen gültigen Mapillary
Access Token (siehe [mapillary.com/developer](https://www.mapillary.com/developer)).
**Nicht committen / teilen** – der Token ist an den Account gebunden.

## Output-Struktur

```
output/
├── images/
│   └── postbox_<osm_id>_img_<mapillary_id>.jpg
├── images_filtered/                    # nur nach filter_by_bearing.py
├── manifest.csv                        # Briefkasten-Foto-Zuordnung mit GPS & Blickrichtung
├── manifest_filtered.csv               # nur nach filter_by_bearing.py
└── postboxes_without_photos.csv        # Briefkästen ohne Mapillary-Abdeckung im 30m-Radius
```

## Nächste Schritte (nach diesem Bootstrap)

1. Bilder aus `output/images/` (oder `output/images_filtered/`) in ein
   Annotation-Tool laden und Bounding Boxes um sichtbare Briefkästen ziehen
   (Klasse z. B. `post_box`).
2. Train/Val-Split (z. B. 80/20) und Export im YOLO-Format
   (`images/train`, `images/val`, `labels/train`, `labels/val`).
3. YOLO-Modell trainieren (z. B. Ultralytics YOLOv8/YOLO11, `yolo train ...`).
4. Modell nach TensorFlow Lite exportieren (`yolo export format=tflite`).
5. Exportiertes `.tflite`-Modell gemäß `Prompt 3` (`Projekt/Prompt 3/promt3.md`)
   in die CourtScout-App integrieren.

## Parameter anpassen

In `bootstrap.py`:
- `SEARCH_RADIUS_M` – Suchradius um jeden Briefkasten (Standard: 30 m)
- `MAX_IMAGES_PER_OBJECT` – max. Fotos pro Briefkasten (Standard: 3)
- Overpass-Query (`OVERPASS_QUERY`) – aktuell auf `area["name"="Berlin"]`
  begrenzt; für andere Städte/Regionen anpassen.

In `filter_by_bearing.py`:
- Toleranzwinkel als Kommandozeilenargument, z. B.
  `python3 filter_by_bearing.py 45` für ±45°.
