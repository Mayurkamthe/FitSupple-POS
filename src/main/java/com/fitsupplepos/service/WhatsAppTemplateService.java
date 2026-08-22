package com.fitsupplepos.service;

import com.fitsupplepos.dao.WhatsAppTemplateDao;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.WhatsAppTemplate;

import java.util.List;

/**
 * Manages local records of WhatsApp message templates. Templates themselves are created
 * and approved inside Meta Business Manager (that approval workflow requires business
 * verification and isn't exposed through the Cloud API in a way a desktop app should
 * drive) — this service just tracks which approved template names/placeholder counts the
 * owner can pick from when sending, so WhatsAppApiClient references them by the exact
 * approved name and language.
 */
public class WhatsAppTemplateService {

    private final WhatsAppTemplateDao templateDao = new WhatsAppTemplateDao();

    public WhatsAppTemplate create(WhatsAppTemplate template) {
        validate(template);
        return templateDao.save(template);
    }

    public WhatsAppTemplate update(WhatsAppTemplate template) {
        validate(template);
        return templateDao.update(template);
    }

    public void deactivate(Long id) {
        templateDao.findById(id).ifPresent(t -> {
            t.setActive(false);
            templateDao.update(t);
        });
    }

    private void validate(WhatsAppTemplate template) {
        if (template.getTemplateName() == null || template.getTemplateName().isBlank()) {
            throw new BusinessException("Template name is required (must exactly match the name approved in Meta Business Manager).");
        }
        if (template.getLanguage() == null || template.getLanguage().isBlank()) {
            template.setLanguage("en");
        }
    }

    public List<WhatsAppTemplate> listActive() {
        return templateDao.findAllActive();
    }
}
