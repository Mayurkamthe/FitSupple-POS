package com.fitsupplepos.dao;

import com.fitsupplepos.model.WhatsAppTemplate;

import java.util.List;

public class WhatsAppTemplateDao extends GenericDao<WhatsAppTemplate, Long> {

    public WhatsAppTemplateDao() { super(WhatsAppTemplate.class); }

    public List<WhatsAppTemplate> findAllActive() {
        return query(session -> session.createQuery(
                "from WhatsAppTemplate where active = true order by templateName", WhatsAppTemplate.class).list());
    }
}
