package com.angus.aaps.ci.keystore;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Exchanges a local public client's PKCE code without a client secret or token storage. */
@RestController
public class OneDriveOAuthController {
    static final String TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token";
    static final String SCOPE = "offline_access https://graph.microsoft.com/Files.ReadWrite.AppFolder";
    private static final Pattern CLIENT_ID = Pattern.compile("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");
    private static final Pattern VERIFIER = Pattern.compile("[A-Za-z0-9._~-]{43,128}");
    private final RestTemplate restTemplate;

    @Autowired
    public OneDriveOAuthController(RestTemplateBuilder builder) {
        this(builder.setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(20)).build());
    }

    OneDriveOAuthController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @CrossOrigin(originPatterns = {"http://localhost:[*]", "http://127.0.0.1:[*]"},
            methods = RequestMethod.POST, maxAge = 600)
    @PostMapping(value = "/api/onedrive/token", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> exchange(@RequestBody TokenRequest request) {
        if (request.clientId() == null || !CLIENT_ID.matcher(request.clientId()).matches()
                || request.codeVerifier() == null || !VERIFIER.matcher(request.codeVerifier()).matches()
                || request.code() == null || request.code().isBlank() || request.code().length() > 8192
                || !isLoopbackRedirect(request.redirectUri())) {
            return response(HttpStatus.BAD_REQUEST, Map.of("error", "invalid_request"));
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", request.clientId());
        form.add("code", request.code());
        form.add("code_verifier", request.codeVerifier());
        form.add("redirect_uri", request.redirectUri());
        form.add("grant_type", "authorization_code");
        form.add("scope", SCOPE);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            JsonNode token = restTemplate.postForObject(TOKEN_URL, new HttpEntity<>(form, headers), JsonNode.class);
            if (token == null || !token.path("refresh_token").isTextual()
                    || token.path("refresh_token").asText().isBlank()) {
                return response(HttpStatus.BAD_GATEWAY, Map.of("error", "missing_refresh_token"));
            }
            // Do not return access/id tokens, log the body, or retain any credentials.
            return response(HttpStatus.OK, Map.of("refresh_token", token.path("refresh_token").asText()));
        } catch (RestClientResponseException error) {
            // Only expose the provider's error identifier, never its full response or request data.
            String errorCode = "token_exchange_failed";
            try {
                JsonNode body = new ObjectMapper().readTree(error.getResponseBodyAsString());
                String candidate = body.path("error").asText();
                if (candidate.matches("[a-z_]{1,80}")) {
                    errorCode = candidate;
                }
                JsonNode codes = body.path("error_codes");
                if (codes.isArray() && !codes.isEmpty() && codes.get(0).canConvertToInt()) {
                    errorCode += " (AADSTS" + codes.get(0).asInt() + ")";
                }
            } catch (Exception ignored) {
                // Malformed upstream bodies must not disclose credentials in a response or log.
            }
            return response(HttpStatus.BAD_GATEWAY, Map.of("error", errorCode));
        } catch (RestClientException error) {
            return response(HttpStatus.BAD_GATEWAY, Map.of("error", "token_service_unavailable"));
        }
    }

    private static boolean isLoopbackRedirect(String value) {
        if (value == null || value.length() > 2048) return false;
        try {
            URI uri = URI.create(value);
            return "http".equals(uri.getScheme())
                    && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))
                    && uri.getUserInfo() == null && uri.getFragment() == null && uri.getQuery() == null
                    && uri.getPort() >= -1 && uri.getPort() <= 65535;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static ResponseEntity<Map<String, String>> response(HttpStatus status, Map<String, String> body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache").body(body);
    }

    public record TokenRequest(String clientId, String code, String codeVerifier, String redirectUri) {}
}
