package com.lapzone.lapzoneweb;

import com.lapzone.lapzoneweb.model.entity.*;
import com.lapzone.lapzoneweb.model.repository.ProductRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Repository fixtures honor the same cursor/order/page boundary as the production scalar query. */
final class AiCatalogFixture {
    static void stub(ProductRepository repository, List<Product> products) {
        when(repository.findAvailableProductsForAiAfter(anyLong(), any(Pageable.class))).thenAnswer(call -> {
            long after = call.getArgument(0); Pageable page = call.getArgument(1);
            return products.stream().filter(p -> p.getId() > after).sorted(Comparator.comparing(p -> p.getId()))
                .limit(page.getPageSize()).map(AiCatalogFixture::row).toList();
        });
    }
    private static ProductRepository.AiProductRow row(Product p) {
        Map<String, Object> f = new HashMap<>();
        f.put("id", p.getId()); f.put("name", p.getName()); f.put("price", p.getPrice()); f.put("stock", p.getStock());
        f.put("categoryName", p.getCategory() == null ? null : p.getCategory().getName());
        ProductDetail d = p.getProductDetail();
        if (d != null) {
            f.put("detailId", 1L); f.put("cpu", d.getCpu()); f.put("gpu", d.getGpu()); f.put("ram", d.getRam());
            f.put("storage", d.getStorage()); f.put("screen", d.getScreen()); f.put("battery", d.getBattery());
            f.put("audio", d.getAudio()); f.put("ports", d.getPorts()); f.put("wireless", d.getWireless());
            f.put("weight", d.getWeight()); f.put("color", d.getColor()); f.put("conditionStatus", d.getConditionStatus());
            f.put("warranty", d.getWarranty()); f.put("moreInfo", d.getMoreInfo());
        }
        return new SpelAwareProxyProjectionFactory().createProjection(ProductRepository.AiProductRow.class, f);
    }
}
