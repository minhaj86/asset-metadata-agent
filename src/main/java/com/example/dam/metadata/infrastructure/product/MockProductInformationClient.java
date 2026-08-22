package com.example.dam.metadata.infrastructure.product;

import com.example.dam.common.config.IntegrationProperties;
import com.example.dam.common.config.YamlResources;
import com.example.dam.metadata.application.port.ProductInformationClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** Catalog-backed stand-in for the product information system. */
@Component
class MockProductInformationClient implements ProductInformationClient {

    private final Map<String, ProductInformation> products;

    MockProductInformationClient(ResourceLoader resourceLoader, IntegrationProperties properties) {
        products = YamlResources.loadList(resourceLoader.getResource(properties.productCatalog()), "products")
                .stream()
                .map(MockProductInformationClient::toProduct)
                .collect(Collectors.toUnmodifiableMap(ProductInformation::productId, product -> product));
    }

    @Override
    public Optional<ProductInformation> findByProductId(String productId) {
        return Optional.ofNullable(productId).map(String::strip).map(products::get);
    }

    @SuppressWarnings("unchecked")
    private static ProductInformation toProduct(Map<String, Object> entry) {
        return new ProductInformation(
                String.valueOf(entry.get("productId")),
                (String) entry.get("articleCode"),
                (String) entry.get("name"),
                (String) entry.get("brand"),
                (String) entry.get("department"),
                (String) entry.get("productType"),
                (String) entry.get("color"),
                (List<String>) entry.getOrDefault("materials", List.of()),
                (String) entry.get("season"),
                (String) entry.get("description"));
    }
}
