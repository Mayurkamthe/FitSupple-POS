package com.fitsupplepos.model.enums;

public enum ProductCategory {
    WHEY_PROTEIN("Whey Protein"),
    MASS_GAINER("Mass Gainer"),
    CREATINE("Creatine"),
    PRE_WORKOUT("Pre Workout"),
    BCAA("BCAA"),
    GLUTAMINE("Glutamine"),
    VITAMINS("Vitamins"),
    FISH_OIL("Fish Oil"),
    FAT_BURNER("Fat Burner"),
    PROTEIN_BARS("Protein Bars"),
    SHAKERS("Shakers"),
    ACCESSORIES("Accessories"),
    OTHER("Other");

    private final String displayName;

    ProductCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
