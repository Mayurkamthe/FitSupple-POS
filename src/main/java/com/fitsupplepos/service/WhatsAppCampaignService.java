package com.fitsupplepos.service;

import com.fitsupplepos.config.SessionManager;
import com.fitsupplepos.dao.CustomerDao;
import com.fitsupplepos.dao.WhatsAppCampaignDao;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Customer;
import com.fitsupplepos.model.WhatsAppCampaign;
import com.fitsupplepos.model.WhatsAppCampaignRecipient;
import com.fitsupplepos.model.WhatsAppTemplate;
import com.fitsupplepos.model.enums.CampaignAudience;
import com.fitsupplepos.model.enums.CustomerSegment;
import com.fitsupplepos.model.enums.ProductCategory;
import com.fitsupplepos.model.enums.WhatsAppMessagePurpose;
import com.fitsupplepos.model.enums.WhatsAppMessageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * WhatsApp marketing campaigns. Resolves an audience (segment- or purchase-history-based)
 * down to individual opted-in customers, persists one WhatsAppCampaignRecipient per
 * customer, then sends and tracks per-recipient status — never sending to a customer
 * whose whatsappOptIn flag is off, regardless of which audience matched them.
 */
public class WhatsAppCampaignService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppCampaignService.class);

    private final WhatsAppCampaignDao campaignDao = new WhatsAppCampaignDao();
    private final CustomerDao customerDao = new CustomerDao();
    private final CustomerService customerService = new CustomerService();
    private final WhatsAppService whatsAppService = new WhatsAppService();

    public List<Customer> resolveAudience(CampaignAudience audience) {
        List<Customer> allCustomers = customerDao.findAllOrderedByName();

        List<Customer> matched = switch (audience) {
            case ALL_CUSTOMERS -> allCustomers;
            case VIP_CUSTOMERS -> filterBySegment(allCustomers, CustomerSegment.VIP);
            case NEW_CUSTOMERS -> filterBySegment(allCustomers, CustomerSegment.NEW);
            case INACTIVE_30_DAYS -> filterBySegment(allCustomers, CustomerSegment.INACTIVE_30);
            case INACTIVE_60_DAYS -> filterBySegment(allCustomers, CustomerSegment.INACTIVE_60);
            case HIGH_VALUE_CUSTOMERS -> filterBySegment(allCustomers, CustomerSegment.HIGH_VALUE);
            case WHEY_CUSTOMERS -> customerDao.findByPurchasedCategory(ProductCategory.WHEY_PROTEIN);
            case CREATINE_CUSTOMERS -> customerDao.findByPurchasedCategory(ProductCategory.CREATINE);
        };

        // Only opted-in customers ever end up in a campaign, regardless of audience.
        return matched.stream().filter(Customer::isWhatsappOptIn).toList();
    }

    private List<Customer> filterBySegment(List<Customer> customers, CustomerSegment segment) {
        return customers.stream().filter(c -> customerService.computeSegment(c) == segment).toList();
    }

    public WhatsAppCampaign createCampaign(String name, CampaignAudience audience, WhatsAppTemplate template, String messageBody) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("Campaign name is required.");
        }
        if (template == null && (messageBody == null || messageBody.isBlank())) {
            throw new BusinessException("Provide either a template or a free-text message body.");
        }

        List<Customer> recipients = resolveAudience(audience);

        return SessionManager.withTransaction(session -> {
            WhatsAppCampaign campaign = new WhatsAppCampaign();
            campaign.setName(name.trim());
            campaign.setAudience(audience);
            campaign.setTemplate(template);
            campaign.setMessageBody(messageBody);
            campaign.setStatus("DRAFT");
            session.persist(campaign);

            for (Customer customer : recipients) {
                WhatsAppCampaignRecipient recipient = new WhatsAppCampaignRecipient();
                recipient.setCampaign(campaign);
                recipient.setCustomer(customer);
                recipient.setStatus(WhatsAppMessageStatus.QUEUED);
                campaign.getRecipients().add(recipient);
                session.persist(recipient);
            }
            return campaign;
        });
    }

    /** Sends the campaign to every queued recipient, updating each recipient's status as it goes. */
    public WhatsAppCampaign sendCampaign(Long campaignId) {
        WhatsAppCampaign campaign = campaignDao.findById(campaignId)
                .orElseThrow(() -> new BusinessException("Campaign not found."));

        if (!whatsAppService.isConfigured()) {
            throw new BusinessException("WhatsApp is not configured. Set the access token and phone number ID in Settings.");
        }

        int sent = 0;
        int failed = 0;
        for (WhatsAppCampaignRecipient recipient : new ArrayList<>(campaign.getRecipients())) {
            if (recipient.getStatus() != WhatsAppMessageStatus.QUEUED) continue;
            try {
                if (campaign.getTemplate() != null) {
                    whatsAppService.sendTemplate(recipient.getCustomer(), campaign.getTemplate(),
                            List.of(), WhatsAppMessagePurpose.CAMPAIGN);
                } else {
                    whatsAppService.sendText(recipient.getCustomer(), campaign.getMessageBody(), WhatsAppMessagePurpose.CAMPAIGN);
                }
                updateRecipientStatus(recipient.getId(), WhatsAppMessageStatus.SENT, null);
                sent++;
            } catch (Exception e) {
                log.warn("Campaign {} failed to send to customer {}: {}", campaign.getName(),
                        recipient.getCustomer().getName(), e.getMessage());
                updateRecipientStatus(recipient.getId(), WhatsAppMessageStatus.FAILED, e.getMessage());
                failed++;
            }
        }

        String finalStatus = failed == 0 ? "COMPLETED" : (sent == 0 ? "FAILED" : "COMPLETED");
        int sentCount = sent;
        int failedCount = failed;
        return SessionManager.withTransaction(session -> {
            WhatsAppCampaign c = session.get(WhatsAppCampaign.class, campaignId);
            c.setStatus(finalStatus);
            c.setSentAt(java.time.LocalDateTime.now());
            session.merge(c);
            log.info("Campaign {} finished — {} sent, {} failed", c.getName(), sentCount, failedCount);
            return c;
        });
    }

    private void updateRecipientStatus(Long recipientId, WhatsAppMessageStatus status, String error) {
        SessionManager.withTransactionVoid(session -> {
            WhatsAppCampaignRecipient r = session.get(WhatsAppCampaignRecipient.class, recipientId);
            if (r != null) {
                r.setStatus(status);
                r.setErrorMessage(error);
                session.merge(r);
            }
        });
    }

    public List<WhatsAppCampaign> listCampaigns() {
        return campaignDao.findAllOrderedDesc();
    }
}
