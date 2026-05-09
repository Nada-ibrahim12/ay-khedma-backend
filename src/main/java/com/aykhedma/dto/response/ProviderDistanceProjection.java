package com.aykhedma.dto.response;

public interface ProviderDistanceProjection {
    Long getId();
    String getName();
    String getProfileImage();
    String getServiceType();
    String getServiceTypeAr();
    String getCategoryName();
    Double getAverageRating();
    Double getPrice();
    String getPriceType();
    Double getServiceAreaRadius();
    Double getAveragePunctualityRating();
    Double getAverageCommitmentRating();
    Double getAverageQualityOfWorkRating();
    String getArea();
    Double getDistanceMeters();
    Double getDistanceKm();
    Integer getEstimatedArrivalTime();
}