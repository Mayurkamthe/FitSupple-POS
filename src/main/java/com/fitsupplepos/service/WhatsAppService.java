package com.fitsupplepos.service;

import com.fitsupplepos.config.SessionManager;
import com.fitsupplepos.dao.WhatsAppMessageDao;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Customer;
import com.fitsupplepos.model.Sale;
import com.fitsupplepos.model.WhatsAppMessage;
import com.fitsupplepos.model.WhatsAppTemplate;
import com.fitsupplepos.model.enums.WhatsAppMessagePurpose;
import com.fitsupplepos.model.enums.WhatsAppMessageStatus;
import com.fitsupplepos.model.enums.WhatsAppMessageType;
import com.fitsupplepos.util.AuditLogger;
import com.fitsupplepos.whatsapp.WhatsAppApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Orchestrates outbound WhatsApp messages: checks the customer has opted in, normalizes
 * their number, calls {@link WhatsAppApiClient}, and logs every attempt (success or
 * failure) to {@link WhatsAppMessage} so the app has a full send history — independent
 * of whether Meta's servers ever received it.
 *
 * Consent: this build tracks a single opt-in flag per customer (Customer.whatsappOptIn)
 * rather than separate marketing/transactional consent. Every send — invoice, order
 * confirmation, or campaign message — is blocked if that flag is off, which is the
 * more conservative reading of "only send marketing messages to customers with
 * appropriate consent."
 */
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);

    private final WhatsAppApiClient apiClient = new WhatsAppApiClient();
    private final WhatsAppMessageDao messageDao = new WhatsAppMessageDao();

    public boolean isConfigured() {
        return com.fitsupplepos.config.AppConfig.whatsAppConfigured();
    }

    /** Converts a stored number into the digits-only, country-code-prefixed format Meta expects. */
    public String normalizeNumber(String rawNumber) {
        if (rawNumber == null) return "";
        String digits = rawNumber.replaceAll("[^0-9]", "");
        if (digits.length() == 10) {
            // Bare 10-digit Indian mobile number — assume +91 unless the shop is configured otherwise.
            return "91" + digits;
        }
        return digits;
    }

    private String resolveNumber(Customer customer) {
        String raw = customer.getWhatsappNumber() != null && !customer.getWhatsappNumber().isBlank()
                ? customer.getWhatsappNumber() : customer.getMobile();
        return normalizeNumber(raw);
    }

    public WhatsAppMessage sendText(Customer customer, String text, WhatsAppMessagePurpose purpose) {
        requireOptIn(customer);
        if (!isConfigured()) {
            throw new BusinessException("WhatsApp is not configured. Set the access token and phone number ID in Settings.");
        }
        String toNumber = resolveNumber(customer);
        WhatsAppApiClient.SendResult result = apiClient.sendTextMessage(toNumber, text);
        return logMessage(customer, null, WhatsAppMessageType.TEXT, purpose, toNumber, text,
                result.success, result.whatsAppMessageId, result.errorMessage);
    }

    public WhatsAppMessage sendTemplate(Customer customer, WhatsAppTemplate template, List<String> params,
                                         WhatsAppMessagePurpose purpose) {
        requireOptIn(customer);
        if (!isConfigured()) {
            throw new BusinessException("WhatsApp is not configured. Set the access token and phone number ID in Settings.");
        }
        String toNumber = resolveNumber(customer);
        WhatsAppApiClient.SendResult result = apiClient.sendTemplateMessage(
                toNumber, template.getTemplateName(), template.getLanguage(), params);
        return logMessage(customer, null, WhatsAppMessageType.TEMPLATE, purpose, toNumber,
                "Template: " + template.getTemplateName(), result.success, result.whatsAppMessageId, result.errorMessage);
    }

    /** Uploads the invoice PDF and sends it as a WhatsApp document message. */
    public WhatsAppMessage sendInvoicePdf(Sale sale, File pdfFile) {
        Customer customer = sale.getCustomer();
        if (customer == null) {
            throw new BusinessException("This sale has no linked customer — WhatsApp invoices need a saved customer with a mobile number.");
        }
        requireOptIn(customer);
        if (!isConfigured()) {
            throw new BusinessException("WhatsApp is not configured. Set the access token and phone number ID in Settings.");
        }
        String toNumber = resolveNumber(customer);
        String content = "Invoice PDF: " + sale.getInvoiceNumber();
        try {
            String mediaId = apiClient.uploadMedia(pdfFile, "application/pdf");
            WhatsAppApiClient.SendResult result = apiClient.sendDocumentMessage(
                    toNumber, mediaId, pdfFile.getName(), "Invoice " + sale.getInvoiceNumber());
            return logMessage(customer, sale, WhatsAppMessageType.DOCUMENT, WhatsAppMessagePurpose.INVOICE,
                    toNumber, content, result.success, result.whatsAppMessageId, result.errorMessage);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("Failed to send invoice PDF via WhatsApp", e);
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return logMessage(customer, sale, WhatsAppMessageType.DOCUMENT, WhatsAppMessagePurpose.INVOICE,
                    toNumber, content, false, null, errorMsg);
        }
    }

    private void requireOptIn(Customer customer) {
        if (customer == null) {
            throw new BusinessException("No customer selected for this WhatsApp message.");
        }
        if (!customer.isWhatsappOptIn()) {
            throw new BusinessException(customer.getName() + " has not opted in to WhatsApp messages.");
        }
        if (customer.getMobile() == null && customer.getWhatsappNumber() == null) {
            throw new BusinessException(customer.getName() + " has no phone number on file.");
        }
    }

    private WhatsAppMessage logMessage(Customer customer, Sale sale, WhatsAppMessageType type,
                                        WhatsAppMessagePurpose purpose, String toNumber, String content,
                                        boolean success, String whatsAppMessageId, String errorMessage) {
        return SessionManager.withTransaction(session -> {
            WhatsAppMessage message = new WhatsAppMessage();
            message.setCustomer(customer);
            message.setSale(sale);
            message.setMessageType(type);
            message.setPurpose(purpose);
            message.setRecipientNumber(toNumber);
            message.setContent(content);
            message.setStatus(success ? WhatsAppMessageStatus.SENT : WhatsAppMessageStatus.FAILED);
            message.setWhatsappMessageId(whatsAppMessageId);
            message.setErrorMessage(errorMessage);
            session.persist(message);
            AuditLogger.log(session, "WHATSAPP_SEND", "WhatsAppMessage",
                    customer != null ? customer.getName() : "unknown",
                    (success ? "Sent" : "Failed: " + errorMessage) + " to " + toNumber);
            return message;
        });
    }

    public List<WhatsAppMessage> recentMessages(int limit) {
        return messageDao.findRecent(limit);
    }
}
