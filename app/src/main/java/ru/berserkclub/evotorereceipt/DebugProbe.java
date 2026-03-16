package ru.berserkclub.evotorereceipt;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;

public final class DebugProbe {
    private static final String TAG = "BERSERK_DEBUG";
    private static final String LOCAL_LOG_FILE = "berserk_debug_events.log";

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    private DebugProbe() {
    }

    public static void event(
            Context context,
            String stage,
            String message,
            @Nullable Intent intent,
            @Nullable String receiptId,
            @Nullable Throwable error
    ) {
        if (!AppConfig.DEBUG_PROBE_ENABLED) {
            return;
        }

        try {
            JSONObject payload = buildPayload(context, stage, message, intent, receiptId, error);
            String line = payload.toString();

            Log.i(TAG, line);
            appendToLocalFile(context, line);
            postAsync(line);
        } catch (Throwable t) {
            Log.e(TAG, "DebugProbe.event failed", t);
        }
    }

    private static JSONObject buildPayload(
            Context context,
            String stage,
            String message,
            @Nullable Intent intent,
            @Nullable String receiptId,
            @Nullable Throwable error
    ) throws Exception {
        JSONObject json = new JSONObject();

        json.put("ts_utc", nowUtc());
        json.put("stage", safe(stage));
        json.put("message", safe(message));
        json.put("packageName", context.getPackageName());
        json.put("appId", BuildConfig.APPLICATION_ID);
        json.put("versionName", BuildConfig.VERSION_NAME);
        json.put("versionCode", BuildConfig.VERSION_CODE);
        json.put("manufacturer", safe(Build.MANUFACTURER));
        json.put("model", safe(Build.MODEL));
        json.put("brand", safe(Build.BRAND));
        json.put("sdkInt", Build.VERSION.SDK_INT);
        json.put("androidRelease", safe(Build.VERSION.RELEASE));
        json.put("receiptId", safe(receiptId));

        if (intent != null) {
            json.put("intentAction", safe(intent.getAction()));
            json.put("intentExtras", bundleToJson(intent.getExtras()));
        }

        if (error != null) {
            JSONObject err = new JSONObject();
            err.put("type", error.getClass().getName());
            err.put("message", safe(error.getMessage()));
            err.put("stack", Log.getStackTraceString(error));
            json.put("error", err);
        }

        return json;
    }

    private static JSONObject bundleToJson(@Nullable Bundle bundle) throws Exception {
        JSONObject json = new JSONObject();

        if (bundle == null) {
            return json;
        }

        for (String key : bundle.keySet()) {
            Object value = readBundleValue(bundle, key);

            if (value == null) {
                json.put(key, JSONObject.NULL);
            } else if (value instanceof Bundle) {
                json.put(key, bundleToJson((Bundle) value));
            } else if (value instanceof String
                    || value instanceof Integer
                    || value instanceof Long
                    || value instanceof Boolean
                    || value instanceof Double
                    || value instanceof Float) {
                json.put(key, value);
            } else if (value instanceof String[]) {
                JSONArray array = new JSONArray();
                String[] values = (String[]) value;
                for (String item : values) {
                    array.put(item == null ? JSONObject.NULL : item);
                }
                json.put(key, array);
            } else if (value instanceof int[]) {
                JSONArray array = new JSONArray();
                int[] values = (int[]) value;
                for (int item : values) {
                    array.put(item);
                }
                json.put(key, array);
            } else if (value instanceof long[]) {
                JSONArray array = new JSONArray();
                long[] values = (long[]) value;
                for (long item : values) {
                    array.put(item);
                }
                json.put(key, array);
            } else if (value instanceof boolean[]) {
                JSONArray array = new JSONArray();
                boolean[] values = (boolean[]) value;
                for (boolean item : values) {
                    array.put(item);
                }
                json.put(key, array);
            } else if (value instanceof double[]) {
                JSONArray array = new JSONArray();
                double[] values = (double[]) value;
                for (double item : values) {
                    array.put(item);
                }
                json.put(key, array);
            } else if (value instanceof float[]) {
                JSONArray array = new JSONArray();
                float[] values = (float[]) value;
                for (float item : values) {
                    array.put((double) item);
                }
                json.put(key, array);
            } else {
                json.put(key, String.valueOf(value));
            }
        }

        return json;
    }

