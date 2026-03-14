package com.aykhedma.repository;

import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.service.RiskLevel;
import com.aykhedma.model.service.PriceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ServiceCategoryRepositoryTest {

    @Autowired
    private ServiceCategoryRepository categoryRepository;

    private ServiceCategory category;

    @BeforeEach
    void setUp() {
        category = ServiceCategory.builder()
                .name("Home Services")
                .nameAr("خدمات منزلية")
                .description("Various home services")
                .build();

        ServiceType type = ServiceType.builder()
                .name("Plumbing")
                .nameAr("سباكة")
                .riskLevel(RiskLevel.LOW)
                .basePrice(100.0)
                .defaultPriceType(PriceType.HOUR)
                .estimatedDuration(60)
                .category(category)
                .build();

        category.getServiceTypes().add(type);

        categoryRepository.save(category);
    }

    @Test
    @DisplayName("Should save and find by ID")
    void testSaveAndFindById() {
        Optional<ServiceCategory> found = categoryRepository.findById(category.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo(category.getName());
        assertThat(found.get().getServiceTypes()).hasSize(1);
    }

    @Test
    @DisplayName("Should find by name")
    void testFindByName() {
        Optional<ServiceCategory> found = categoryRepository.findByName("Home Services");
        assertThat(found).isPresent();
        assertThat(found.get().getNameAr()).isEqualTo("خدمات منزلية");
    }

    @Test
    @DisplayName("Should fetch all categories with service types")
    void testFindAllWithServiceTypes() {
        List<ServiceCategory> categories = categoryRepository.findAllWithServiceTypes();
        assertThat(categories).isNotEmpty();
        assertThat(categories.get(0).getServiceTypes()).hasSize(1);
    }

    @Test
    @DisplayName("Should count categories")
    void testCountCategories() {
        long count = categoryRepository.countCategories();
        assertThat(count).isGreaterThan(0);
    }
}