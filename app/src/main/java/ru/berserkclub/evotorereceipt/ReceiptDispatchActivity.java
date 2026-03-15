package ru.berserkclub.evotorereceipt;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import ru.evotor.framework.component.PaymentPerformer;
import ru.evotor.framework.core.IntegrationAppCompatActivity;
import ru.evotor.framework.core.IntegrationException;
import ru.evotor.framework.core.IntegrationManagerCallback;
import ru.evotor.framework.core.IntegrationManagerFuture;
import ru.evotor.framework.core.action.command.print_receipt_command.PrintReceiptCommandResult;
import ru.evotor.framework.core.action.command.print_receipt_command.PrintSellReceiptCommand;
import ru.evotor.framework.payment.PaymentSystem;
import ru.evotor.framework.payment.PaymentType;
import ru.evotor.framework.receipt.Measure;
import ru.evotor.framework.receipt.Payment;
import ru.evotor.framework.receipt.Position;
import ru.evotor.framework.receipt.PrintGroup;
import ru.evotor.framework.receipt.Receipt;
import ru.evotor.framework.users.User;
import ru.evotor.framework.users.UserApi;

public class ReceiptDispatchActivity extends IntegrationAppCompatActivity {

    public static final String ACTION_SEND_RECEIPT =
            "ru.berserkclub.evotorereceipt.action.SEND_RECEIPT";

    public static final String EXTRA_RECEIPT_ID = "receiptId";
    public static final String EXTRA_ITEM_NAME = "itemName";
    public static final String EXTRA_PRICE = "price";
    public static final String EXTRA_QUANTITY = "quantity";
    public static final String EXTRA_EMAIL = "email";
    public static final String EXTRA_PHONE = "phone";
    public static final String EXTRA_PAYMENT_PLACE = "paymentPlace";

