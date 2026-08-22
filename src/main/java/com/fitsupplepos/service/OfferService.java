package com.fitsupplepos.service;

import com.fitsupplepos.dao.OfferDao;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Customer;
import com.fitsupplepos.model.Offer;
import com.fitsupplepos.model.Product;
import com.fitsupplepos.model.enums.OfferScope;
import com.fitsupplepos.model.enums.OfferType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class OfferService {

    private final OfferDao offerDao = new OfferDao();

    public Offer create(Offer offer) {
        validate(offer);
        Offer saved = offerDao.save(offer);
        com.fitsupplepos.util.AuditLogger.log("OFFER_CREATED", "Offer", String.valueOf(saved.getId()),
                saved.getName() + " (" + saved.getOfferType() + ")");
        return saved;
    }

    public Offer update(Offer offer) {
        validate(offer);
        Offer saved = offerDao.update(offer);
        com.fitsupplepos.util.AuditLogger.log("OFFER_UPDATED", "Offer", String.valueOf(saved.getId()), saved.getName());
        return saved;
    }

    public void deactivate(Long id) {
        offerDao.findById(id).ifPresent(o -> {
            o.setActive(false);
            offerDao.update(o);
            com.fitsupplepos.util.AuditLogger.log("OFFER_DEACTIVATED", "Offer", String.valueOf(id), o.getName());
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

    /**
     * Picks the best automatically-applied discount offer for one cart line (a given
     * product, at a given pre-discount line value). Considers PERCENTAGE_DISCOUNT,
     * FIXED_DISCOUNT (scoped to this product / its category / all products) and, when a
     * customer is supplied, CUSTOMER_SPECIFIC offers for that customer. Coupon offers and
     * BUY_X_GET_Y offers are handled separately (see {@link #findCouponOffer} and
     * {@link #findBuyXGetYOffersFor}) since they don't reduce a single line's price directly.
     *
     * Returns empty if no eligible offer applies. When more than one offer is eligible,
     * the one yielding the larger discount for this line wins — a customer never loses
     * out because two offers happened to overlap.
     */
    public Optional<Offer> findBestAutoDiscount(List<Offer> validOffers, Product product, Customer customer, BigDecimal lineGrossValue) {
        if (product == null || lineGrossValue == null || lineGrossValue.signum() <= 0) return Optional.empty();
        return validOffers.stream()
                .filter(o -> o.getOfferType() == OfferType.PERCENTAGE_DISCOUNT
                        || o.getOfferType() == OfferType.FIXED_DISCOUNT
                        || o.getOfferType() == OfferType.CUSTOMER_SPECIFIC)
                .filter(o -> appliesToProduct(o, product, customer))
                .max(Comparator.comparing(o -> discountAmountFor(o, lineGrossValue)));
    }

    /** Discount amount (₹) a given offer would knock off a line worth {@code lineGrossValue}. */
    public BigDecimal discountAmountFor(Offer offer, BigDecimal lineGrossValue) {
        BigDecimal amount = BigDecimal.ZERO;
        if (offer.getDiscountPercent() != null && offer.getDiscountPercent().signum() > 0) {
            amount = lineGrossValue.multiply(offer.getDiscountPercent())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        if (offer.getDiscountFixed() != null && offer.getDiscountFixed().signum() > 0
                && offer.getDiscountFixed().compareTo(amount) > 0) {
            amount = offer.getDiscountFixed();
        }
        // A discount can never exceed the value of the line itself.
        return amount.min(lineGrossValue);
    }

    private boolean appliesToProduct(Offer offer, Product product, Customer customer) {
        if (offer.getOfferType() == OfferType.CUSTOMER_SPECIFIC) {
            return offer.getCustomer() != null && customer != null && offer.getCustomer().getId().equals(customer.getId());
        }
        if (offer.getScope() == null) return false;
        return switch (offer.getScope()) {
            case ALL -> true;
            case PRODUCT -> offer.getProduct() != null && offer.getProduct().getId().equals(product.getId());
            case CATEGORY -> offer.getCategory() != null && offer.getCategory().equals(product.getCategory());
        };
    }

    /** Finds a currently-valid, active coupon offer matching the given code (case-insensitive). */
    public Optional<Offer> findCouponOffer(List<Offer> validOffers, String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        String trimmed = code.trim();
        return validOffers.stream()
                .filter(o -> o.getOfferType() == OfferType.COUPON)
                .filter(o -> trimmed.equalsIgnoreCase(o.getCouponCode()))
                .findFirst();
    }

    /** All currently-valid Buy-X-Get-Y offers whose "buy" product is the given product. */
    public List<Offer> findBuyXGetYOffersFor(List<Offer> validOffers, Product buyProduct) {
        return validOffers.stream()
                .filter(o -> o.getOfferType() == OfferType.BUY_X_GET_Y)
                .filter(o -> o.getBuyProduct() != null && buyProduct != null && o.getBuyProduct().getId().equals(buyProduct.getId()))
                .toList();
    }
}
