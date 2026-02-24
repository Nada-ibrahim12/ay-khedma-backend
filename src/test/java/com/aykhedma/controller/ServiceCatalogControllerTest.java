package com.aykhedma.controller;

import com.aykhedma.dto.service.ServiceCategoryDTO;
import com.aykhedma.dto.service.ServiceTypeDTO;
import com.aykhedma.config.TestSecurityConfig;
import com.aykhedma.exception.GlobalExceptionHandler;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.service.ServiceCategoryService;
import com.aykhedma.service.ServiceManagementServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@DisplayName("Service Catalog Controller Unit Tests")
@WebMvcTest(controllers = ServiceCatalogController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class ServiceCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ServiceCategoryService categoryService;

    @MockBean
    private ServiceManagementServiceImpl typeService;


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
        when(categoryService.createCategory(any(ServiceCategoryDTO.class))).thenReturn(category1);

        mockMvc.perform(post("/api/services/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(category1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CATEGORY_ID))
                .andExpect(jsonPath("$.name").value("Home Maintenance"));
    }

    @Test
    @DisplayName("PUT /api/services/categories/{id} - Should update category")
    void updateCategory_ShouldReturnUpdatedCategory() throws Exception {
        when(categoryService.updateCategory(eq(CATEGORY_ID), any(ServiceCategoryDTO.class))).thenReturn(category1);

        mockMvc.perform(put("/api/services/categories/{id}", CATEGORY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(category1)))
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
    @DisplayName("GET /api/services/types/count - Should return type count")
    void countTypes_ShouldReturnCount() throws Exception {
        when(typeService.countTypes()).thenReturn(2L);

        mockMvc.perform(get("/api/services/types/count"))
                .andExpect(status().isOk())
                .andExpect(content().string("2"));
    }
}