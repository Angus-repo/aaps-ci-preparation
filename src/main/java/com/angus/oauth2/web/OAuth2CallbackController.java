package com.angus.oauth2.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@RestController
public class OAuth2CallbackController {

    @GetMapping("/oauth2callback")
    public ResponseEntity<String> handleOAuth2Callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            HttpSession session) {
        
        if (error != null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("<h1>授權失敗</h1><p>" + error + "</p>");
        }

        if (code == null) {
            return ResponseEntity.badRequest().body("<h1>缺少授權碼</h1>");
        }

        // 從 session 中取出 code_verifier
        String codeVerifier = (String) session.getAttribute("code_verifier");
        if (codeVerifier == null) {
            return ResponseEntity.badRequest()
                .body("<h1>驗證失敗</h1><p>找不到 code_verifier，請重新開始授權流程</p>");
        }

        try {
            // 1. 交換 Access + Refresh Token
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", code);
            params.add("client_id", OAuth2Constants.CLIENT_ID);
            params.add("redirect_uri", OAuth2Constants.REDIRECT_URI);
            params.add("grant_type", "authorization_code");
            params.add("code_verifier", codeVerifier);  // 加入 code_verifier

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = 
                new HttpEntity<>(params, headers);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.postForEntity(
                OAuth2Constants.TOKEN_ENDPOINT, 
                request, 
                String.class
            );

            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(response.getBody());
            
            if (!json.has("access_token")) {
                return ResponseEntity.badRequest()
                    .body("<h1>Token 交換失敗</h1><pre>" + json.toPrettyString() + "</pre>");
            }

            String accessToken = json.get("access_token").asText();
            String refreshToken = json.has("refresh_token") ? 
                json.get("refresh_token").asText() : "（未回傳 refresh_token）";

            // 2. 使用 Google Drive API
            HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
            JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

            GoogleCredential credential = new GoogleCredential.Builder()
                    .setTransport(transport)
                    .setJsonFactory(jsonFactory)
                    .setClientSecrets(OAuth2Constants.CLIENT_ID, OAuth2Constants.CLIENT_SECRET)
                    .build()
                    .setAccessToken(accessToken);

            Drive driveService = new Drive.Builder(
                    transport,
                    jsonFactory,
                    credential)
                    .setApplicationName("SpringBoot-GoogleDriveDemo")
                    .build();

            FileList files = driveService.files().list()
                    .setPageSize(10)
                    .setFields("files(id, name)")
                    .execute();

            StringBuilder fileListHtml = new StringBuilder("<ul>");
            List<com.google.api.services.drive.model.File> fileItems = files.getFiles();
            if (fileItems == null || fileItems.isEmpty()) {
                fileListHtml.append("<li>沒有檔案</li>");
            } else {
                for (com.google.api.services.drive.model.File file : fileItems) {
                    fileListHtml.append("<li>")
                        .append(file.getName())
                        .append(" (")
                        .append(file.getId())
                        .append(")</li>");
                }
            }
            fileListHtml.append("</ul>");

            // 3. 清除 session 中的 code_verifier
            session.removeAttribute("code_verifier");

            // 4. 顯示頁面
            String html = """
                    <html>
                    <body>
                      <h1>OAuth2 授權成功</h1>
                      <p><b>Refresh Token:</b></p>
                      <pre>%s</pre>
                      <h2>Google Drive 前 10 個檔案：</h2>
                      %s
                    </body>
                    </html>
                    """.formatted(refreshToken, fileListHtml.toString());

            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("<h1>Google Drive 錯誤</h1><pre>" + e.getMessage() + "</pre>");
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("<h1>錯誤</h1><pre>" + ex.getMessage() + "</pre>");
        }
    }
}