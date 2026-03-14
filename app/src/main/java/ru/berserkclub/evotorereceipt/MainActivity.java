package ru.berserkclub.evotorereceipt;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import ru.evotor.framework.core.IntegrationAppCompatActivity;
import ru.evotor.framework.core.IntegrationException;
import ru.evotor.framework.core.IntegrationManagerFuture;
import ru.evotor.framework.core.action.command.print_receipt_command.PrintReceiptCommandErrorData;
import ru.evotor.framework.core.action.command.print_receipt_command.PrintReceiptCommandErrorDataFactory;
import ru.evotor.framework.core.action.command.print_receipt_command.PrintReceiptCommandResult;
import ru.evotor.framework.core.action.command.print_receipt_command.PrintSellReceiptCommand;
import ru.evotor.framework.payment.PaymentSystem;
import ru.evotor.framework.payment.PaymentType;
import ru.evotor.framework.receipt.Measure;
import ru.evotor.framework.receipt.Payment;
import ru.evotor.framework.receipt.Position;
import ru.evotor.framework.receipt.PrintGroup;
import ru.evotor.framework.receipt.Receipt;

public class MainActivity extends IntegrationAppCompatActivity {

    private EditText etItemName;
    private EditText etQuantity;
    private EditText etPrice;
    private EditText etEmail;
    private EditText etPhone;
    private EditText etPaymentPlace;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etItemName = findViewById(R.id.etItemName);
        etQuantity = findViewById(R.id.etQuantity);
        etPrice = findViewById(R.id.etPrice);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etPaymentPlace = findViewById(R.id.etPaymentPlace);
        Button btnSendReceipt = findViewById(R.id.btnSendReceipt);

        etItemName.setText("Услуга клуба");
        etQuantity.setText("1");
        etPrice.setText("1000");
        etPaymentPlace.setText("berserkclub.ru");

        btnSendReceipt.setOnClickListener(v -> sendElectronicReceipt());
    }

    private void sendElectronicReceipt() {
        String itemName = safeTrim(etItemName);
        String quantityStr = safeTrim(etQuantity);
        String priceStr = safeTrim(etPrice);
        String email = safeTrim(etEmail);
        String phone = normalizePhone(safeTrim(etPhone));
        String paymentPlace = safeTrim(etPaymentPlace);

        if (TextUtils.isEmpty(itemName)) {
            toast("Укажите наименование товара");
            return;
        }

        if (TextUtils.isEmpty(quantityStr) || TextUtils.isEmpty(priceStr)) {
            toast("Укажите количество и цену");
            return;
        }

        if (TextUtils.isEmpty(email) && TextUtils.isEmpty(phone)) {
            toast("Укажите email и/или телефон покупателя");
            return;
        }

        BigDecimal quantity;
        BigDecimal price;
        try {
            quantity = new BigDecimal(quantityStr);
            price = new BigDecimal(priceStr);
        } catch (NumberFormatException ex) {
            toast("Количество и цена должны быть числами");
            return;
        }

        BigDecimal total = price.multiply(quantity);

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
        Payment payment = new Payment(
                UUID.randomUUID().toString(),
                total,
                new PaymentSystem(PaymentType.ELECTRON, "Internet", "Cashless"),
                null,
                null,
                null
        );
        payments.put(payment, total);

        /*
         * В разных версиях SDK Эвотор встречаются разные конструкторы PrintGroup.
         * Этот вариант ближе к демо-примеру Evotor API example.
         * Если в вашей версии SDK есть расширенный конструктор с receiptFromInternet,
         * используйте его и передайте true.
         */
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

        /*
         * Основной вызов по официальной схеме из документации:
         * PrintSellReceiptCommand(listDocs, null, phone, email, null, null, paymentPlace)
         *
         * В некоторых демо-проектах SDK есть расширенный overload с additional params.
         * Если ваш SDK просит другой конструктор, оставьте ту же бизнес-логику и
         * подставьте paymentPlace в нужный аргумент соответствующего overload.
         */
        new PrintSellReceiptCommand(
                listDocs,
                null,
                emptyToNull(phone),
                emptyToNull(email),
                null,
                null,
                emptyToNull(paymentPlace)
        ).process(MainActivity.this, future -> handleResult(future));
    }

    private void handleResult(IntegrationManagerFuture future) {
        try {
            IntegrationManagerFuture.Result result = future.getResult();
            switch (result.getType()) {
                case OK:
                    PrintReceiptCommandResult ignored = PrintReceiptCommandResult.create(result.getData());
                    toast("Чек успешно отправлен");
                    break;
                case ERROR:
                    StringBuilder errorText = new StringBuilder();
                    if (result.getError() != null) {
                        errorText.append(result.getError().getMessage());
                    }

                    PrintReceiptCommandErrorData errorData =
                            PrintReceiptCommandErrorDataFactory.create(result.getData());

                    if (errorData instanceof PrintReceiptCommandErrorData.KktError) {
                        PrintReceiptCommandErrorData.KktError kktError =
                                (PrintReceiptCommandErrorData.KktError) errorData;
                        errorText.append(" | KKT code: ")
                                .append(kktError.getKktErrorCode())
                                .append(" | ")
                                .append(kktError.getKktErrorDescription());
                    }

                    toast(errorText.toString());
                    break;
            }
        } catch (IntegrationException e) {
            toast("Ошибка интеграции: " + e.getMessage());
        }
    }

    private String safeTrim(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private String normalizePhone(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private String emptyToNull(String value) {
        return TextUtils.isEmpty(value) ? null : value;
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}
