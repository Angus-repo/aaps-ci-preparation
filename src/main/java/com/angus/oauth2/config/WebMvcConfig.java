package com.angus.oauth2.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    // 已改用自訂 AcceptHeaderLocaleResolver，不能再註冊 LocaleChangeInterceptor
    // @Override
    // public void addInterceptors(InterceptorRegistry registry) {
    //     LocaleChangeInterceptor localeChangeInterceptor = new LocaleChangeInterceptor();
    //     localeChangeInterceptor.setParamName("lang");
    //     registry.addInterceptor(localeChangeInterceptor);
    // }
}
