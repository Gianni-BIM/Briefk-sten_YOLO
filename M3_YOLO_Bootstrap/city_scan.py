#!/usr/bin/env python3
"""
Stadtweiter Abgleich: Scannt Mapillary-Streetview-Fotos flächendeckend über ein
Gebiet (nicht nur nahe bereits bekannter OSM-Briefkästen), lässt das trainierte
YOLO/TFLite-Modell (briefkasten_detector.tflite) über jedes Foto laufen, und prüft
für jede visuell erkannte Stelle per Overpass API, ob dort schon ein Briefkasten
in OpenStreetMap existiert.

Ergebnis: Liste von Kandidaten-Standorten, an denen das Modell visuell einen
Briefkasten erkennt, obwohl OSM dort (noch) keinen kennt -> mögliche echte
Kartierungslücke.

Nutzung:
    source .venv_infer/bin/activate
    python3 city_scan.py
"""

import concurrent.futures
import csv
import json
import math
import os
import urllib.error
import urllib.parse
import urllib.request

import numpy as np
from PIL import Image
from ai_edge_litert.interpreter import Interpreter

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
TOKEN_FILE = os.path.join(SCRIPT_DIR, "mapillary_token.txt")
MODEL_PATH = os.path.join(SCRIPT_DIR, "..", "BriefkastenScout", "app", "src", "main", "assets", "briefkasten_detector.tflite")

OUTPUT_DIR = os.path.join(SCRIPT_DIR, "city_scan_output")
IMAGES_DIR = os.path.join(OUTPUT_DIR, "images")
CANDIDATES_CSV = os.path.join(OUTPUT_DIR, "candidates_missing_in_osm.csv")
ALL_DETECTIONS_CSV = os.path.join(OUTPUT_DIR, "all_detections.csv")

# Scan-Gebiet: zentrales Berlin (Mitte/Kreuzberg/Neukölln/Tempelhof-Umgebung), ca. 6 x 6.7 km
SCAN_BBOX = {"west": 13.35, "south": 52.49, "east": 13.43, "north": 52.55}
GRID_CELL_M = 300          # Rastergröße für die flächendeckende Mapillary-Suche
IMAGES_PER_CELL = 2        # Max. Fotos pro Rasterzelle
CONFIDENCE_THRESHOLD = 0.30  # Etwas strenger als im App-Default (0.25), um Kandidatenliste sauberer zu halten
OSM_CHECK_RADIUS_M = 30
MAX_WORKERS = 6

OVERPASS_ENDPOINT = "https://overpass-api.de/api/interpreter"
MAPILLARY_GRAPH_ENDPOINT = "https://graph.mapillary.com/images"

INPUT_SIZE = 640


def load_mapillary_token():
    with open(TOKEN_FILE, "r", encoding="utf-8") as f:
        return f.read().strip()


def meters_to_deg(lat, radius_m):
    dlat = radius_m / 111_320.0
    dlon = radius_m / (111_320.0 * math.cos(math.radians(lat)))
    return dlat, dlon


def build_grid(bbox, cell_m):
    """Erzeugt eine Liste von (west,south,east,north)-Bboxen, die das Scan-Gebiet lückenlos abdecken."""
    cells = []
    lat = bbox["south"]
    while lat < bbox["north"]:
        dlat, dlon = meters_to_deg(lat, cell_m)
        lon = bbox["west"]
        while lon < bbox["east"]:
            cells.append((lon, lat, min(lon + dlon, bbox["east"]), min(lat + dlat, bbox["north"])))
            lon += dlon
        lat += dlat
    return cells


def fetch_images_in_bbox(token, bbox, limit):
    params = {
        "access_token": token,
        "fields": "id,geometry,compass_angle,captured_at,thumb_1024_url,camera_type",
        "bbox": f"{bbox[0]},{bbox[1]},{bbox[2]},{bbox[3]}",
        "limit": limit,
    }
    url = MAPILLARY_GRAPH_ENDPOINT + "?" + urllib.parse.urlencode(params)
    try:
        with urllib.request.urlopen(url, timeout=30) as resp:
            images = json.loads(resp.read().decode("utf-8")).get("data", [])
    except Exception:
        return []
    # 360°-Panoramafotos (spherical/equirectangular) ausschließen: Das Modell wurde auf
    # normalen perspektivischen Fotos trainiert, auf Fisheye-verzerrten Panoramen liefert
    # es unzuverlässige Ergebnisse (Formen/Objekte sind geometrisch verzerrt).
    return [img for img in images if img.get("camera_type") in (None, "perspective")]


