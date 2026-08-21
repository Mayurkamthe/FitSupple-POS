package com.fitsupplepos.service;

import com.fitsupplepos.dao.OfferDao;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Offer;
import com.fitsupplepos.model.enums.OfferScope;
import com.fitsupplepos.model.enums.OfferType;

import java.util.List;

public class OfferService {

    private final OfferDao offerDao = new OfferDao();

    public Offer create(Offer offer) {
        validate(offer);
        return offerDao.save(offer);
    }

    public Offer update(Offer offer) {
        validate(offer);
        return offerDao.update(offer);
    }

    public void deactivate(Long id) {
        offerDao.findById(id).ifPresent(o -> {
            o.setActive(false);
            offerDao.update(o);
        });
    }

    private void validate(Offer offer) {
        if (offer.getName() == null || offer.getName().isBlank()) {
            throw new BusinessException("Offer name is required.");
        }
        if (offer.getOfferType() == null) {
            throw new BusinessException("Offer type is required.");
        }
        if (offer.getStartDate() == null || offer.getEndDate() == null) {
            throw new BusinessException("Start and end dates are required.");
        }
        if (offer.getEndDate().isBefore(offer.getStartDate())) {
            throw new BusinessException("End date cannot be before the start date.");
        }

        switch (offer.getOfferType()) {
            case PERCENTAGE_DISCOUNT -> {
                if (offer.getDiscountPercent() == null || offer.getDiscountPercent().signum() <= 0) {
                    throw new BusinessException("Discount percent is required for a percentage discount offer.");
                }
                requireScopeTarget(offer);
            }
            case FIXED_DISCOUNT -> {
                if (offer.getDiscountFixed() == null || offer.getDiscountFixed().signum() <= 0) {
                    throw new BusinessException("Fixed discount amount is required.");
                }
                requireScopeTarget(offer);
            }
            case COUPON -> {
                if (offer.getCouponCode() == null || offer.getCouponCode().isBlank()) {
                    throw new BusinessException("Coupon code is required.");
                }
                if (offer.getDiscountPercent() == null && offer.getDiscountFixed() == null) {
                    throw new BusinessException("A coupon offer needs either a percentage or fixed discount value.");
                }
            }
            case BUY_X_GET_Y -> {
                if (offer.getBuyProduct() == null || offer.getBuyQuantity() == null || offer.getBuyQuantity() <= 0) {
                    throw new BusinessException("Buy-X product and quantity are required.");
                }
                if (offer.getGetProduct() == null || offer.getGetQuantity() == null || offer.getGetQuantity() <= 0) {
                    throw new BusinessException("Get-Y product and quantity are required.");
                }
            }
            case CUSTOMER_SPECIFIC -> {
                if (offer.getCustomer() == null) {
                    throw new BusinessException("A customer must be selected for a customer-specific offer.");
                }
                if (offer.getDiscountPercent() == null && offer.getDiscountFixed() == null) {
                    throw new BusinessException("A customer-specific offer needs either a percentage or fixed discount value.");
                }
            }
        }
    }

    private void requireScopeTarget(Offer offer) {
        if (offer.getScope() == null) {
            throw new BusinessException("Select a scope (Product / Category / All Products).");
        }
        if (offer.getScope() == OfferScope.PRODUCT && offer.getProduct() == null) {
            throw new BusinessException("Select a product for a product-scoped offer.");
        }
        if (offer.getScope() == OfferScope.CATEGORY && offer.getCategory() == null) {
            throw new BusinessException("Select a category for a category-scoped offer.");
        }
    }

    public List<Offer> listAll() {
        return offerDao.findAllOrderedByStartDateDesc();
    }

    public List<Offer> listCurrentlyValid() {
        return offerDao.findCurrentlyValid();
    }
}
