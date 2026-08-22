package com.fitsupplepos.dao;

import com.fitsupplepos.model.WhatsAppCampaign;

import java.util.List;

public class WhatsAppCampaignDao extends GenericDao<WhatsAppCampaign, Long> {

    public WhatsAppCampaignDao() { super(WhatsAppCampaign.class); }

    public List<WhatsAppCampaign> findAllOrderedDesc() {
        return query(session -> session.createQuery(
                "from WhatsAppCampaign order by createdAt desc", WhatsAppCampaign.class).list());
    }
}
