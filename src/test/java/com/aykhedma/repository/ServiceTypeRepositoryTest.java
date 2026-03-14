package com.aykhedma.repository;

import com.aykhedma.model.service.PriceType;
import com.aykhedma.model.service.RiskLevel;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class ServiceTypeRepositoryTest {

    @Autowired
    private ServiceTypeRepository typeRepository;

    @Autowired
    private ServiceCategoryRepository categoryRepository;

    private ServiceCategory category;

    @BeforeEach
    void setUp() {
        category = ServiceCategory.builder()
                .name("Cleaning")
                .nameAr("تنظيف")
                .description("Cleaning services")
                .build();

        categoryRepository.save(category);

        // إنشاء ServiceType
        ServiceType type1 = ServiceType.builder()
                .name("Home Cleaning")
                .nameAr("تنظيف المنازل")
                .description("Basic home cleaning")
                .category(category)
                .riskLevel(RiskLevel.LOW)
                .basePrice(100.0)
                .defaultPriceType(PriceType.VISIT)
                .build();

        ServiceType type2 = ServiceType.builder()
                .name("Office Cleaning")
                .nameAr("تنظيف المكاتب")
                .description("Office cleaning service")
                .category(category)
                .riskLevel(RiskLevel.LOW)
                .basePrice(200.0)
                .defaultPriceType(PriceType.VISIT)
                .build();

        typeRepository.save(type1);
        typeRepository.save(type2);
    }

    @Test
    void testFindByCategoryId() {
        List<ServiceType> types = typeRepository.findByCategoryId(category.getId());
        assertThat(types).hasSize(2);
    }

    @Test
    void testFindByRiskLevel() {
        List<ServiceType> lowRisk = typeRepository.findByRiskLevel(RiskLevel.LOW);
        assertThat(lowRisk).hasSize(2);
        assertThat(lowRisk.get(0).getName()).isEqualTo("Home Cleaning");
    }

    @Test
    void testFindByCategoryAndRiskLevel() {
        List<ServiceType> mediumTypes = typeRepository.findByCategoryAndRiskLevel(category.getId(), RiskLevel.LOW);
        assertThat(mediumTypes).hasSize(2);
        assertThat(mediumTypes.get(0).getName()).isEqualTo("Home Cleaning");
    }

    @Test
    void testCountServices() {
        long count = typeRepository.countServices();
        assertThat(count).isEqualTo(2);
    }
}