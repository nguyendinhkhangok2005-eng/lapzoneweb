package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.model.entity.Category;
import com.lapzone.lapzoneweb.model.entity.Product;
import com.lapzone.lapzoneweb.model.entity.ProductDetail;
import com.lapzone.lapzoneweb.model.repository.ProductRepository;
import com.lapzone.lapzoneweb.model.service.AiConsultantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiConsultantServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private AiConsultantService aiConsultantService;

    @Test
    void shouldBuildProductContextForConsultation() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Gaming");

        Product product = new Product();
        product.setId(1L);
        product.setName("Laptop Gaming Acer Nitro 5");
        product.setPrice(21990000d);
        product.setCategory(category);

        ProductDetail detail = new ProductDetail();
        detail.setCpu("Intel Core i5-12450H");
        detail.setRam("16GB");
        detail.setStorage("512GB SSD");
        detail.setGpu("NVIDIA RTX 3050");
        detail.setScreen("15.6 inch FHD 144Hz");
        detail.setMoreInfo("Laptop gaming phù hợp học tập và chơi game.");
        product.setProductDetail(detail);

        when(productRepository.findAll()).thenReturn(List.of(product));

        String context = aiConsultantService.buildProductContext();

        assertTrue(context.contains("Laptop Gaming Acer Nitro 5"));
        assertTrue(context.contains("Intel Core i5-12450H"));
        assertTrue(context.contains("RTX 3050"));
    }
}
