package com.aykhedma.service;

import com.aykhedma.dto.service.CategoryWithServicesDTO;
import com.aykhedma.dto.service.ServiceCategoryDTO;
import com.aykhedma.dto.service.ServiceTypeDTO;
import com.aykhedma.dto.service.ServicesResponse;
import com.aykhedma.model.service.RiskLevel;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;

import java.util.List;
import java.util.Optional;

public interface ServiceManagementService {

    List<ServiceTypeDTO> getAllTypes();

    ServiceTypeDTO getTypeById(Long id);

    ServiceTypeDTO createType(ServiceTypeDTO dto);

    ServiceTypeDTO updateType(Long id, ServiceTypeDTO dto);

    void deleteType(Long id);

    // --- Extra Operations ---
    long countTypes();
}