package org.briefkastenscout.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.briefkastenscout.model.BriefkastenRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLiteOpenHelper zur lokalen Persistierung aller erfassten Briefkästen mit Foto, Geodaten,
 * dem Overpass-OSM-Prüfergebnis und dem visuellen YOLO-Objekterkennungsergebnis.
 */
public class BriefkastenDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "briefkastenscout.db";
    private static final int DATABASE_VERSION = 3; // Upgrade auf Version 3 für visuelle YOLO-Erkennung

    public static final String TABLE_RECORDS = "briefkasten_records";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_IMAGE_PATH = "image_path";
    public static final String COLUMN_LATITUDE = "latitude";
    public static final String COLUMN_LONGITUDE = "longitude";
    public static final String COLUMN_TIMESTAMP = "timestamp";

    // Spalten für den Overpass API OSM-Abgleich
    public static final String COLUMN_OSM_STATUS = "osm_status";
    public static final String COLUMN_OSM_ID = "osm_id";
    public static final String COLUMN_OSM_DISTANCE = "osm_distance";
    public static final String COLUMN_OSM_ERROR = "osm_error";

    // Spalten für die visuelle On-Device YOLO-Erkennung (TensorFlow Lite)
    public static final String COLUMN_VISUAL_STATUS = "visual_status";
    public static final String COLUMN_VISUAL_CONFIDENCE = "visual_confidence";
    public static final String COLUMN_VISUAL_ERROR = "visual_error";

    private static final String SQL_CREATE_TABLE =
            "CREATE TABLE " + TABLE_RECORDS + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_IMAGE_PATH + " TEXT NOT NULL, " +
                    COLUMN_LATITUDE + " REAL NOT NULL, " +
                    COLUMN_LONGITUDE + " REAL NOT NULL, " +
                    COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
                    COLUMN_OSM_STATUS + " TEXT DEFAULT 'PENDING', " +
                    COLUMN_OSM_ID + " TEXT, " +
                    COLUMN_OSM_DISTANCE + " REAL, " +
                    COLUMN_OSM_ERROR + " TEXT, " +
                    COLUMN_VISUAL_STATUS + " TEXT DEFAULT 'VISUAL_PENDING', " +
                    COLUMN_VISUAL_CONFIDENCE + " REAL, " +
                    COLUMN_VISUAL_ERROR + " TEXT" +
                    ");";

    public BriefkastenDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Migration auf v2: OSM-Spalten hinzufügen
            db.execSQL("ALTER TABLE " + TABLE_RECORDS + " ADD COLUMN " + COLUMN_OSM_STATUS + " TEXT DEFAULT 'PENDING';");
            db.execSQL("ALTER TABLE " + TABLE_RECORDS + " ADD COLUMN " + COLUMN_OSM_ID + " TEXT;");
            db.execSQL("ALTER TABLE " + TABLE_RECORDS + " ADD COLUMN " + COLUMN_OSM_DISTANCE + " REAL;");
            db.execSQL("ALTER TABLE " + TABLE_RECORDS + " ADD COLUMN " + COLUMN_OSM_ERROR + " TEXT;");
        }
        if (oldVersion < 3) {
            // Migration auf v3: Visuelle YOLO-Spalten hinzufügen
            db.execSQL("ALTER TABLE " + TABLE_RECORDS + " ADD COLUMN " + COLUMN_VISUAL_STATUS + " TEXT DEFAULT 'VISUAL_PENDING';");
            db.execSQL("ALTER TABLE " + TABLE_RECORDS + " ADD COLUMN " + COLUMN_VISUAL_CONFIDENCE + " REAL;");
            db.execSQL("ALTER TABLE " + TABLE_RECORDS + " ADD COLUMN " + COLUMN_VISUAL_ERROR + " TEXT;");
        }
    }

    /**
     * Fügt einen neuen Briefkasten-Datensatz in die SQLite-Datenbank ein.
     */
    public long insertRecord(BriefkastenRecord record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_IMAGE_PATH, record.getImagePath());
        values.put(COLUMN_LATITUDE, record.getLatitude());
        values.put(COLUMN_LONGITUDE, record.getLongitude());
        values.put(COLUMN_TIMESTAMP, record.getTimestamp());
        values.put(COLUMN_OSM_STATUS, record.getOsmStatus() != null ? record.getOsmStatus() : BriefkastenRecord.STATUS_PENDING);
        values.put(COLUMN_OSM_ID, record.getOsmId());
        values.put(COLUMN_OSM_DISTANCE, record.getOsmDistance());
        values.put(COLUMN_OSM_ERROR, record.getOsmErrorMessage());
        values.put(COLUMN_VISUAL_STATUS, record.getVisualStatus() != null ? record.getVisualStatus() : BriefkastenRecord.VISUAL_PENDING);
        values.put(COLUMN_VISUAL_CONFIDENCE, record.getVisualConfidence());
        values.put(COLUMN_VISUAL_ERROR, record.getVisualErrorMessage());

        long newId = db.insert(TABLE_RECORDS, null, values);
        if (newId != -1) {
            record.setId(newId);
        }
        return newId;
    }

    /**
     * Aktualisiert das Overpass-OSM-Prüfergebnis eines Datensatzes.
     */
    public boolean updateOsmStatus(long id, String status, String osmId, Double distance, String errorMsg) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_OSM_STATUS, status);
        values.put(COLUMN_OSM_ID, osmId);
        values.put(COLUMN_OSM_DISTANCE, distance);
        values.put(COLUMN_OSM_ERROR, errorMsg);

        int rows = db.update(TABLE_RECORDS, values, COLUMN_ID + "=?", new String[]{String.valueOf(id)});
        return rows > 0;
    }

    /**
     * Aktualisiert das visuelle YOLO-Prüfergebnis eines Datensatzes.
     */
    public boolean updateVisualStatus(long id, String status, Float confidence, String errorMsg) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_VISUAL_STATUS, status);
        values.put(COLUMN_VISUAL_CONFIDENCE, confidence);
        values.put(COLUMN_VISUAL_ERROR, errorMsg);

        int rows = db.update(TABLE_RECORDS, values, COLUMN_ID + "=?", new String[]{String.valueOf(id)});
        return rows > 0;
    }

    /**
     * Liest alle gespeicherten Datensätze sortiert nach Zeitstempel (neueste zuerst).
     */
    public List<BriefkastenRecord> getAllRecords() {
        List<BriefkastenRecord> recordList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String selectQuery = "SELECT * FROM " + TABLE_RECORDS + " ORDER BY " + COLUMN_TIMESTAMP + " DESC";
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor != null && cursor.moveToFirst()) {
            int idIndex = cursor.getColumnIndex(COLUMN_ID);
            int pathIndex = cursor.getColumnIndex(COLUMN_IMAGE_PATH);
            int latIndex = cursor.getColumnIndex(COLUMN_LATITUDE);
            int lngIndex = cursor.getColumnIndex(COLUMN_LONGITUDE);
            int timeIndex = cursor.getColumnIndex(COLUMN_TIMESTAMP);

            int statusIndex = cursor.getColumnIndex(COLUMN_OSM_STATUS);
            int osmIdIndex = cursor.getColumnIndex(COLUMN_OSM_ID);
            int distIndex = cursor.getColumnIndex(COLUMN_OSM_DISTANCE);
            int errIndex = cursor.getColumnIndex(COLUMN_OSM_ERROR);

            int visualStatusIndex = cursor.getColumnIndex(COLUMN_VISUAL_STATUS);
            int visualConfIndex = cursor.getColumnIndex(COLUMN_VISUAL_CONFIDENCE);
            int visualErrIndex = cursor.getColumnIndex(COLUMN_VISUAL_ERROR);

            do {
                long id = cursor.getLong(idIndex);
                String path = cursor.getString(pathIndex);
                double lat = cursor.getDouble(latIndex);
                double lng = cursor.getDouble(lngIndex);
                long timestamp = cursor.getLong(timeIndex);

                String osmStatus = statusIndex != -1 && !cursor.isNull(statusIndex) ? cursor.getString(statusIndex) : BriefkastenRecord.STATUS_PENDING;
                String osmId = osmIdIndex != -1 && !cursor.isNull(osmIdIndex) ? cursor.getString(osmIdIndex) : null;
                Double osmDistance = distIndex != -1 && !cursor.isNull(distIndex) ? cursor.getDouble(distIndex) : null;
                String osmError = errIndex != -1 && !cursor.isNull(errIndex) ? cursor.getString(errIndex) : null;

                String visualStatus = visualStatusIndex != -1 && !cursor.isNull(visualStatusIndex) ? cursor.getString(visualStatusIndex) : BriefkastenRecord.VISUAL_PENDING;
                Float visualConfidence = visualConfIndex != -1 && !cursor.isNull(visualConfIndex) ? cursor.getFloat(visualConfIndex) : null;
                String visualError = visualErrIndex != -1 && !cursor.isNull(visualErrIndex) ? cursor.getString(visualErrIndex) : null;

                BriefkastenRecord record = new BriefkastenRecord(id, path, lat, lng, timestamp, osmStatus, osmId, osmDistance, osmError,
                        visualStatus, visualConfidence, visualError);
                recordList.add(record);
            } while (cursor.moveToNext());

            cursor.close();
        }

        return recordList;
    }

    /**
     * Liest einen einzelnen Datensatz anhand der ID.
     */
    public BriefkastenRecord getRecordById(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_RECORDS, null, COLUMN_ID + "=?",
                new String[]{String.valueOf(id)}, null, null, null);

        BriefkastenRecord record = null;
        if (cursor != null && cursor.moveToFirst()) {
            int idIndex = cursor.getColumnIndex(COLUMN_ID);
            int pathIndex = cursor.getColumnIndex(COLUMN_IMAGE_PATH);
            int latIndex = cursor.getColumnIndex(COLUMN_LATITUDE);
            int lngIndex = cursor.getColumnIndex(COLUMN_LONGITUDE);
            int timeIndex = cursor.getColumnIndex(COLUMN_TIMESTAMP);

            int statusIndex = cursor.getColumnIndex(COLUMN_OSM_STATUS);
            int osmIdIndex = cursor.getColumnIndex(COLUMN_OSM_ID);
            int distIndex = cursor.getColumnIndex(COLUMN_OSM_DISTANCE);
            int errIndex = cursor.getColumnIndex(COLUMN_OSM_ERROR);

            int visualStatusIndex = cursor.getColumnIndex(COLUMN_VISUAL_STATUS);
            int visualConfIndex = cursor.getColumnIndex(COLUMN_VISUAL_CONFIDENCE);
            int visualErrIndex = cursor.getColumnIndex(COLUMN_VISUAL_ERROR);

            String osmStatus = statusIndex != -1 && !cursor.isNull(statusIndex) ? cursor.getString(statusIndex) : BriefkastenRecord.STATUS_PENDING;
            String osmId = osmIdIndex != -1 && !cursor.isNull(osmIdIndex) ? cursor.getString(osmIdIndex) : null;
            Double osmDistance = distIndex != -1 && !cursor.isNull(distIndex) ? cursor.getDouble(distIndex) : null;
            String osmError = errIndex != -1 && !cursor.isNull(errIndex) ? cursor.getString(errIndex) : null;

            String visualStatus = visualStatusIndex != -1 && !cursor.isNull(visualStatusIndex) ? cursor.getString(visualStatusIndex) : BriefkastenRecord.VISUAL_PENDING;
            Float visualConfidence = visualConfIndex != -1 && !cursor.isNull(visualConfIndex) ? cursor.getFloat(visualConfIndex) : null;
            String visualError = visualErrIndex != -1 && !cursor.isNull(visualErrIndex) ? cursor.getString(visualErrIndex) : null;

            record = new BriefkastenRecord(
                    cursor.getLong(idIndex),
                    cursor.getString(pathIndex),
                    cursor.getDouble(latIndex),
                    cursor.getDouble(lngIndex),
                    cursor.getLong(timeIndex),
                    osmStatus,
                    osmId,
                    osmDistance,
                    osmError,
                    visualStatus,
                    visualConfidence,
                    visualError
            );
            cursor.close();
        }
        return record;
    }

    /**
     * Löscht einen Datensatz aus der Datenbank anhand der ID.
     */
    public boolean deleteRecord(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsDeleted = db.delete(TABLE_RECORDS, COLUMN_ID + "=?", new String[]{String.valueOf(id)});
        return rowsDeleted > 0;
    }

    /**
     * Gibt die Anzahl aller erfassten Datensätze zurück.
     */
    public int getRecordCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_RECORDS, null);
        int count = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();
        }
        return count;
    }
}
