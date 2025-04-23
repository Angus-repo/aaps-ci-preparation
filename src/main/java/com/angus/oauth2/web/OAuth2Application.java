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
                    if (supported.getLanguage().equalsIgnoreCase(browserLocale.getLanguage()) &&
                        supported.getCountry().equalsIgnoreCase(browserLocale.getCountry())) {
                        return supported;
                    }
                }
                for (Locale supported : getSupportedLocales()) {
                    if (supported.getLanguage().equalsIgnoreCase(browserLocale.getLanguage())) {
                        return supported;
                    }
                }
                return getDefaultLocale();
            }
        };
        resolver.setSupportedLocales(Arrays.asList(
            Locale.forLanguageTag("ar"),
            Locale.forLanguageTag("bg"),
            Locale.forLanguageTag("ca"),
            Locale.forLanguageTag("cs"),
            Locale.forLanguageTag("da"),
            Locale.forLanguageTag("de"),
            Locale.forLanguageTag("el"),
            Locale.forLanguageTag("en"),
            Locale.forLanguageTag("es"),
            Locale.forLanguageTag("fr"),
            Locale.forLanguageTag("he"),
            Locale.forLanguageTag("hr"),
            Locale.forLanguageTag("hu"),
            Locale.forLanguageTag("it"),
            Locale.forLanguageTag("ko"),
            Locale.forLanguageTag("lt"),
            Locale.forLanguageTag("nb"),
            Locale.forLanguageTag("nl"),
            Locale.forLanguageTag("pl"),
            Locale.forLanguageTag("pt-BR"),
            Locale.forLanguageTag("pt-PT"),
            Locale.forLanguageTag("ro"),
            Locale.forLanguageTag("ru"),
            Locale.forLanguageTag("sk"),
            Locale.forLanguageTag("sr-Latn"),
            Locale.forLanguageTag("sv"),
            Locale.forLanguageTag("tr"),
            Locale.forLanguageTag("uk"),
            Locale.forLanguageTag("zh-CN"),
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