    private static void appendToLocalFile(Context context, String line) {
        try (FileOutputStream fos = context.openFileOutput(LOCAL_LOG_FILE, Context.MODE_APPEND)) {
            fos.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (Throwable t) {
            Log.e(TAG, "appendToLocalFile failed", t);
        }
    }

    private static void postAsync(final String line) {
        if (!AppConfig.DEBUG_PROBE_ENABLED) {
            return;
        }

        if (AppConfig.DEBUG_PING_URL == null || AppConfig.DEBUG_PING_URL.trim().isEmpty()) {
            return;
        }

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(AppConfig.DEBUG_PING_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                if (AppConfig.DEBUG_PING_TOKEN != null && !AppConfig.DEBUG_PING_TOKEN.trim().isEmpty()) {
                    connection.setRequestProperty("X-Debug-Token", AppConfig.DEBUG_PING_TOKEN);
                }

                byte[] body = line.getBytes(StandardCharsets.UTF_8);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body);
                    os.flush();
                }

                int code = connection.getResponseCode();
                Log.i(TAG, "DebugProbe post responseCode=" + code);
            } catch (Throwable t) {
                Log.e(TAG, "DebugProbe postAsync failed", t);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "berserk-debug-probe").start();
    }

    public static String findReceiptId(@Nullable Intent intent) {
        if (intent == null) {
            return null;
        }

        Bundle extras = intent.getExtras();
        if (extras == null || extras.isEmpty()) {
            return null;
        }

        String[] directKeys = new String[]{
                "receiptId",
                "receipt_id",
                "receipt_id_string",
                "receiptUuid",
                "receipt_uuid",
                "uuid",
                "id",
                "document_uuid",
                "documentUuid"
        };

        for (String key : directKeys) {
            String value = readString(extras, key);
            if (notBlank(value)) {
                return value.trim();
            }
        }

        String[] jsonKeys = new String[]{
                "payload",
                "data",
                "message",
                "body"
        };

        for (String key : jsonKeys) {
            String raw = readString(extras, key);
            if (!notBlank(raw)) {
                continue;
            }

            String found = tryFindReceiptIdInJson(raw);
            if (notBlank(found)) {
                return found.trim();
            }
        }

        return null;
    }

    private static String tryFindReceiptIdInJson(String raw) {
        try {
            JSONObject json = new JSONObject(raw);

            String[] keys = new String[]{
                    "receiptId",
                    "receipt_id",
                    "receiptUuid",
                    "receipt_uuid",
                    "uuid",
                    "id",
                    "document_uuid",
                    "documentUuid"
            };

            for (String key : keys) {
                if (json.has(key) && !json.isNull(key)) {
                    String value = json.optString(key, null);
                    if (notBlank(value)) {
                        return value;
                    }
                }
            }

            Iterator<String> iterator = json.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                Object value = json.opt(key);
                if (value instanceof JSONObject) {
                    JSONObject nested = (JSONObject) value;
                    for (String nestedKey : keys) {
                        if (nested.has(nestedKey) && !nested.isNull(nestedKey)) {
                            String nestedValue = nested.optString(nestedKey, null);
                            if (notBlank(nestedValue)) {
                                return nestedValue;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    @Nullable
    private static Object readBundleValue(Bundle bundle, String key) {
        try {
            String stringValue = bundle.getString(key);
            if (stringValue != null) {
                return stringValue;
            }
        } catch (Throwable ignored) {
        }

        try {
            Bundle nested = bundle.getBundle(key);
            if (nested != null) {
                return nested;
            }
        } catch (Throwable ignored) {
        }

        try {
            CharSequence charSequence = bundle.getCharSequence(key);
            if (charSequence != null) {
                return charSequence.toString();
            }
        } catch (Throwable ignored) {
        }

        try {
            String[] values = bundle.getStringArray(key);
            if (values != null) {
                return values;
            }
        } catch (Throwable ignored) {
        }

        try {
            int[] values = bundle.getIntArray(key);
            if (values != null) {
                return values;
            }
        } catch (Throwable ignored) {
        }

        try {
            long[] values = bundle.getLongArray(key);
            if (values != null) {
                return values;
            }
        } catch (Throwable ignored) {
        }

        try {
            boolean[] values = bundle.getBooleanArray(key);
            if (values != null) {
                return values;
            }
        } catch (Throwable ignored) {
        }

        try {
            double[] values = bundle.getDoubleArray(key);
            if (values != null) {
                return values;
            }
        } catch (Throwable ignored) {
        }

        try {
            float[] values = bundle.getFloatArray(key);
            if (values != null) {
                return values;
            }
        } catch (Throwable ignored) {
        }

        try {
            return bundle.getBoolean(key);
        } catch (Throwable ignored) {
        }

        try {
            return bundle.getInt(key);
        } catch (Throwable ignored) {
        }

        try {
            return bundle.getLong(key);
        } catch (Throwable ignored) {
        }

        try {
            return bundle.getDouble(key);
        } catch (Throwable ignored) {
        }

        try {
            return bundle.getFloat(key);
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static String readString(Bundle extras, String key) {
        try {
            String value = extras.getString(key);
            if (value != null) {
                return value;
            }
        } catch (Throwable ignored) {
        }

        Object value = readBundleValue(extras, key);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean notBlank(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static String nowUtc() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }
}