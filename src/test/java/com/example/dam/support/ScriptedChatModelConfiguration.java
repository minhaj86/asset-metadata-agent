package com.example.dam.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class ScriptedChatModelConfiguration {

    @Bean
    @Primary
    ScriptedChatModel scriptedChatModel() {
        return new ScriptedChatModel();
    }
}
