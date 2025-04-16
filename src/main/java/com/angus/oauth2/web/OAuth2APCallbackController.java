package com.angus.oauth2.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Locale;

@RestController
public class OAuth2APCallbackController {

    @Autowired
    private MessageSource messageSource;

    private static final String REFRESH_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto") != null ? 
            request.getHeader("X-Forwarded-Proto") : request.getScheme();
            
        String serverName = request.getHeader("X-Forwarded-Host") != null ?
            request.getHeader("X-Forwarded-Host") : request.getServerName();
            
        String port = "";
        if (request.getHeader("X-Forwarded-Port") != null) {
            String forwardedPort = request.getHeader("X-Forwarded-Port");
            if (!("80".equals(forwardedPort) && "http".equals(scheme)) && 
                !("443".equals(forwardedPort) && "https".equals(scheme))) {
                port = ":" + forwardedPort;
            }
        } else if (request.getServerPort() != 80 && request.getServerPort() != 443) {
            port = ":" + request.getServerPort();
        }
        
        return scheme + "://" + serverName + port;
    }

    @GetMapping("/oauth2APcallback")
    public RedirectView handleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String lang,
            HttpSession session,
            HttpServletRequest request) {
        
        // 設定語言
        Locale locale;
        if (lang != null) {
            switch (lang.toLowerCase()) {
                case "en":
                    locale = new Locale("en");
                    break;
                case "cs":
                    locale = new Locale("cs");
                    break;
                case "zh-tw":
                    locale = new Locale("zh", "TW");
                    break;
                default:
                    locale = LocaleContextHolder.getLocale();
            }
        } else {
            locale = LocaleContextHolder.getLocale();
        }

        if (error != null) {
            return new RedirectView(getBaseUrl(request) + "/error?message=" + messageSource.getMessage("auth.failed", null, locale));
        }

        if (code == null) {
            return new RedirectView(getBaseUrl(request) + "/error?message=" + messageSource.getMessage("auth.missing.code", null, locale));
        }

        String codeVerifier = (String) session.getAttribute("code_verifier");
        if (codeVerifier == null) {
            return new RedirectView(getBaseUrl(request) + "/error?message=" + messageSource.getMessage("auth.verification.missing", null, locale));
        }

        try {
            // 交換 Access Token
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", OAuth2Constants.CLIENT_ID);
            params.add("client_secret", OAuth2Constants.CLIENT_SECRET);
            params.add("code", code);
            params.add("redirect_uri", OAuth2Constants.REDIRECT_URI);
            params.add("grant_type", "authorization_code");
            params.add("code_verifier", codeVerifier);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, headers);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.postForEntity(
                OAuth2Constants.TOKEN_ENDPOINT,
                requestEntity,
                String.class
            );

            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(response.getBody());

            if (!json.has("access_token")) {
                return new RedirectView(getBaseUrl(request) + "/error?message=" + messageSource.getMessage("auth.token.exchange.failed", null, locale));
            }

            // 儲存 tokens 到 session
            session.setAttribute("access_token", json.get("access_token").asText());
            if (json.has("refresh_token")) {
                session.setAttribute("refresh_token", json.get("refresh_token").asText());
            }

            // 清除 code_verifier
            session.removeAttribute("code_verifier");

            // 重新導向到歡迎頁面
            return new RedirectView(getBaseUrl(request) + ":8443/welcome" + (lang != null ? "?lang=" + lang : ""));

        } catch (Exception e) {
            return new RedirectView(getBaseUrl(request) + "/error?message=" + messageSource.getMessage("error", null, locale));
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<JsonNode> refreshToken(@RequestParam("refresh_token") String refreshToken) {
        try {
            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode errorNode = mapper.createObjectNode();
                errorNode.put("error", "refresh_token cannot be empty");
                return ResponseEntity.badRequest().body(errorNode);
            }

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", OAuth2Constants.CLIENT_ID);
            params.add("client_secret", OAuth2Constants.CLIENT_SECRET);
            params.add("refresh_token", refreshToken);
            params.add("grant_type", "refresh_token");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.postForEntity(
                REFRESH_TOKEN_ENDPOINT,
                request,
                String.class
            );

            ObjectMapper mapper = new ObjectMapper();
            JsonNode responseJson = mapper.readTree(response.getBody());
            
            return ResponseEntity.ok(responseJson);

        } catch (Exception e) {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode errorNode = mapper.createObjectNode();
            errorNode.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorNode);
        }
    }
}