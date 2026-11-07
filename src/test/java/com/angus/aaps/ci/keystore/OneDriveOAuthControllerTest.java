package com.angus.aaps.ci.keystore;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

class OneDriveOAuthControllerTest {
    private static final String CLIENT_ID = "11111111-2222-3333-4444-555555555555";
    private static final String VERIFIER = "a".repeat(43);
    private MockRestServiceServer upstream;
    private MockMvc mvc;
    private AnnotationConfigWebApplicationContext context;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebConfig.class);
        context.refresh();
        upstream = MockRestServiceServer.createServer(context.getBean(RestTemplate.class));
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Configuration
    @EnableWebMvc
    static class WebConfig {
        @Bean
        RestTemplate restTemplate() { return new RestTemplate(); }

        @Bean
        OneDriveOAuthController oneDriveOAuthController(RestTemplate restTemplate) {
            return new OneDriveOAuthController(restTemplate);
        }
    }

    private String request(String redirect, String verifier, String clientId) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of(
                "clientId", clientId, "code", "test-authorization-code",
                "codeVerifier", verifier, "redirectUri", redirect));
    }

    @Test
    void exchangesNativePkceCodeAndReturnsOnlyRefreshTokenWithoutCaching() throws Exception {
        upstream.expect(requestTo(OneDriveOAuthController.TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist("Origin"))
                .andExpect(content().string(containsString("grant_type=authorization_code")))
                .andExpect(content().string(containsString("code_verifier=" + VERIFIER)))
                .andExpect(content().string(containsString("client_id=" + CLIENT_ID)))
                .andExpect(content().string(containsString("Files.ReadWrite.AppFolder")))
                .andExpect(content().string(not(containsString("client_secret"))))
                .andRespond(withSuccess("{\"refresh_token\":\"test-refresh\",\"access_token\":\"not-returned\",\"id_token\":\"not-returned\"}", MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/onedrive/token").header("Origin", "http://localhost:8765")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("http://localhost:8765/aaps-ci-preparation.html", VERIFIER, CLIENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refresh_token").value("test-refresh"))
                .andExpect(jsonPath("$.access_token").doesNotExist())
                .andExpect(jsonPath("$.id_token").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Access-Control-Allow-Origin", "http://localhost:8765"));
        upstream.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/callback", "http://localhost.evil.test/callback",
            "http://user@localhost/callback", "http://127.0.0.1/callback?lang=en",
            "http://localhost/callback#fragment", "file:///callback", "not a URL"})
    void rejectsNonLoopbackOrAmbiguousCallbacks(String redirect) throws Exception {
        mvc.perform(post("/api/onedrive/token").contentType(MediaType.APPLICATION_JSON)
                .content(request(redirect, VERIFIER, CLIENT_ID))).andExpect(status().isBadRequest());
        upstream.verify();
    }

    @Test
    void rejectsMissingPkceOrInvalidClientIdWithoutContactingMicrosoft() throws Exception {
        mvc.perform(post("/api/onedrive/token").contentType(MediaType.APPLICATION_JSON)
                .content(request("http://localhost/callback", "too-short", CLIENT_ID)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/onedrive/token").contentType(MediaType.APPLICATION_JSON)
                .content(request("http://localhost/callback", VERIFIER, "invalid-client")))
                .andExpect(status().isBadRequest());
        upstream.verify();
    }

    @Test
    void sanitizesMicrosoftErrorsAndDoesNotReturnProviderBody() throws Exception {
        upstream.expect(requestTo(OneDriveOAuthController.TOKEN_URL)).andRespond(withBadRequest()
                .body("{\"error\":\"invalid_grant\",\"error_codes\":[70000],\"error_description\":\"private details\",\"refresh_token\":\"never-expose\"}")
                .contentType(MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/onedrive/token").contentType(MediaType.APPLICATION_JSON)
                .content(request("http://127.0.0.1:9000/callback", VERIFIER, CLIENT_ID)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("invalid_grant (AADSTS70000)"))
                .andExpect(jsonPath("$.error_description").doesNotExist())
                .andExpect(jsonPath("$.refresh_token").doesNotExist());
        upstream.verify();
    }

    @Test
    void missingRefreshTokenIsNotReportedAsSuccess() throws Exception {
        upstream.expect(requestTo(OneDriveOAuthController.TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"test-access\"}", MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/onedrive/token").contentType(MediaType.APPLICATION_JSON)
                .content(request("http://localhost/callback", VERIFIER, CLIENT_ID)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("missing_refresh_token"));
        upstream.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8765", "http://127.0.0.1:9000", "http://localhost"})
    void permitsLocalFileServerPreflight(String origin) throws Exception {
        mvc.perform(options("https://auth.example.test/api/onedrive/token").header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Access-Control-Allow-Origin", origin));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com", "http://localhost.evil.test", "null"})
    void blocksOtherBrowserOrigins(String origin) throws Exception {
        mvc.perform(options("https://auth.example.test/api/onedrive/token").header("Origin", origin)
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
