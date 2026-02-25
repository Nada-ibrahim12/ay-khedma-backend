package com.aykhedma.controller;

import com.aykhedma.dto.response.SearchResponse;
import com.aykhedma.dto.service.ServiceCategoryDTO;
import com.aykhedma.dto.service.ServiceTypeDTO;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.service.ServiceCategoryService;
import com.aykhedma.service.ServiceManagementService;
import com.aykhedma.service.ServiceManagementServiceImpl;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PostMapping("/categories")
    public ServiceCategoryDTO createCategory(@RequestBody ServiceCategoryDTO dto) {
        return categoryService.createCategory(dto);
    }

    @PutMapping("/categories/{id}")
    public ServiceCategoryDTO updateCategory(@PathVariable Long id, @RequestBody ServiceCategoryDTO dto) {
        return categoryService.updateCategory(id, dto);
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
    public ServiceTypeDTO updateType(@PathVariable Long id, @RequestBody ServiceTypeDTO dto) {
        return typeService.updateType(id, dto);
    }

    @DeleteMapping("/types/{id}")
    public void deleteType(@PathVariable Long id) {

        typeService.deleteType(id);
    }

    @GetMapping("/types/count")
    public long countTypes() {

        return typeService.countTypes();
    }
    @GetMapping("/search")
    @Operation(summary = "Search providers with filters and location-based sorting")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search completed successfully"),
            @ApiResponse(responseCode = "404", description = "Consumer not found")
    })
    public ResponseEntity<Page<SearchResponse>> search(
            @Parameter(description = "Search keyword (searches in name, bio, service type)")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "Filter by category ID")
            @RequestParam(required = false) Long categoryId,

            @Parameter(description = "Filter by category name")
            @RequestParam(required = false) String categoryName,

            @Parameter(description = "Consumer ID for location-based search")
            @RequestParam(required = true) Long consumerId,

            @Parameter(description = "Search radius in kilometers (requires consumerId)")
            @RequestParam(required = false, defaultValue = "5.0") Double radius,

            @Parameter(description = "Sort by field (distance, rating, price, experience)")
            @RequestParam(required = false, defaultValue = "rating") String sortBy,

            @PageableDefault(size = 20, sort = "averageRating", direction = Sort.Direction.DESC)
            Pageable pageable) {


        List<SearchResponse> list = typeService.searchList(
                keyword, categoryId, categoryName, consumerId, radius, sortBy);

        Page<SearchResponse> page = new PageImpl<>(list, pageable, list.size());

        return ResponseEntity.ok(page);
    }
}