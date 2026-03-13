package com.aykhedma.repository;

import com.aykhedma.model.location.Location;
import com.aykhedma.util.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Location Repository Tests")
class LocationRepositoryTest {

    @Autowired
    private LocationRepository locationRepository;

    @Test
    @DisplayName("Should save and find location by ID")
    void saveAndFindById() {
        Location location = TestDataFactory.createLocation(null);

        Location saved = locationRepository.save(location);

        Optional<Location> found = locationRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getLatitude()).isEqualTo(location.getLatitude());
        assertThat(found.get().getLongitude()).isEqualTo(location.getLongitude());
    }
}
