package com.example.dam.bootstrap;

import com.example.dam.asset.application.AssetService;
import com.example.dam.asset.application.AssetService.RegisterAssetCommand;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.common.config.YamlResources;
import com.example.dam.common.domain.Actor;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Registers the sample assets through the regular application services (validation, audit and indexing apply).
 * Idempotent: assets that already exist are skipped.
 */
@Component
@Order(10)
@ConditionalOnBooleanProperty(name = "dam.sample-data.enabled")
class SampleDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataLoader.class);
    private static final Pattern RELATIVE_DATE = Pattern.compile("today([+-]\\d+)d");
    private static final Actor LOADER = Actor.system("sample-data-loader");

    private final AssetService assetService;
    private final ResourceLoader resourceLoader;
    private final SampleDataProperties properties;
    private final Clock clock;

    SampleDataLoader(AssetService assetService, ResourceLoader resourceLoader, SampleDataProperties properties,
                     Clock clock) {
        this.assetService = assetService;
        this.resourceLoader = resourceLoader;
        this.properties = properties;
        this.clock = clock;
    }

    @ConfigurationProperties("dam.sample-data")
    record SampleDataProperties(@DefaultValue("false") boolean enabled,
                                @DefaultValue("classpath:sample-data/sample-assets.yaml") String location) {
    }

    @Override
    public void run(ApplicationArguments args) {
        int registered = 0;
        for (Map<String, Object> entry : YamlResources.loadList(resourceLoader.getResource(properties.location()), "assets")) {
            String externalId = String.valueOf(entry.get("externalAssetId"));
            if (assetService.findByExternalAssetId(externalId).isPresent()) {
                continue;
            }
            try {
                assetService.register(new RegisterAssetCommand(externalId, null, null, null,
                        AssetType.valueOf(String.valueOf(entry.get("type"))), resolveDates(metadata(entry)),
                        String.valueOf(entry.getOrDefault("source", "MANUAL"))), LOADER);
                registered++;
            } catch (RuntimeException e) {
                log.warn("Sample asset {} could not be registered: {}", externalId, e.getMessage());
            }
        }
        if (registered > 0) {
            log.info("Registered {} sample assets", registered);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(Map<String, Object> entry) {
        Object metadata = entry.get("metadata");
        return metadata == null ? Map.of() : (Map<String, Object>) metadata;
    }

    private Map<String, Object> resolveDates(Map<String, Object> metadata) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        metadata.forEach((key, value) -> resolved.put(key, resolveDate(value)));
        return resolved;
    }

    private Object resolveDate(Object value) {
        if (value instanceof String text) {
            Matcher matcher = RELATIVE_DATE.matcher(text.strip());
            if (matcher.matches()) {
                return LocalDate.now(clock).plusDays(Long.parseLong(matcher.group(1))).toString();
            }
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::resolveDate).toList();
        }
        return value;
    }
}
