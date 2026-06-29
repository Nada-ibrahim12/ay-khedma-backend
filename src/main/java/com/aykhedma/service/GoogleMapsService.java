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

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LocationDetails
    {
        private String country;
        private String city;
        private String area;
        private String address;
        private String countryAr;
        private String cityAr;
        private String areaAr;
        private String addressAr;
    }

    public DistanceAndTime getDistanceAndTime (double originLat, double originLong, double destLat, double destLong)
    {
        DistanceAndTime distanceAndTime = new DistanceAndTime();

        String url = "https://maps.googleapis.com/maps/api/distancematrix/json?origins="
                + originLat + "," + originLong
                + "&destinations=" + destLat + "," + destLong
                + "&key=" + apiKey;

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        try
        {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody());

            JsonNode rowsNode = root.path("rows");
            if (!rowsNode.isArray() || rowsNode.isEmpty())
                throw new RuntimeException("Invalid Google Maps response: rows missing or empty");

            JsonNode elementsNode = rowsNode.get(0).path("elements");
            if (!elementsNode.isArray() || elementsNode.isEmpty())
                throw new RuntimeException("Invalid Google Maps response: elements missing or empty");

            JsonNode element = elementsNode.get(0);
            distanceAndTime.distance = element.path("distance").path("value").asDouble() / 1000;
            distanceAndTime.estimatedArrivalTime= element.path("duration").path("value").asInt() / 60;

            return distanceAndTime;
        }
        catch (JsonProcessingException e)
        {
            throw new RuntimeException("Failed to parse Google Maps response", e);
        }
    }

    public LocationDetails getLocationDetails (double lat, double lng)
    {
        LocationDetails locationDetails = new LocationDetails();

        String urlEn = "https://maps.googleapis.com/maps/api/geocode/json?latlng="
                + lat + "," + lng + "&key=" + apiKey + "&language=en";
        String urlAr = "https://maps.googleapis.com/maps/api/geocode/json?latlng="
                    + lat + "," + lng + "&key=" + apiKey + "&language=ar";

        ResponseEntity<String> responseEn = restTemplate.getForEntity(urlEn, String.class);
        ResponseEntity<String> responseAr = restTemplate.getForEntity(urlAr, String.class);

        try
        {
            ObjectMapper mapper = new ObjectMapper();

            JsonNode rootEn = mapper.readTree(responseEn.getBody());
            JsonNode firstResultEn = rootEn.path("results").get(0);

            if (firstResultEn != null && !firstResultEn.isMissingNode())
            {
                JsonNode components = firstResultEn.path("address_components");
                for (JsonNode comp : components)
                {
                    JsonNode types = comp.path("types");
                    if (types.toString().contains("country"))
                    {
                        locationDetails.setCountry(comp.path("long_name").asText());
                    }
                    else if (types.toString().contains("administrative_area_level_1"))
                    {
                        locationDetails.setCity(comp.path("long_name").asText());
                    }
                    else if (types.toString().contains("administrative_area_level_2"))
                    {
                        locationDetails.setArea(comp.path("long_name").asText());
                    }
                }
                locationDetails.setAddress(firstResultEn.path("formatted_address").asText(null));
            }


            JsonNode rootAr = mapper.readTree(responseAr.getBody());
            JsonNode firstResultAr = rootAr.path("results").get(0);

            if (firstResultAr != null && !firstResultAr.isMissingNode())
            {
                JsonNode componentsAr = firstResultAr.path("address_components");
                for (JsonNode comp : componentsAr)
                {
                    JsonNode types = comp.path("types");
                    if (types.toString().contains("country"))
                    {
                        locationDetails.setCountryAr(comp.path("long_name").asText());
                    }
                    else if (types.toString().contains("administrative_area_level_1"))
                    {
                        locationDetails.setCityAr(comp.path("long_name").asText());
                    }
                    else if (types.toString().contains("administrative_area_level_2"))
                    {
                        locationDetails.setAreaAr(comp.path("long_name").asText());
                    }
                }
                locationDetails.setAddressAr(firstResultAr.path("formatted_address").asText(null));
            }

            return locationDetails;
        }
        catch (JsonProcessingException e)
        {
            throw new RuntimeException("Failed to parse Google Maps response", e);
        }
    }
}
