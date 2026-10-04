package com.example.dam.asset.infrastructure.dam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.dam.common.config.IntegrationProperties;
import com.example.dam.common.domain.ResourceNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

class MockDamImageFolderTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, (byte) 0xFF, (byte) 0xD9};

    @TempDir
    Path folder;

    private MockDamClient client(String imageDir) {
        return new MockDamClient(new DefaultResourceLoader(), new IntegrationProperties(
                "classpath:sample-data/mock-dam-catalog.yaml", "classpath:sample-data/mock-products.yaml", true,
                "http://localhost/mock-dam", imageDir));
    }

    @Test
    void addsFolderImagesAsSequentialAssetsNextToTheCatalog() throws Exception {
        Files.write(folder.resolve("b shirt-S.JPG"), JPEG);
        Files.write(folder.resolve("A dress.png"), JPEG);
        Files.writeString(folder.resolve("notes.txt"), "ignored");

        MockDamClient client = client(folder.toString());

        assertThat(client.getAsset("DA-100001")).isPresent();
        assertThat(client.getAsset("DA-300001")).hasValueSatisfying(asset -> {
            assertThat(asset.filename()).isEqualTo("A dress.png");
            assertThat(asset.mimeType()).isEqualTo("image/png");
        });
        assertThat(client.getAsset("DA-300002")).hasValueSatisfying(asset -> {
            assertThat(asset.filename()).isEqualTo("b shirt-S.JPG");
            assertThat(asset.mimeType()).isEqualTo("image/jpeg");
            assertThat(asset.url()).hasToString("http://localhost/mock-dam/assets/DA-300002/b%20shirt-S.JPG");
        });
        assertThat(client.getAsset("DA-300003")).isEmpty();
        assertThat(client.searchAssets("shirt-s", 50)).extracting(asset -> asset.externalAssetId())
                .containsExactly("DA-300002");
    }

    @Test
    void servesOriginalImageBytesForFolderAssets() throws Exception {
        Files.write(folder.resolve("shirt.jpg"), JPEG);
        MockDamRenditionController controller = new MockDamRenditionController(client(folder.toString()));

        ResponseEntity<Resource> image = controller.rendition("DA-300001", "shirt.jpg");
        assertThat(image.getHeaders().getContentType()).hasToString("image/jpeg");
        assertThat(image.getBody().getContentAsByteArray()).isEqualTo(JPEG);

        ResponseEntity<Resource> svg = controller.rendition("DA-100001", "blue-shirt-front.jpg");
        assertThat(svg.getHeaders().getContentType()).hasToString("image/svg+xml");
        assertThat(new String(svg.getBody().getContentAsByteArray())).contains("MOCK DAM PREVIEW");

        assertThatThrownBy(() -> controller.rendition("DA-300001", "other.jpg"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void skipsMissingFolder() {
        MockDamClient client = client(folder.resolve("missing").toString());

        assertThat(client.getAsset("DA-300001")).isEmpty();
        assertThat(client.getAsset("DA-100001")).isPresent();
    }
}
