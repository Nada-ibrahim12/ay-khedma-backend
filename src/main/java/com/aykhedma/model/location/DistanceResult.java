package com.aykhedma.model.location;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistanceResult {
    private double distance; // in km
    private int duration; // in minutes
}