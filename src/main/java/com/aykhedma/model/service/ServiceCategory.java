package com.aykhedma.model.service;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "service_categories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Category name is required")
    @Size(min = 2, max = 50, message = "Category name must be between 2 and 50 characters")
    @Column(unique = true, nullable = false, length = 50)
    private String name;

    @Size(max = 50, message = "Arabic name cannot exceed 50 characters")
    @Column(name = "name_ar", length = 50)
    private String nameAr;

    @Size(max = 200, message = "Description cannot exceed 200 characters")
    @Column(length = 200)
    private String description;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ServiceType> serviceTypes = new ArrayList<>();
}