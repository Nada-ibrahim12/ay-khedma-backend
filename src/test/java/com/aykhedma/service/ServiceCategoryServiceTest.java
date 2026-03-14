package com.aykhedma.service;

import com.aykhedma.dto.service.ServiceCategoryDTO;
import com.aykhedma.dto.service.ServiceTypeDTO;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.service.RiskLevel;
import com.aykhedma.model.service.PriceType;
import com.aykhedma.repository.ServiceCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ServiceCategoryServiceTest {

    @Mock
    private ServiceCategoryRepository categoryRepository;

    @InjectMocks
    private ServiceCategoryService service;

    private ServiceCategory category;
    private ServiceType type;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        category = ServiceCategory.builder()
                .id(1L)
                .name("Home Services")
                .nameAr("خدمات منزلية")
                .build();

        type = ServiceType.builder()
                .id(1L)
                .name("Plumbing")
                .nameAr("سباكة")
                .category(category)
                .riskLevel(RiskLevel.LOW)
                .basePrice(100.0)
                .defaultPriceType(PriceType.HOUR)
                .estimatedDuration(60)
                .build();

        category.getServiceTypes().add(type);
    }

    @Test
    @DisplayName("getAllCategories should return all categories with types")
    void testGetAllCategories() {
        when(categoryRepository.findAllWithServiceTypes()).thenReturn(List.of(category));

        List<ServiceCategoryDTO> dtos = service.getAllCategories();

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).getName()).isEqualTo("Home Services");
        assertThat(dtos.get(0).getServiceTypes()).hasSize(1);
        verify(categoryRepository, times(1)).findAllWithServiceTypes();
    }

    @Test
    @DisplayName("getCategoryById should return category if found")
    void testGetCategoryById() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        ServiceCategoryDTO dto = service.getCategoryById(1L);

        assertThat(dto.getName()).isEqualTo("Home Services");
        assertThat(dto.getServiceTypes()).hasSize(1);
        verify(categoryRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("createCategory should save and return new category")
    void testCreateCategory() {
        ServiceTypeDTO typeDto = ServiceTypeDTO.builder()
                .name("Electrician")
                .riskLevel(RiskLevel.HIGH)
                .basePrice(200.0)
                .defaultPriceType(PriceType.HOUR)
                .estimatedDuration(120)
                .build();

        ServiceCategoryDTO dto = ServiceCategoryDTO.builder()
                .name("Technical Services")
                .nameAr("خدمات فنية")
                .serviceTypes(List.of(typeDto))
                .build();

        when(categoryRepository.save(any(ServiceCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceCategoryDTO created = service.createCategory(dto);

        assertThat(created.getName()).isEqualTo("Technical Services");
        assertThat(created.getServiceTypes()).hasSize(1);
        assertThat(created.getServiceTypes().get(0).getName()).isEqualTo("Electrician");
        verify(categoryRepository, times(1)).save(any(ServiceCategory.class));
    }

    @Test
    @DisplayName("updateCategory should modify existing category")
    void testUpdateCategory() {
        ServiceCategoryDTO dto = ServiceCategoryDTO.builder()
                .name("Updated Services")
                .nameAr("خدمات محدثة")
                .build();

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(ServiceCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceCategoryDTO updated = service.updateCategory(1L, dto);

        assertThat(updated.getName()).isEqualTo("Updated Services");
        assertThat(updated.getNameAr()).isEqualTo("خدمات محدثة");
        verify(categoryRepository, times(1)).save(category);
    }

    @Test
    @DisplayName("deleteCategory should call repository deleteById")
    void testDeleteCategory() {
        service.deleteCategory(1L);
        verify(categoryRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("countCategories should return repository count")
    void testCountCategories() {
        when(categoryRepository.countCategories()).thenReturn(5L);
        long count = service.countCategories();
        assertThat(count).isEqualTo(5L);
    }
}