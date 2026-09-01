package com.ice.searchservice.Enum;

public enum SortOption {
    RELEVANCE, PRICE_ASC, PRICE_DESC, NEWEST, BESTSELLER, RATING;

    public static boolean isValid(String raw) {
        try { from(raw); return true; } catch (Exception e) { return false; }
    }
    public static SortOption from(String raw) {
        return SortOption.valueOf(raw.trim().toUpperCase().replace('-', '_'));
    }
}
