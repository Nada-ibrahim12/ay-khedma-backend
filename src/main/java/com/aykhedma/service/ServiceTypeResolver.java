package com.aykhedma.service;

import com.aykhedma.model.service.ServiceType;
import com.aykhedma.repository.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class ServiceTypeResolver {

    private final ServiceTypeRepository serviceTypeRepository;
    private final ConcurrentHashMap<String, ServiceType> cache = new ConcurrentHashMap<>();

    public ServiceType resolveByMeaning(String serviceTypeName) {
        if (serviceTypeName == null || serviceTypeName.isEmpty()) {
            return null;
        }

        String key = serviceTypeName.toLowerCase().trim();
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        List<ServiceType> allServices = serviceTypeRepository.findAll();

        ServiceType result = allServices.stream()
                .filter(st -> st.getName().equalsIgnoreCase(serviceTypeName) ||
                        st.getNameAr().equalsIgnoreCase(serviceTypeName))
                .findFirst()
                .orElse(null);

        if (result == null) {
            String normalized = serviceTypeName.toLowerCase();
            result = allServices.stream()
                    .filter(st -> st.getName().toLowerCase().contains(normalized) ||
                            st.getNameAr().toLowerCase().contains(normalized))
                    .findFirst()
                    .orElse(null);
        }

        if (result != null) {
            cache.put(key, result);
        }

        return result;
    }
}