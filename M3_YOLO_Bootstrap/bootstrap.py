#!/usr/bin/env python3
"""
M3 – YOLO-Trainingsdaten-Bootstrap für BriefkastenScout.

Sammelt Kandidaten-Fotos für Briefkästen (amenity=post_box) aus
OpenStreetMap (Overpass API) und nahegelegene Streetview-Fotos aus
Mapillary. Liefert KEINE fertigen YOLO-Labels (Bounding Boxes), sondern
Rohdaten + Manifest für die anschließende manuelle Annotation
(z. B. mit Roboflow oder LabelImg).

Nutzung:
    python3 bootstrap.py
"""

import concurrent.futures
import csv
import json
import math
import os
import urllib.error
import urllib.parse
import urllib.request

# ---------------------------------------------------------------------------
# Konfiguration
# ---------------------------------------------------------------------------

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
TOKEN_FILE = os.path.join(SCRIPT_DIR, "mapillary_token.txt")
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "output")
IMAGES_DIR = os.path.join(OUTPUT_DIR, "images")
MANIFEST_PATH = os.path.join(OUTPUT_DIR, "manifest.csv")
OBJECTS_WITHOUT_PHOTOS_PATH = os.path.join(OUTPUT_DIR, "postboxes_without_photos.csv")

OVERPASS_ENDPOINT = "https://overpass-api.de/api/interpreter"
OVERPASS_QUERY = """
[out:json][timeout:90];
area["name"="Berlin"]["boundary"="administrative"]["admin_level"="4"]->.searchArea;
(
  nwr["amenity"="post_box"](area.searchArea);
);
out center tags;
"""

MAPILLARY_GRAPH_ENDPOINT = "https://graph.mapillary.com/images"
SEARCH_RADIUS_M = 30          # Umkreis um den Briefkasten, in dem nach Fotos gesucht wird
MAX_IMAGES_PER_OBJECT = 3     # Wie viele Kandidaten-Fotos pro Briefkasten maximal geladen werden
MAX_WORKERS = 6               # Parallele Downloads (Mapillary-API + Bilddownload)


def load_mapillary_token():
    with open(TOKEN_FILE, "r", encoding="utf-8") as f:
        token = f.read().strip()
    if not token:
        raise RuntimeError(f"Kein Mapillary-Token in {TOKEN_FILE} gefunden.")
    return token


def fetch_berlin_postboxes():
    """Fragt alle Briefkästen (amenity=post_box) in Berlin über Overpass ab."""
    data = urllib.parse.urlencode({"data": OVERPASS_QUERY}).encode("utf-8")
    req = urllib.request.Request(
        OVERPASS_ENDPOINT,
        data=data,
        headers={"User-Agent": "BriefkastenScout-YOLO-Bootstrap/1.0 (Master Geodatenprozessierung)"},
    )
    with urllib.request.urlopen(req, timeout=90) as resp:
        result = json.loads(resp.read().decode("utf-8"))

    postboxes = []
    for elem in result.get("elements", []):
        if "lat" in elem and "lon" in elem:
            lat, lon = elem["lat"], elem["lon"]
        elif "center" in elem:
            lat, lon = elem["center"]["lat"], elem["center"]["lon"]
        else:
            continue
        postboxes.append({
            "osm_id": f'{elem["type"]}/{elem["id"]}',
            "lat": lat,
            "lon": lon,
            "tags": elem.get("tags", {}),
        })
    return postboxes


def bbox_around(lat, lon, radius_m):
    """Erzeugt eine Bounding-Box (west,south,east,north) mit radius_m Metern um einen Punkt."""
    dlat = radius_m / 111_320.0
    dlon = radius_m / (111_320.0 * math.cos(math.radians(lat)))
    return f"{lon - dlon},{lat - dlat},{lon + dlon},{lat + dlat}"


def fetch_nearby_mapillary_images(token, lat, lon, radius_m, limit):
    """Fragt die Mapillary Graph API nach Fotos im Umkreis eines Punktes ab."""
    params = {
        "access_token": token,
        "fields": "id,geometry,compass_angle,captured_at,thumb_1024_url",
        "bbox": bbox_around(lat, lon, radius_m),
        "limit": limit,
    }
    url = MAPILLARY_GRAPH_ENDPOINT + "?" + urllib.parse.urlencode(params)
    try:
        with urllib.request.urlopen(url, timeout=30) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            return body.get("data", [])
    except urllib.error.HTTPError as e:
        print(f"  [Mapillary-Fehler] {e.code} bei ({lat:.6f},{lon:.6f}): {e.read()[:200]}")
        return []
    except Exception as e:
        print(f"  [Mapillary-Fehler] {e} bei ({lat:.6f},{lon:.6f})")
        return []


