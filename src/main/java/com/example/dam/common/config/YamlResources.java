package com.example.dam.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Safe YAML loading (no arbitrary type instantiation) for configuration-like catalog files. */
public final class YamlResources {

    private YamlResources() {
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> loadList(Resource resource, String rootKey) {
        try (InputStream in = resource.getInputStream()) {
            Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            Object entries = root == null ? null : root.get(rootKey);
            return entries == null ? List.of() : (List<Map<String, Object>>) entries;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource, e);
        }
    }
}
