package com.example.dam.metadata.application.port;

import java.util.List;
import java.util.Optional;

/** Port to the product information system (PIM) - the authoritative source for product attributes. */
public interface ProductInformationClient {

    Optional<ProductInformation> findByProductId(String productId);

    record ProductInformation(
            String productId,
            String articleCode,
            String name,
            String brand,
            String department,
            String productType,
            String color,
            List<String> materials,
            String season,
            String description) {

        public ProductInformation {
            materials = materials == null ? List.of() : List.copyOf(materials);
        }
    }
}
