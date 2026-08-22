package com.fitsupplepos.dao;

import com.fitsupplepos.model.WhatsAppMessage;
import org.hibernate.query.Query;

import java.util.List;

public class WhatsAppMessageDao extends GenericDao<WhatsAppMessage, Long> {

    public WhatsAppMessageDao() { super(WhatsAppMessage.class); }

    public List<WhatsAppMessage> findRecent(int limit) {
        return query(session -> {
            Query<WhatsAppMessage> q = session.createQuery("from WhatsAppMessage order by sentAt desc", WhatsAppMessage.class);
            q.setMaxResults(limit);
            return q.list();
        });
    }
}