    private static final String TAG = "ru.berserkclub.evotorereceipt";
    private static final int MAX_ITEM_NAME_LENGTH = 128;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^\\d{10,15}$");

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            handleIntent(getIntent());
        } catch (Exception e) {
            Log.e(TAG, "Failed to handle receipt intent", e);
            finish();
        }
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            Log.e(TAG, "Intent is null");
            finish();
            return;
        }

        if (!ACTION_SEND_RECEIPT.equals(intent.getAction())) {
            Log.e(TAG, "Unsupported action: " + intent.getAction());
            finish();
            return;
        }

        String receiptId = getRequiredString(intent, EXTRA_RECEIPT_ID);
        String itemName = getRequiredString(intent, EXTRA_ITEM_NAME);
        String priceStr = getRequiredString(intent, EXTRA_PRICE);
        String quantityStr = getRequiredString(intent, EXTRA_QUANTITY);

        String rawEmail = trimToNull(intent.getStringExtra(EXTRA_EMAIL));
        String rawPhone = normalizePhone(intent.getStringExtra(EXTRA_PHONE));
        String paymentPlace = trimToNull(intent.getStringExtra(EXTRA_PAYMENT_PLACE));

        // Приоритет: телефон. Если телефон есть, email игнорируем.
        String phone = rawPhone;
        String email = (phone == null) ? rawEmail : null;

        if (!isValidReceiptId(receiptId)) {
            Log.e(TAG, "Invalid receiptId: " + receiptId);
            finish();
            return;
        }

        if (!isValidItemName(itemName)) {
            Log.e(TAG, "Invalid itemName for receiptId=" + receiptId);
            finish();
            return;
        }

        if (phone == null && email == null) {
            Log.i(TAG, "Receipt skipped: no phone/email, receiptId=" + receiptId);
            finish();
            return;
        }

        if (email != null && !isValidEmail(email)) {
            Log.e(TAG, "Invalid email for receiptId=" + receiptId + ": " + email);
            finish();
            return;
        }

        if (phone != null && !isValidPhone(phone)) {
            Log.e(TAG, "Invalid phone for receiptId=" + receiptId + ": " + phone);
            finish();
            return;
        }

        if (paymentPlace == null) {
            Log.e(TAG, "paymentPlace is required, receiptId=" + receiptId);
            finish();
            return;
        }

        BigDecimal price;
        BigDecimal quantity;

        try {
            price = new BigDecimal(priceStr.trim());
            quantity = new BigDecimal(quantityStr.trim());
        } catch (Throwable e) {
            Log.e(TAG, "Invalid numeric values for receiptId=" + receiptId, e);
            finish();
            return;
        }

        if (!isValidMoney(price)) {
            Log.e(TAG, "Invalid price for receiptId=" + receiptId + ": " + price);
            finish();
            return;
        }

        if (!isValidQuantity(quantity)) {
            Log.e(TAG, "Invalid quantity for receiptId=" + receiptId + ": " + quantity);
            finish();
            return;
        }

        BigDecimal amount = price.multiply(quantity);

        List<Position> positions = new ArrayList<>();
        positions.add(
                Position.Builder.newInstance(
                        UUID.randomUUID().toString(),
                        null,
                        itemName,
                        new Measure("шт", 0, 0),
                        price,
                        quantity
                ).build()
        );

        HashMap<Payment, BigDecimal> payments = new HashMap<>();
        payments.put(
                new Payment(
                        UUID.randomUUID().toString(),
                        amount,
                        null,
                        new PaymentPerformer(
                                new PaymentSystem(PaymentType.ELECTRON, "Internet", "12424"),
                                getPackageName(),
                                getClass().getName(),
                                "70e0031d-1ada-479a-a1eb-d0c2673ca206",
                                getString(R.string.app_name)
                        ),
                        null,
                        null,
                        null
                ),
                amount
        );

        PrintGroup printGroup = new PrintGroup(
                UUID.randomUUID().toString(),
                PrintGroup.Type.CASH_RECEIPT,
                null,
                null,
                null,
                null,
                false,
                null,
                null
        );

        Receipt.PrintReceipt printReceipt = new Receipt.PrintReceipt(
                printGroup,
                positions,
                payments,
                new HashMap<Payment, BigDecimal>(),
                new HashMap<String, BigDecimal>()
        );

        ArrayList<Receipt.PrintReceipt> listDocs = new ArrayList<>();
        listDocs.add(printReceipt);

        BigDecimal receiptDiscount = BigDecimal.ZERO;

        User currentUser = UserApi.getAuthenticatedUser(this);
        String userUuid = currentUser != null ? currentUser.getUuid() : null;

        processReceipt(
                receiptId,
                listDocs,
                phone,
                email,
                paymentPlace,
                receiptDiscount,
                userUuid
        );
    }

    private void processReceipt(
            String receiptId,
            ArrayList<Receipt.PrintReceipt> listDocs,
            String phone,
            String email,
            String paymentPlace,
            BigDecimal receiptDiscount,
            String userUuid
    ) {
        try {
            new PrintSellReceiptCommand(
                    listDocs,
                    null,
                    phone,
                    email,
                    receiptDiscount,
                    null,
                    paymentPlace,
                    userUuid,
                    true
            ).process(this, new IntegrationManagerCallback() {
                @Override
                public void run(IntegrationManagerFuture future) {
                    handleResult(receiptId, future);
                }
            });

        } catch (Throwable e) {
            Log.e(TAG, "PrintSellReceiptCommand failed to start, receiptId=" + receiptId, e);
            finish();
        }
    }

    private void handleResult(String receiptId, IntegrationManagerFuture future) {
        try {
            IntegrationManagerFuture.Result result = future.getResult();

            switch (result.getType()) {
                case OK:
                    PrintReceiptCommandResult commandResult =
                            PrintReceiptCommandResult.create(result.getData());
                    Log.i(TAG, "Receipt sent successfully, receiptId=" + receiptId + ", result=" + commandResult);
                    break;

                case ERROR:
                    String message = result.getError() != null
                            ? result.getError().getMessage()
                            : "Unknown error";
                    Log.e(TAG, "Receipt error, receiptId=" + receiptId + ", message=" + message);
                    break;
            }

        } catch (IntegrationException e) {
            Log.e(TAG, "IntegrationException, receiptId=" + receiptId, e);
        } finally {
            finish();
        }
    }

    private String getRequiredString(Intent intent, String key) {
        String value = trimToNull(intent.getStringExtra(key));
        if (value == null) {
            throw new IllegalArgumentException("Missing required extra: " + key);
        }
        return value;
    }

    private boolean isValidReceiptId(String value) {
        return value != null && !value.trim().isEmpty() && value.trim().length() <= 100;
    }

    private boolean isValidItemName(String value) {
        return value != null
                && !value.trim().isEmpty()
                && value.trim().length() <= MAX_ITEM_NAME_LENGTH;
    }

    private boolean isValidEmail(String value) {
        return value != null && EMAIL_PATTERN.matcher(value).matches();
    }

    private boolean isValidPhone(String value) {
        return value != null && PHONE_PATTERN.matcher(value).matches();
    }

    private boolean isValidMoney(BigDecimal value) {
        return value != null
                && value.compareTo(BigDecimal.ZERO) > 0
                && value.scale() <= 2;
    }

    private boolean isValidQuantity(BigDecimal value) {
        return value != null
                && value.compareTo(BigDecimal.ZERO) > 0
                && value.scale() <= 3;
    }

    private String normalizePhone(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.replaceAll("[^0-9]", "");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}