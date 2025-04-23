package com.angus.oauth2.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import java.util.Locale;
import java.util.Arrays;

@SpringBootApplication(scanBasePackages = "com.angus.oauth2")
public class OAuth2Application {
    public static void main(String[] args) {
        
        SpringApplication.run(OAuth2Application.class, args);
    }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver() {
            @Override
            public Locale resolveLocale(javax.servlet.http.HttpServletRequest request) {
                String langParam = request.getParameter("lang");
                if (langParam != null && !langParam.isEmpty()) {
                    Locale locale = Locale.forLanguageTag(langParam.replace('_', '-'));
                    if (getSupportedLocales().contains(locale)) {
                        return locale;
                    }
                }
                Locale browserLocale = request.getLocale();
                for (Locale supported : getSupportedLocales()) {
                    if (supported.getLanguage().equals(browserLocale.getLanguage())) {
                        return supported;
                    }
                }
                return getDefaultLocale();
            }
        };
        resolver.setSupportedLocales(Arrays.asList(
            Locale.forLanguageTag("en"),
            Locale.forLanguageTag("cs"),
            Locale.forLanguageTag("zh-TW")
        ));
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }

    @Bean
    public ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages", "i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }
}