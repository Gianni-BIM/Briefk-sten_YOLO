#!/usr/bin/env python3
"""
Filtert die Bootstrap-Bilder danach, ob die Kamera beim Aufnehmen ungefähr
in Richtung des Briefkastens geschaut hat (compass_angle vs. Peilung
Kamera-Position -> Briefkasten-Position). Reduziert den Anteil an Fotos, die
zwar in der Nähe eines Briefkastens aufgenommen wurden, aber in eine andere
Richtung zeigen (z. B. nur die Straße).

Kopiert (nicht verschiebt) wahrscheinlich brauchbare Bilder nach
output/images_filtered/ und schreibt output/manifest_filtered.csv.
Die ursprünglichen Daten in output/images/ und output/manifest.csv bleiben
unverändert.

Nutzung:
    python3 filter_by_bearing.py [max_angle_diff_degrees]
"""

import csv
import math
import os
import shutil
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "output")
MANIFEST_PATH = os.path.join(OUTPUT_DIR, "manifest.csv")
FILTERED_IMAGES_DIR = os.path.join(OUTPUT_DIR, "images_filtered")
FILTERED_MANIFEST_PATH = os.path.join(OUTPUT_DIR, "manifest_filtered.csv")

DEFAULT_MAX_ANGLE_DIFF = 60.0  # Grad Toleranz zwischen Blickrichtung und Peilung zum Briefkasten


def bearing(lat1, lon1, lat2, lon2):
    """Initiale Peilung (Grad, 0-360, 0=Norden) von Punkt 1 zu Punkt 2."""
    lat1_r, lat2_r = math.radians(lat1), math.radians(lat2)
    dlon_r = math.radians(lon2 - lon1)
    x = math.sin(dlon_r) * math.cos(lat2_r)
    y = math.cos(lat1_r) * math.sin(lat2_r) - math.sin(lat1_r) * math.cos(lat2_r) * math.cos(dlon_r)
    return (math.degrees(math.atan2(x, y)) + 360) % 360


def angle_diff(a, b):
    """Kleinster Winkelunterschied zwischen zwei Richtungen (0-180 Grad)."""
    d = abs(a - b) % 360
    return d if d <= 180 else 360 - d


def main():
    max_diff = float(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_MAX_ANGLE_DIFF

    if not os.path.exists(MANIFEST_PATH):
        print(f"Manifest nicht gefunden: {MANIFEST_PATH}. Erst bootstrap.py ausführen.")
        return

    os.makedirs(FILTERED_IMAGES_DIR, exist_ok=True)

    kept, dropped, no_compass = 0, 0, 0
    filtered_rows = []

    with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        rows = list(reader)

    for row in rows:
        compass = row.get("compass_angle")
        if compass in (None, "", "None"):
            no_compass += 1
            continue  # Ohne Blickrichtung keine verlässliche Aussage möglich -> aussortieren

        compass = float(compass)
        postbox_bearing = bearing(
            float(row["image_lat"]), float(row["image_lon"]),
            float(row["postbox_lat"]), float(row["postbox_lon"]),
        )
        diff = angle_diff(compass, postbox_bearing)
        row["bearing_diff_deg"] = round(diff, 1)

        if diff <= max_diff:
            src = os.path.join(OUTPUT_DIR, row["image_path"])
            dest = os.path.join(FILTERED_IMAGES_DIR, os.path.basename(row["image_path"]))
            if os.path.exists(src):
                shutil.copy2(src, dest)
                row["image_path"] = os.path.relpath(dest, OUTPUT_DIR)
                filtered_rows.append(row)
                kept += 1
        else:
            dropped += 1

    if filtered_rows:
        fieldnames = list(filtered_rows[0].keys())
        with open(FILTERED_MANIFEST_PATH, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            writer.writerows(filtered_rows)

    print(f"Toleranz: ±{max_diff}° zwischen Kamera-Blickrichtung und Peilung zum Briefkasten")
    print(f"Behalten (Kamera zeigt ungefähr zum Briefkasten): {kept}")
    print(f"Verworfen (Kamera zeigt woanders hin):        {dropped}")
    print(f"Ohne Blickrichtungs-Info (verworfen):          {no_compass}")
    print(f"Gefilterte Bilder: {FILTERED_IMAGES_DIR}")
    print(f"Gefiltertes Manifest: {FILTERED_MANIFEST_PATH}")


if __name__ == "__main__":
    main()
