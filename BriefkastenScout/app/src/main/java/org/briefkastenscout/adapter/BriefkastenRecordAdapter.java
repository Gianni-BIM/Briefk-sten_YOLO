package org.briefkastenscout.adapter;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import org.briefkastenscout.R;
import org.briefkastenscout.model.BriefkastenRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView-Adapter zur Darstellung aller erfassten Briefkästen mit Geokoordinaten
 * und farblicher OSM-Status-Badge (Grün = In OSM, Rot = Fehlt in OSM, Grau = Fehler).
 * Foto, Zeitstempel und visuelle YOLO-Erkennung werden hier bewusst nicht angezeigt
 * (weiterhin im Detail-Dialog sichtbar), da sie sich auf dem Emulator ohne echte
 * Kamera nicht sinnvoll verifizieren lassen.
 */
public class BriefkastenRecordAdapter extends RecyclerView.Adapter<BriefkastenRecordAdapter.RecordViewHolder> {

    public interface OnRecordActionListener {
        void onShowOnMap(BriefkastenRecord record);
        void onDelete(BriefkastenRecord record);
        void onItemClick(BriefkastenRecord record);
        void onRetryOsmCheck(BriefkastenRecord record);
    }

    private final Context context;
    private final List<BriefkastenRecord> recordList = new ArrayList<>();
    private final OnRecordActionListener actionListener;

    public BriefkastenRecordAdapter(Context context, OnRecordActionListener actionListener) {
        this.context = context;
        this.actionListener = actionListener;
    }

    public void setRecords(List<BriefkastenRecord> records) {
        this.recordList.clear();
        if (records != null) {
            this.recordList.addAll(records);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_record, parent, false);
        return new RecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordViewHolder holder, int position) {
        BriefkastenRecord record = recordList.get(position);
        holder.bind(record, position);
    }

    @Override
    public int getItemCount() {
        return recordList.size();
    }

    class RecordViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView cardRecord;
        private final TextView tvTitle;
        private final TextView tvCoords;

        // OSM Badge Komponenten
        private final LinearLayout layoutOsmBadge;
        private final View viewStatusDot;
        private final TextView tvOsmStatus;

        private final ImageButton btnRetryOsm;
        private final ImageButton btnShowOnMap;
        private final ImageButton btnDelete;

        public RecordViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRecord = itemView.findViewById(R.id.card_record);
            tvTitle = itemView.findViewById(R.id.tv_record_title);
            tvCoords = itemView.findViewById(R.id.tv_record_coords);

            layoutOsmBadge = itemView.findViewById(R.id.layout_osm_badge);
            viewStatusDot = itemView.findViewById(R.id.view_status_dot);
            tvOsmStatus = itemView.findViewById(R.id.tv_osm_status);

            btnRetryOsm = itemView.findViewById(R.id.btn_retry_osm);
            btnShowOnMap = itemView.findViewById(R.id.btn_show_on_map);
            btnDelete = itemView.findViewById(R.id.btn_delete_record);
        }

        public void bind(BriefkastenRecord record, int position) {
            tvTitle.setText(context.getString(R.string.app_name) + " #" + record.getId());
            tvCoords.setText(record.getFormattedCoordinates());

            // ==========================================
            // 1. OSM STATUS-BADGE LOGIK (Grün, Rot, Grau)
            // ==========================================
            int strokeColor;
            int osmBadgeBgColor;
            int osmStatusColor;
            String osmStatusText;
            boolean showRetry = false;

            if (record.isOsmMatched()) {
                // GRÜN: In OSM erfasst
                osmStatusColor = ContextCompat.getColor(context, R.color.osm_match);
                osmBadgeBgColor = ContextCompat.getColor(context, R.color.osm_match_bg);
                strokeColor = ContextCompat.getColor(context, R.color.osm_match);
                osmStatusText = context.getString(R.string.osm_status_match);

            } else if (record.isOsmMissing()) {
                // ROT: Nicht in OSM erfasst (Fehlt in OSM)
                osmStatusColor = ContextCompat.getColor(context, R.color.osm_missing);
                osmBadgeBgColor = ContextCompat.getColor(context, R.color.osm_missing_bg);
                strokeColor = ContextCompat.getColor(context, R.color.osm_missing);
                osmStatusText = context.getString(R.string.osm_status_missing);

            } else if (record.isOsmError()) {
                // GRAU: Prüfung fehlgeschlagen (Fehler / Timeout / kein Netz)
                osmStatusColor = ContextCompat.getColor(context, R.color.osm_error);
                osmBadgeBgColor = ContextCompat.getColor(context, R.color.osm_error_bg);
                strokeColor = ContextCompat.getColor(context, R.color.osm_error);
                osmStatusText = context.getString(R.string.osm_status_error);
                showRetry = true;

            } else {
                // ORANGE / AMBER: In Prüfung (PENDING)
                osmStatusColor = ContextCompat.getColor(context, R.color.osm_pending);
                osmBadgeBgColor = ContextCompat.getColor(context, R.color.osm_pending_bg);
                strokeColor = ContextCompat.getColor(context, R.color.divider);
                osmStatusText = context.getString(R.string.osm_status_pending);
            }

            // OSM Badge stylen
            cardRecord.setStrokeColor(strokeColor);
            tvOsmStatus.setTextColor(osmStatusColor);
            tvOsmStatus.setText(osmStatusText);

            GradientDrawable dotDrawable = (GradientDrawable) viewStatusDot.getBackground().mutate();
            dotDrawable.setColor(osmStatusColor);

            GradientDrawable osmBadgeDrawable = (GradientDrawable) layoutOsmBadge.getBackground().mutate();
            osmBadgeDrawable.setColor(osmBadgeBgColor);

            // Retry Button Sichtbarkeit & Event
            btnRetryOsm.setVisibility(showRetry ? View.VISIBLE : View.GONE);
            btnRetryOsm.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onRetryOsmCheck(record);
                }
            });

            // Allgemeine Listen-Klicks
            itemView.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onItemClick(record);
                }
            });

            btnShowOnMap.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onShowOnMap(record);
                }
            });

            btnDelete.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onDelete(record);
                }
            });
        }
    }
}
