package com.aykhedma.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class GoogleMapsService
{
    @Value("${google.maps.api.key}")
    private String apiKey;
    private final RestTemplate restTemplate;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DistanceAndTime
    {
        private double distance;
        private int estimatedArrivalTime;
    }

    public DistanceAndTime getDistanceAndTime (double originLat, double originLong, double destLat, double destLong)
    {
        DistanceAndTime distanceAndTime = new DistanceAndTime();

        String url = "https://maps.googleapis.com/maps/api/distancematrix/json?origin="
                + originLat + "," + originLong
                + "&destination=" + destLat + "," + destLong
                + "&key=" + apiKey;

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        try
        {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody());
            JsonNode element = root.path("rows").get(0).path("elements").get(0);
            distanceAndTime.distance = element.path("distance").path("value").asDouble() / 1000;
            distanceAndTime.estimatedArrivalTime = element.path("duration").path("value").asInt() / 60;

            return distanceAndTime;
        }
        catch (JsonProcessingException e)
        {
            throw new RuntimeException(e);
        }
    }
}
