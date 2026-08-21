package com.fitsupplepos.dao;

import com.fitsupplepos.model.Offer;

import java.time.LocalDate;
import java.util.List;

public class OfferDao extends GenericDao<Offer, Long> {

    public OfferDao() { super(Offer.class); }

    public List<Offer> findAllOrderedByStartDateDesc() {
        return query(session -> session.createQuery("from Offer order by startDate desc", Offer.class).list());
    }

    public List<Offer> findCurrentlyValid() {
        return query(session -> {
            LocalDate today = LocalDate.now();
            return session.createQuery(
                    "from Offer where active = true and startDate <= :today and endDate >= :today", Offer.class)
                    .setParameter("today", today)
                    .list();
        });
    }
}
