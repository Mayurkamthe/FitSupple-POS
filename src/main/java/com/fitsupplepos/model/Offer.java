package com.fitsupplepos.model;

import com.fitsupplepos.model.enums.OfferScope;
import com.fitsupplepos.model.enums.OfferType;
import com.fitsupplepos.model.enums.ProductCategory;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "offer")
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "offer_type", nullable = false, length = 25)
    private OfferType offerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private OfferScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ProductCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "discount_percent", precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "discount_fixed", precision = 12, scale = 2)
    private BigDecimal discountFixed;

    @Column(name = "coupon_code", length = 30)
    private String couponCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buy_product_id")
    private Product buyProduct;

    @Column(name = "buy_quantity")
    private Integer buyQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "get_product_id")
    private Product getProduct;

    @Column(name = "get_quantity")
    private Integer getQuantity;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public OfferType getOfferType() { return offerType; }
    public void setOfferType(OfferType offerType) { this.offerType = offerType; }
    public OfferScope getScope() { return scope; }
    public void setScope(OfferScope scope) { this.scope = scope; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public ProductCategory getCategory() { return category; }
    public void setCategory(ProductCategory category) { this.category = category; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }
    public BigDecimal getDiscountFixed() { return discountFixed; }
    public void setDiscountFixed(BigDecimal discountFixed) { this.discountFixed = discountFixed; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public Product getBuyProduct() { return buyProduct; }
    public void setBuyProduct(Product buyProduct) { this.buyProduct = buyProduct; }
    public Integer getBuyQuantity() { return buyQuantity; }
    public void setBuyQuantity(Integer buyQuantity) { this.buyQuantity = buyQuantity; }
    public Product getGetProduct() { return getProduct; }
    public void setGetProduct(Product getProduct) { this.getProduct = getProduct; }
    public Integer getGetQuantity() { return getQuantity; }
    public void setGetQuantity(Integer getQuantity) { this.getQuantity = getQuantity; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isCurrentlyValid() {
        LocalDate today = LocalDate.now();
        return active && !today.isBefore(startDate) && !today.isAfter(endDate);
    }
}
