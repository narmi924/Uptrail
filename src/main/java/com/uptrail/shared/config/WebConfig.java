package com.uptrail.shared.config;

import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

@Configuration(proxyBeanMethods = false)
public class WebConfig {

    /** The interface is English only; dates and numbers must not follow the browser language. */
    @Bean
    LocaleResolver localeResolver() {
        return new FixedLocaleResolver(Locale.ENGLISH);
    }
}
