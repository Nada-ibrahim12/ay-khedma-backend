package com.aykhedma.model.service;

public enum PriceType {
    HOUR("بالساعة"),
    SESSION("بالجلسة"),
    VISIT("بالزيارة");

    private final String arabicLabel;

    PriceType(String arabicLabel) {
        this.arabicLabel = arabicLabel;
    }

    public String getArabicLabel() {
        return arabicLabel;
    }
}