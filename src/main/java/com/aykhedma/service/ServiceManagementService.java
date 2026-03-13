package com.aykhedma.service;

import com.aykhedma.dto.response.SearchResponse;
import com.aykhedma.dto.service.CategoryWithServicesDTO;
import com.aykhedma.dto.service.ServiceCategoryDTO;
import com.aykhedma.dto.service.ServiceTypeDTO;
import com.aykhedma.dto.service.ServicesResponse;
import com.aykhedma.model.service.RiskLevel;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ServiceManagementService {

    List<ServiceTypeDTO> getAllTypes();

    ServiceTypeDTO getTypeById(Long id);

    ServiceTypeDTO createType(ServiceTypeDTO dto);

    ServiceTypeDTO updateType(Long id, ServiceTypeDTO dto);

    void deleteType(Long id);

    long countTypes();

    Page<SearchResponse> search(String keyword,
                                Long categoryId,
                                String categoryName,
                                Long consumerId,
                                Double radius,
                                String sortBy,
                                Pageable pageable);
    List<SearchResponse> searchList(String keyword,
                                    Long categoryId,
                                    String categoryName,
                                    Long consumerId,
                                    Double radius,
                                    String sortBy);
}