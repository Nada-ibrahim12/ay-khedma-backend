package com.aykhedma.controller;

import com.aykhedma.dto.response.SearchResponse;
import com.aykhedma.dto.service.ServiceCategoryDTO;
import com.aykhedma.dto.service.ServiceTypeDTO;
import com.aykhedma.model.service.PriceType;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.service.ServiceCategoryService;
import com.aykhedma.service.ServiceManagementService;
import com.aykhedma.service.ServiceManagementServiceImpl;
import com.aykhedma.dto.response.PriceTypeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceCatalogController {

    private final ServiceCategoryService categoryService;
    private final ServiceManagementServiceImpl typeService;

    @GetMapping("/categories")
    public List<ServiceCategoryDTO> getCategories() {

        return categoryService.getAllCategories();
    }

    @GetMapping("/categories/{id}")
    public ServiceCategoryDTO getCategory(@PathVariable Long id) {

        return categoryService.getCategoryById(id);
    }

    @PostMapping(value = "/categories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ServiceCategoryDTO createCategory(
            @RequestPart("category") ServiceCategoryDTO dto,
            @RequestPart("image") MultipartFile image) {

        return categoryService.createCategory(dto, image);
    }

    @PutMapping(value = "/categories/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ServiceCategoryDTO updateCategory(
            @PathVariable Long id,
            @RequestPart("category") ServiceCategoryDTO dto,
            @RequestPart(value = "image", required = false) MultipartFile image) {

        return categoryService.updateCategory(id, dto, image);
    }

    @DeleteMapping("/categories/{id}")
    public void deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
    }

    @GetMapping("/categories/count")
    public long countCategories() {
        return categoryService.countCategories();
    }

    @GetMapping("/types")
    public List<ServiceTypeDTO> getTypes() {
        return typeService.getAllTypes();
    }

    @GetMapping("/types/{id}")
    public ServiceTypeDTO getType(@PathVariable Long id) {

        return typeService.getTypeById(id);
    }

    @PostMapping("/types")
    public ServiceTypeDTO createType(@RequestBody ServiceTypeDTO dto) {

        return typeService.createType(dto);
    }

    @PutMapping("/types/{id}")
    public ResponseEntity<?> updateType(@PathVariable Long id, @RequestBody ServiceTypeDTO dto) {
        try {
            ServiceTypeDTO updated = typeService.updateType(id, dto);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @DeleteMapping("/types/{id}")
    public void deleteType(@PathVariable Long id) {

        typeService.deleteType(id);
    }

    @GetMapping("/types/count")
    public long countTypes() {

        return typeService.countTypes();
    }

    @GetMapping("/price-types")
    public ResponseEntity<List<PriceTypeResponse>> getAllPriceTypes() {
        List<PriceType> priceTypes = typeService.getAllPriceTypes();

        List<PriceTypeResponse> response = priceTypes.stream()
                .map(priceType -> PriceTypeResponse.builder()
                        .name(priceType.name())
                        .label(priceType.getArabicLabel())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
    @DeleteMapping("/categories/{id}/image")
    public ResponseEntity<?> deleteCategoryImage(@PathVariable Long id) {
        categoryService.deleteCategoryImage(id);
        return ResponseEntity.ok(
                java.util.Map.of(
                        "success", true,
                        "message", "Category image deleted successfully"
                )
        );
    }
}