def download_image(url, dest_path):
    req = urllib.request.Request(url, headers={"User-Agent": "BriefkastenScout-CityScan/1.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        with open(dest_path, "wb") as f:
            f.write(resp.read())


def check_postbox_in_osm(lat, lon, radius_m):
    """Analog zu OverpassApiClient.checkCourtInOsm in der App: MATCH/MISSING/ERROR."""
    query = (
        f'[out:json][timeout:25];'
        f'(nwr["amenity"="post_box"](around:{radius_m},{lat:.7f},{lon:.7f}););'
        f'out center;'
    )
    data = urllib.parse.urlencode({"data": query}).encode("utf-8")
    req = urllib.request.Request(OVERPASS_ENDPOINT, data=data,
                                  headers={"User-Agent": "BriefkastenScout-CityScan/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            result = json.loads(resp.read().decode("utf-8"))
            elements = result.get("elements", [])
            if elements:
                elem = elements[0]
                osm_id = f'{elem.get("type", "osm")}/{elem.get("id", "?")}'
                return "MATCH", osm_id
            return "MISSING", None
    except Exception as e:
        return "ERROR", str(e)


def run_model(interpreter, input_index, output_index, is_channels_first, image_path):
    img = Image.open(image_path).convert("RGB").resize((INPUT_SIZE, INPUT_SIZE))
    arr = np.asarray(img).astype(np.float32) / 255.0  # HWC [0,1]
    if is_channels_first:
        tensor = np.transpose(arr, (2, 0, 1))[np.newaxis, ...]
    else:
        tensor = arr[np.newaxis, ...]
    interpreter.set_tensor(input_index, tensor.astype(np.float32))
    interpreter.invoke()
    pred = interpreter.get_tensor(output_index)[0]  # [5, 8400] oder [8400, 5]
    scores = pred[4, :] if pred.shape[0] < pred.shape[1] else pred[:, 4]
    return float(scores.max())


def main():
    os.makedirs(IMAGES_DIR, exist_ok=True)
    token = load_mapillary_token()

    interpreter = Interpreter(model_path=MODEL_PATH)
    interpreter.allocate_tensors()
    inp = interpreter.get_input_details()[0]
    out = interpreter.get_output_details()[0]
    is_channels_first = len(inp["shape"]) == 4 and inp["shape"][1] == 3
    print(f"Modell-Input-Shape: {inp['shape']} (channels_first={is_channels_first})")

    print("1/3  Baue Suchraster über das Scan-Gebiet ...")
    cells = build_grid(SCAN_BBOX, GRID_CELL_M)
    print(f"     -> {len(cells)} Rasterzellen (~{GRID_CELL_M} m Kantenlänge)")

    print(f"2/3  Frage Mapillary-Fotos je Zelle ab (max. {IMAGES_PER_CELL}/Zelle) und lade sie herunter ...")
    all_images = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        futures = [executor.submit(fetch_images_in_bbox, token, cell, IMAGES_PER_CELL) for cell in cells]
        for i, future in enumerate(concurrent.futures.as_completed(futures)):
            all_images.extend(future.result())
            if (i + 1) % 100 == 0 or (i + 1) == len(cells):
                print(f"     ... {i + 1}/{len(cells)} Zellen abgefragt ({len(all_images)} Fotos gefunden bisher)")

    # Duplikate entfernen (Fotos an Zellgrenzen können doppelt gefunden werden)
    unique_images = {img["id"]: img for img in all_images}.values()
    print(f"     -> {len(unique_images)} eindeutige Fotos im gesamten Scan-Gebiet gefunden")

    def download_one(img):
        dest = os.path.join(IMAGES_DIR, f"{img['id']}.jpg")
        if not os.path.exists(dest):
            try:
                download_image(img["thumb_1024_url"], dest)
            except Exception:
                return None
        return img, dest

    downloaded = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        for i, result in enumerate(executor.map(download_one, unique_images)):
            if result:
                downloaded.append(result)
            if (i + 1) % 200 == 0:
                print(f"     ... {i + 1} Fotos heruntergeladen")
    print(f"     -> {len(downloaded)} Fotos erfolgreich heruntergeladen")

    print("3/3  Führe YOLO-Inferenz aus und prüfe Kandidaten gegen OSM ...")
    all_rows = []
    candidates = []
    for i, (img, path) in enumerate(downloaded):
        try:
            score = run_model(interpreter, inp["index"], out["index"], is_channels_first, path)
        except Exception as e:
            continue

        lon, lat = img["geometry"]["coordinates"]
        row = {
            "mapillary_image_id": img["id"],
            "lat": lat,
            "lon": lon,
            "confidence": round(score, 4),
            "image_path": os.path.relpath(path, OUTPUT_DIR),
        }
        all_rows.append(row)

        if score >= CONFIDENCE_THRESHOLD:
            osm_status, osm_id = check_postbox_in_osm(lat, lon, OSM_CHECK_RADIUS_M)
            row["osm_status"] = osm_status
            row["osm_id"] = osm_id
            if osm_status == "MISSING":
                candidates.append(row)

        if (i + 1) % 100 == 0 or (i + 1) == len(downloaded):
            print(f"     ... {i + 1}/{len(downloaded)} Fotos analysiert "
                  f"({len(candidates)} Kandidaten für fehlende Briefkästen bisher)")

    with open(ALL_DETECTIONS_CSV, "w", newline="", encoding="utf-8") as f:
        fieldnames = ["mapillary_image_id", "lat", "lon", "confidence", "image_path", "osm_status", "osm_id"]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in all_rows:
            writer.writerow({k: row.get(k, "") for k in fieldnames})

    with open(CANDIDATES_CSV, "w", newline="", encoding="utf-8") as f:
        fieldnames = ["mapillary_image_id", "lat", "lon", "confidence", "image_path", "osm_status", "osm_id"]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(candidates)

    print()
    print("=== Zusammenfassung ===")
    print(f"Rasterzellen abgefragt:              {len(cells)}")
    print(f"Eindeutige Fotos im Scan-Gebiet:      {len(unique_images)}")
    print(f"Analysierte Fotos:                    {len(all_rows)}")
    print(f"Fotos über Confidence-Threshold {CONFIDENCE_THRESHOLD}: {sum(1 for r in all_rows if r['confidence'] >= CONFIDENCE_THRESHOLD)}")
    print(f"Kandidaten (erkannt, aber fehlt in OSM): {len(candidates)}")
    print(f"Alle Detections:  {ALL_DETECTIONS_CSV}")
    print(f"Kandidatenliste:  {CANDIDATES_CSV}")
    for c in candidates[:10]:
        print(f"  -> {c['lat']:.6f},{c['lon']:.6f}  Konfidenz={c['confidence']}  Foto={c['mapillary_image_id']}")


if __name__ == "__main__":
    main()
