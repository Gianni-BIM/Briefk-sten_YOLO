Prompt 1 – App-Grundgerüst (M1: Foto + GPS + Karte)

Erstelle eine Android-App "Briefkasten Scout" in Java zur mobilen Erfassung
von Briefkästen (amenity=post_box) mit folgenden Funktionen:
1. Eine MapLibre GL Karte (Vektortiles, OpenStreetMap-Style) als Hauptansicht.
2. Ein Ortungs-Button (Floating Action Button), der die aktuelle GPS-Position
   des Nutzers über FusedLocationProviderClient ermittelt und auf der Karte
   zentriert/markiert.
3. Einen Kamera-Button, der die native Kamera-App öffnet, ein Foto eines
   Briefkastens aufnimmt und lokal speichert (App-eigenes Bilderverzeichnis).
4. Nach der Aufnahme wird das Foto zusammen mit der GPS-Position (Latitude,
   Longitude, Zeitstempel), die zum Aufnahmezeitpunkt ermittelt wurde, als
   Datensatz in einer lokalen SQLite-Datenbank (über SQLiteOpenHelper)
   gespeichert.
5. Zeige eine einfache Liste/Übersicht aller bisher erfassten Briefkästen mit
   Position und Zeitstempel (RecyclerView mit Adapter).
Verwende die notwendigen Berechtigungen für Kamera und Standort (Manifest +
Runtime Permissions). Zielplattform: Android, minSdk 26. Kein Kotlin, nur
Java-Klassen und XML-Layouts.
