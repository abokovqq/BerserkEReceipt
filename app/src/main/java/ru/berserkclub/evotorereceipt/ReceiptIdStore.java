package ru.berserkclub.evotorereceipt;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ReceiptIdStore {

    private static final String PREFS_NAME = "berserk_receipt_store";

    private static final String KEY_IDS = "processed_receipt_ids";
    private static final int MAX_IDS = 50;

    private static final String KEY_RECEIPT_ID = "last_receipt_id";
    private static final String KEY_UPDATED_AT = "last_receipt_updated_at";

    private ReceiptIdStore() {
    }

    public static synchronized boolean isProcessed(Context context, String receiptId) {
        String normalized = normalize(receiptId);
        if (normalized == null) {
            return false;
        }

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String raw = prefs.getString(KEY_IDS, null);
        if (raw == null || raw.trim().isEmpty()) {
            return false;
        }

        String[] parts = raw.split("\\n");
        for (String part : parts) {
            if (normalized.equals(normalize(part))) {
                return true;
            }
        }

        return false;
    }

    public static synchronized void markProcessed(Context context, String receiptId) {
        String normalized = normalize(receiptId);
        if (normalized == null) {
            return;
        }

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String raw = prefs.getString(KEY_IDS, null);

        List<String> ids = new ArrayList<>();

        if (raw != null && !raw.trim().isEmpty()) {
            String[] parts = raw.split("\\n");
            for (String part : parts) {
                String value = normalize(part);
                if (value != null && !normalized.equals(value)) {
                    ids.add(value);
                }
            }
        }

        ids.add(normalized);

        while (ids.size() > MAX_IDS) {
            ids.remove(0);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(ids.get(i));
        }

        prefs.edit().putString(KEY_IDS, sb.toString()).apply();
    }

    public static synchronized void save(Context context, String receiptId) {
        String normalized = normalize(receiptId);
        if (normalized == null) {
            return;
        }

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        prefs.edit()
                .putString(KEY_RECEIPT_ID, normalized)
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply();
    }

    @Nullable
    public static synchronized String get(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        return normalize(prefs.getString(KEY_RECEIPT_ID, null));
    }

    public static synchronized long getUpdatedAt(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        return prefs.getLong(KEY_UPDATED_AT, 0L);
    }

    public static synchronized void clear(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        prefs.edit()
                .remove(KEY_RECEIPT_ID)
                .remove(KEY_UPDATED_AT)
                .apply();
    }

    @Nullable
    private static String normalize(@Nullable String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