def download_image(url, dest_path):
    req = urllib.request.Request(url, headers={"User-Agent": "BriefkastenScout-YOLO-Bootstrap/1.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        with open(dest_path, "wb") as f:
            f.write(resp.read())


def process_postbox(token, postbox):
    """Holt Mapillary-Bilder für einen Briefkasten und lädt sie herunter. Gibt Manifest-Zeilen zurück."""
    osm_id_safe = postbox["osm_id"].replace("/", "_")
    images = fetch_nearby_mapillary_images(
        token, postbox["lat"], postbox["lon"], SEARCH_RADIUS_M, MAX_IMAGES_PER_OBJECT
    )

    rows = []
    for img in images:
        image_id = img["id"]
        thumb_url = img.get("thumb_1024_url")
        if not thumb_url:
            continue
        filename = f"postbox_{osm_id_safe}_img_{image_id}.jpg"
        dest_path = os.path.join(IMAGES_DIR, filename)
        try:
            download_image(thumb_url, dest_path)
        except Exception as e:
            print(f"  [Download-Fehler] {image_id}: {e}")
            continue

        img_lon, img_lat = img["geometry"]["coordinates"]
        rows.append({
            "postbox_osm_id": postbox["osm_id"],
            "postbox_lat": postbox["lat"],
            "postbox_lon": postbox["lon"],
            "postbox_tags": json.dumps(postbox["tags"], ensure_ascii=False),
            "mapillary_image_id": image_id,
            "image_path": os.path.relpath(dest_path, OUTPUT_DIR),
            "image_lat": img_lat,
            "image_lon": img_lon,
            "compass_angle": img.get("compass_angle"),
            "captured_at_epoch_ms": img.get("captured_at"),
        })
    return postbox, rows


def main():
    os.makedirs(IMAGES_DIR, exist_ok=True)
    token = load_mapillary_token()

    print("1/3  Frage Overpass API nach Briefkästen (amenity=post_box) in Berlin ab ...")
    postboxes = fetch_berlin_postboxes()
    print(f"     -> {len(postboxes)} Briefkästen gefunden.")

    print(f"2/3  Suche Mapillary-Fotos im Umkreis von {SEARCH_RADIUS_M} m je Briefkasten "
          f"(max. {MAX_IMAGES_PER_OBJECT}/Objekt, {MAX_WORKERS} parallele Worker) ...")

    manifest_rows = []
    postboxes_without_photos = []

    with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        futures = {executor.submit(process_postbox, token, pb): pb for pb in postboxes}
        done_count = 0
        for future in concurrent.futures.as_completed(futures):
            postbox, rows = future.result()
            done_count += 1
            if rows:
                manifest_rows.extend(rows)
            else:
                postboxes_without_photos.append(postbox)
            if done_count % 100 == 0 or done_count == len(postboxes):
                print(f"     ... {done_count}/{len(postboxes)} Briefkästen verarbeitet "
                      f"({len(manifest_rows)} Bilder bisher heruntergeladen)")

    print("3/3  Schreibe Manifest und Zusammenfassung ...")
    with open(MANIFEST_PATH, "w", newline="", encoding="utf-8") as f:
        fieldnames = [
            "postbox_osm_id", "postbox_lat", "postbox_lon", "postbox_tags",
            "mapillary_image_id", "image_path", "image_lat", "image_lon",
            "compass_angle", "captured_at_epoch_ms",
        ]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(manifest_rows)

    with open(OBJECTS_WITHOUT_PHOTOS_PATH, "w", newline="", encoding="utf-8") as f:
        fieldnames = ["osm_id", "lat", "lon", "tags"]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for pb in postboxes_without_photos:
            writer.writerow({
                "osm_id": pb["osm_id"],
                "lat": pb["lat"],
                "lon": pb["lon"],
                "tags": json.dumps(pb["tags"], ensure_ascii=False),
            })

    print()
    print("=== Zusammenfassung ===")
    print(f"Briefkästen insgesamt:            {len(postboxes)}")
    print(f"Briefkästen mit >=1 Foto:         {len(postboxes) - len(postboxes_without_photos)}")
    print(f"Briefkästen ohne Mapillary-Foto:  {len(postboxes_without_photos)}")
    print(f"Heruntergeladene Bilder:          {len(manifest_rows)}")
    print(f"Manifest:                         {MANIFEST_PATH}")
    print(f"Bilder-Ordner:                    {IMAGES_DIR}")


if __name__ == "__main__":
    main()
