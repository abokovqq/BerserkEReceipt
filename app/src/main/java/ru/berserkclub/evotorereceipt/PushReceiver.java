package ru.berserkclub.evotorereceipt;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ru.evotor.pushNotifications.PushNotificationReceiver;

public class PushReceiver extends PushNotificationReceiver {

    private static final String TAG = "ru.berserkclub.evotorereceipt";

    @Override
    public void onReceivePushNotification(@NonNull Context context, @NonNull Bundle data, long messageId) {
        Intent debugIntent = toDebugIntent(data, messageId);
        String receiptIdForDebug = trimToNull(data.getString("receiptId"));

        DebugProbe.event(
                context,
                "push_received",
                "Evotor push received, messageId=" + messageId,
                debugIntent,
                receiptIdForDebug,
                null
        );

        String type = trimToNull(data.getString("type"));
        if (!"send_receipt".equals(type)) {
            Log.i(TAG, "Push ignored, unsupported type=" + type + ", messageId=" + messageId);

            DebugProbe.event(
                    context,
                    "push_ignored",
                    "Unsupported push type: " + type,
                    debugIntent,
                    receiptIdForDebug,
                    null
            );
            return;
        }

        String receiptId = trimToNull(data.getString("receiptId"));
        String itemName = trimToNull(data.getString("itemName"));
        String price = trimToNull(data.getString("price"));
        String quantity = trimToNull(data.getString("quantity"));
        String email = trimToNull(data.getString("email"));
        String phone = trimToNull(data.getString("phone"));
        String paymentPlace = trimToNull(data.getString("paymentPlace"));

        if (receiptId == null) {
            Log.e(TAG, "Push missing receiptId, messageId=" + messageId);

            DebugProbe.event(
                    context,
                    "push_missing_receipt_id",
                    "Push missing receiptId",
                    debugIntent,
                    null,
                    null
            );
            return;
        }

        if (ReceiptIdStore.isProcessed(context, receiptId)) {
            Log.i(TAG, "Duplicate receipt ignored, receiptId=" + receiptId + ", messageId=" + messageId);

            DebugProbe.event(
                    context,
                    "push_duplicate_ignored",
                    "Duplicate receipt ignored",
                    debugIntent,
                    receiptId,
                    null
            );
            return;
        }

        if (itemName == null || price == null || quantity == null) {
            Log.e(TAG, "Push missing required receipt fields, receiptId=" + receiptId + ", messageId=" + messageId);

            DebugProbe.event(
                    context,
                    "push_missing_required_fields",
                    "Push missing itemName/price/quantity",
                    debugIntent,
                    receiptId,
                    null
            );
            return;
        }

        if (phone == null && email == null) {
            Log.e(TAG, "Push missing phone/email, receiptId=" + receiptId + ", messageId=" + messageId);

            DebugProbe.event(
                    context,
                    "push_missing_contact",
                    "Push missing phone/email",
                    debugIntent,
                    receiptId,
                    null
            );
            return;
        }

        if (paymentPlace == null) {
            Log.e(TAG, "Push missing paymentPlace, receiptId=" + receiptId + ", messageId=" + messageId);

            DebugProbe.event(
                    context,
                    "push_missing_payment_place",
                    "Push missing paymentPlace",
                    debugIntent,
                    receiptId,
                    null
            );
            return;
        }

        ReceiptIdStore.save(context, receiptId);

        DebugProbe.event(
                context,
                "push_receipt_saved",
                "Receipt id saved for processing",
                debugIntent,
                receiptId,
                null
        );

        Intent intent = new Intent(context, ReceiptDispatchActivity.class);
        intent.setAction(ReceiptDispatchActivity.ACTION_SEND_RECEIPT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        intent.putExtra(ReceiptDispatchActivity.EXTRA_RECEIPT_ID, receiptId);
        intent.putExtra(ReceiptDispatchActivity.EXTRA_ITEM_NAME, itemName);
        intent.putExtra(ReceiptDispatchActivity.EXTRA_PRICE, price);
        intent.putExtra(ReceiptDispatchActivity.EXTRA_QUANTITY, quantity);
        intent.putExtra(ReceiptDispatchActivity.EXTRA_PAYMENT_PLACE, paymentPlace);

        if (phone != null) {
            intent.putExtra(ReceiptDispatchActivity.EXTRA_PHONE, phone);
        } else {
            intent.putExtra(ReceiptDispatchActivity.EXTRA_EMAIL, email);
        }

        try {
            Log.i(TAG, "Starting ReceiptDispatchActivity, receiptId=" + receiptId + ", messageId=" + messageId);
            context.startActivity(intent);

            DebugProbe.event(
                    context,
                    "dispatch_activity_started",
                    "ReceiptDispatchActivity started from push",
                    intent,
                    receiptId,
                    null
            );
        } catch (Throwable e) {
            Log.e(TAG, "Failed to start ReceiptDispatchActivity, receiptId=" + receiptId + ", messageId=" + messageId, e);

            DebugProbe.event(
                    context,
                    "push_receiver_error",
                    "Failed to start ReceiptDispatchActivity",
                    intent,
                    receiptId,
                    e
            );
        }
    }

    private Intent toDebugIntent(@NonNull Bundle data, long messageId) {
        Intent intent = new Intent("ru.berserkclub.evotorereceipt.action.DEBUG_PUSH");
        intent.putExtra("messageId", messageId);

        for (String key : data.keySet()) {
            Object value = data.get(key);
            putExtraSafely(intent, key, value);
        }

        return intent;
    }

    private void putExtraSafely(Intent intent, String key, @Nullable Object value) {
        if (value == null) {
            return;
        }

        if (value instanceof String) {
            intent.putExtra(key, (String) value);
        } else if (value instanceof Integer) {
            intent.putExtra(key, (Integer) value);
        } else if (value instanceof Long) {
            intent.putExtra(key, (Long) value);
        } else if (value instanceof Boolean) {
            intent.putExtra(key, (Boolean) value);
        } else if (value instanceof Double) {
            intent.putExtra(key, (Double) value);
        } else if (value instanceof Float) {
            intent.putExtra(key, (Float) value);
        } else if (value instanceof Bundle) {
            intent.putExtra(key, (Bundle) value);
        } else {
            intent.putExtra(key, String.valueOf(value));
        }
    }

    @Nullable
    private String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
