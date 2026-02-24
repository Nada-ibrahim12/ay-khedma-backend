package com.aykhedma.service;

import com.aykhedma.dto.service.ServiceTypeDTO;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.service.RiskLevel;
import com.aykhedma.model.service.PriceType;
import com.aykhedma.repository.ServiceCategoryRepository;
import com.aykhedma.repository.ServiceTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ServiceManagementServiceImplTest {

    @Mock
    private ServiceTypeRepository typeRepository;

    @Mock
    private ServiceCategoryRepository categoryRepository;

    @InjectMocks
    private ServiceManagementServiceImpl service;

    private ServiceCategory category;
    private ServiceType type;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        category = ServiceCategory.builder()
                .id(1L)
                .name("Home Services")
                .build();

        type = ServiceType.builder()
                .id(1L)
                .name("Plumbing")
                .category(category)
                .riskLevel(RiskLevel.LOW)
                .defaultPriceType(PriceType.HOUR)
                .basePrice(100.0)
                .estimatedDuration(60)
                .build();
    }

    @Test
    @DisplayName("getAllTypes should return all service types")
    void testGetAllTypes() {
        when(typeRepository.findAll()).thenReturn(List.of(type));

        List<ServiceTypeDTO> dtos = service.getAllTypes();

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).getName()).isEqualTo("Plumbing");
        verify(typeRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("getTypeById should return type if found")
    void testGetTypeById() {
        when(typeRepository.findById(1L)).thenReturn(Optional.of(type));

        ServiceTypeDTO dto = service.getTypeById(1L);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getCategoryName()).isEqualTo("Home Services");
        verify(typeRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("createType should save and return type")
    void testCreateType() {
        ServiceTypeDTO dto = ServiceTypeDTO.builder()
                .name("Electrician")
                .categoryId(1L)
                .riskLevel(RiskLevel.HIGH)
                .defaultPriceType(PriceType.HOUR)
                .basePrice(200.0)
                .estimatedDuration(120)
                .build();

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(typeRepository.save(any(ServiceType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceTypeDTO created = service.createType(dto);

        assertThat(created.getName()).isEqualTo("Electrician");
        assertThat(created.getCategoryId()).isEqualTo(1L);
        verify(typeRepository, times(1)).save(any(ServiceType.class));
    }

    @Test
    @DisplayName("updateType should modify existing type")
    void testUpdateType() {
        ServiceTypeDTO dto = ServiceTypeDTO.builder()
                .name("Updated Plumbing")
                .categoryId(1L)
                .riskLevel(RiskLevel.HIGH)
                .defaultPriceType(PriceType.VISIT)
                .basePrice(150.0)
                .estimatedDuration(90)
                .build();

        when(typeRepository.findById(1L)).thenReturn(Optional.of(type));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(typeRepository.save(any(ServiceType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceTypeDTO updated = service.updateType(1L, dto);

        assertThat(updated.getName()).isEqualTo("Updated Plumbing");
        assertThat(updated.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
        verify(typeRepository, times(1)).save(any(ServiceType.class));
    }

    @Test
    @DisplayName("deleteType should call repository deleteById")
    void testDeleteType() {
        service.deleteType(1L);
        verify(typeRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("countTypes should return repository count")
    void testCountTypes() {
        when(typeRepository.countServices()).thenReturn(5L);
        long count = service.countTypes();
        assertThat(count).isEqualTo(5L);
    }
}