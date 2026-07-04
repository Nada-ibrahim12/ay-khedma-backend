package com.aykhedma.controller;

import com.aykhedma.dto.response.SearchResponse;
import com.aykhedma.dto.service.ServiceCategoryDTO;
import com.aykhedma.dto.service.ServiceTypeDTO;
import com.aykhedma.config.TestSecurityConfig;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.GlobalExceptionHandler;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.security.JwtAuthenticationFilter;
import com.aykhedma.security.JwtService;
import com.aykhedma.service.ProviderService;
import com.aykhedma.service.ServiceCategoryService;
import com.aykhedma.service.ServiceManagementServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@DisplayName("Service Catalog Controller Unit Tests")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class ServiceCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProviderService providerService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ServiceCategoryService categoryService;

    @MockBean
    private ServiceManagementServiceImpl typeService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private SearchResponse provider() {
        return SearchResponse.builder()
                .id(1L)
                .name("Ibrahim Nasser")
                .serviceType("Drain Cleaning")
                .categoryName("Plumbing")
                .averageRating(4.6)
                .price(200.0)
                .distance(0.39)
                .estimatedArrivalTime(15)
                .withinServiceArea(true)
                .completedJobs(12)
                .build();
    }

    private ServiceCategoryDTO category1;
    private ServiceCategoryDTO category2;
    private ServiceTypeDTO type1;
    private ServiceTypeDTO type2;

    private final Long CATEGORY_ID = 1L;
    private final Long TYPE_ID = 2L;

    @BeforeEach
    void setUp() {
        category1 = new ServiceCategoryDTO();
        category1.setId(1L);
        category1.setName("Home Maintenance");

        category2 = new ServiceCategoryDTO();
        category2.setId(2L);
        category2.setName("Electrical");

        type1 = new ServiceTypeDTO();
        type1.setId(1L);
        type1.setName("Furniture Assembly");

        type2 = new ServiceTypeDTO();
        type2.setId(2L);
        type2.setName("Electrical Wiring");
    }


    @Test
    @DisplayName("GET /api/services/categories - Should return all categories")
    void getCategories_ShouldReturnList() throws Exception {
        when(categoryService.getAllCategories()).thenReturn(List.of(category1, category2));

        mockMvc.perform(get("/api/services/categories"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[1].name").value("Electrical"));
    }

    @Test
    @DisplayName("GET /api/services/categories/{id} - Should return category by id")
    void getCategory_ShouldReturnCategory() throws Exception {
        when(categoryService.getCategoryById(CATEGORY_ID)).thenReturn(category1);

        mockMvc.perform(get("/api/services/categories/{id}", CATEGORY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CATEGORY_ID))
                .andExpect(jsonPath("$.name").value("Home Maintenance"));
    }

    @Test
    @DisplayName("GET /api/services/categories/{id} - Should return 404 if not found")
    void getCategory_NotFound_Returns404() throws Exception {
        when(categoryService.getCategoryById(CATEGORY_ID))
                .thenThrow(new ResourceNotFoundException("Category not found with id: " + CATEGORY_ID));

        mockMvc.perform(get("/api/services/categories/{id}", CATEGORY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Category not found with id: " + CATEGORY_ID));
    }

    @Test
    @DisplayName("POST /api/services/categories - Should create category")
    void createCategory_ShouldReturnCreatedCategory() throws Exception {
        when(categoryService.createCategory(any(ServiceCategoryDTO.class), any()))
                .thenReturn(category1);

        MockMultipartFile categoryPart = new MockMultipartFile(
                "category",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(category1));

        MockMultipartFile imagePart = new MockMultipartFile(
                "image",
                "photo.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "fake-image-bytes".getBytes());

        mockMvc.perform(multipart("/api/services/categories")
                        .file(categoryPart)
                        .file(imagePart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CATEGORY_ID))
                .andExpect(jsonPath("$.name").value("Home Maintenance"));
    }

    @Test
    @DisplayName("PUT /api/services/categories/{id} - Should update category")
    void updateCategory_ShouldReturnUpdatedCategory() throws Exception {
        when(categoryService.updateCategory(eq(CATEGORY_ID), any(ServiceCategoryDTO.class), any()))
                .thenReturn(category1);

        MockMultipartFile categoryPart = new MockMultipartFile(
                "category",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(category1));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/services/categories/{id}", CATEGORY_ID)
                        .file(categoryPart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CATEGORY_ID));
    }

    @Test
    @DisplayName("DELETE /api/services/categories/{id} - Should delete category")
    void deleteCategory_ShouldReturnOk() throws Exception {
        doNothing().when(categoryService).deleteCategory(CATEGORY_ID);

        mockMvc.perform(delete("/api/services/categories/{id}", CATEGORY_ID))
                .andExpect(status().isOk());
    }
    @Test
    @DisplayName("DELETE /categories/{id} - Should fail if category has providers")
    void deleteCategory_ShouldFail_WhenProvidersExist() throws Exception {

        doThrow(new BadRequestException("Cannot delete category because it is used by providers"))
                .when(categoryService).deleteCategory(CATEGORY_ID);

        mockMvc.perform(delete("/api/services/categories/{id}", CATEGORY_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Cannot delete category because it is used by providers"));
    }

    @Test
    @DisplayName("GET /api/services/categories/count - Should return category count")
    void countCategories_ShouldReturnCount() throws Exception {
        when(categoryService.countCategories()).thenReturn(2L);

        mockMvc.perform(get("/api/services/categories/count"))
                .andExpect(status().isOk())
                .andExpect(content().string("2"));
    }


    @Test
    @DisplayName("GET /api/services/types - Should return all types")
    void getTypes_ShouldReturnList() throws Exception {
        when(typeService.getAllTypes()).thenReturn(List.of(type1, type2));

        mockMvc.perform(get("/api/services/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Furniture Assembly"));
    }

    @Test
    @DisplayName("GET /api/services/types/{id} - Should return type by id")
    void getType_ShouldReturnType() throws Exception {
        when(typeService.getTypeById(TYPE_ID)).thenReturn(type2);

        mockMvc.perform(get("/api/services/types/{id}", TYPE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TYPE_ID))
                .andExpect(jsonPath("$.name").value("Electrical Wiring"));
    }

    @Test
    @DisplayName("POST /api/services/types - Should create type")
    void createType_ShouldReturnCreatedType() throws Exception {
        when(typeService.createType(any(ServiceTypeDTO.class))).thenReturn(type1);

        mockMvc.perform(post("/api/services/types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(type1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @DisplayName("PUT /api/services/types/{id} - Should update type")
    void updateType_ShouldReturnUpdatedType() throws Exception {
        when(typeService.updateType(eq(TYPE_ID), any(ServiceTypeDTO.class))).thenReturn(type2);

        mockMvc.perform(put("/api/services/types/{id}", TYPE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(type2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TYPE_ID));
    }

    @Test
    @DisplayName("DELETE /api/services/types/{id} - Should delete type")
    void deleteType_ShouldReturnOk() throws Exception {
        doNothing().when(typeService).deleteType(TYPE_ID);

        mockMvc.perform(delete("/api/services/types/{id}", TYPE_ID))
                .andExpect(status().isOk());
    }
    @Test
    @DisplayName("DELETE /types/{id} - Should fail if service type has providers")
    void deleteType_ShouldFail_WhenProvidersExist() throws Exception {

        doThrow(new BadRequestException("Cannot delete service type because it has providers"))
                .when(typeService).deleteType(TYPE_ID);

        mockMvc.perform(delete("/api/services/types/{id}", TYPE_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Cannot delete service type because it has providers"));
    }


    @Test
    @DisplayName("GET /api/services/types/count - Should return type count")
    void countTypes_ShouldReturnCount() throws Exception {
        when(typeService.countTypes()).thenReturn(2L);

        mockMvc.perform(get("/api/services/types/count"))
                .andExpect(status().isOk())
                .andExpect(content().string("2"));
    }
}