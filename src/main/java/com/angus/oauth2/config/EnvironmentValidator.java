package com.angus.oauth2.config;

import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentValidator {
    
    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        validateEnvironmentVariables();
    }
    
    private void validateEnvironmentVariables() {
        String clientId = System.getenv("OAUTH_CLIENT_ID");
        String clientSecret = System.getenv("OAUTH_CLIENT_SECRET");
        String redirectUri = System.getenv("OAUTH_REDIRECT_URI");
        
        StringBuilder missingVars = new StringBuilder();
        
        if (isEmpty(clientId)) {
            missingVars.append("OAUTH_CLIENT_ID, ");
        }
        if (isEmpty(clientSecret)) {
            missingVars.append("OAUTH_CLIENT_SECRET, ");
        }
        if (isEmpty(redirectUri)) {
            missingVars.append("OAUTH_REDIRECT_URI, ");
        }
        
        if (missingVars.length() > 0) {
            missingVars.setLength(missingVars.length() - 2);
            throw new IllegalStateException(
                "必要的環境變數未設定: " + missingVars.toString() + 
                "\n請確認已在環境變數中設定這些值，或在 .env 檔案中提供這些設定。"
            );
        }
    }
    
    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}