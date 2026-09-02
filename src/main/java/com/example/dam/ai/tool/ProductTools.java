package com.example.dam.ai.tool;

import com.example.dam.ai.config.ConditionalOnAiEnabled;
import com.example.dam.metadata.application.port.ProductInformationClient;
import com.example.dam.metadata.application.port.ProductInformationClient.ProductInformation;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/** Read-only access to the authoritative product information system. */
@Component
@ConditionalOnAiEnabled
public class ProductTools {

    private final ProductInformationClient productClient;

    public ProductTools(ProductInformationClient productClient) {
        this.productClient = productClient;
    }

    @Tool("Returns authoritative product information (name, department, colour, materials) for a product id, "
            + "or null if the product is unknown. Never guess product ids.")
    public ProductInformation getProductInformation(@P("Product id, e.g. 12345") String productId) {
        return productClient.findByProductId(productId).orElse(null);
    }
}
