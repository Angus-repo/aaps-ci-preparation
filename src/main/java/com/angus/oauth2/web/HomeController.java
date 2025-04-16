package com.angus.oauth2.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.ui.Model;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.angus.oauth2.service.MarkdownService;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Random;
import java.util.Locale;

@Controller
public class HomeController {
    
    @Autowired
    private MessageSource messageSource;
    
    @Autowired
    private MarkdownService markdownService;
    
    @GetMapping("/")
    public String home(Model model) {
        String googleSiteVerification = System.getenv("GOOGLE_SITE_VERIFICATION");
        model.addAttribute("googleSiteVerification", googleSiteVerification);
        return "index";
    }

    @GetMapping("/welcome")
    public String welcome(@RequestParam(required = false) String lang, Model model, HttpSession session) {
        Locale locale;
        if (lang != null) {
            locale = switch (lang.toLowerCase()) {
                case "en" -> new Locale("en");
                case "cs" -> new Locale("cs");
                case "zh-tw" -> new Locale("zh", "TW");
                default -> LocaleContextHolder.getLocale();
            };
            // 保存語系設定
            LocaleContextHolder.setLocale(locale);
            session.setAttribute("org.springframework.web.servlet.i18n.SessionLocaleResolver.LOCALE", locale);
        } else {
            locale = LocaleContextHolder.getLocale();
        }
        
        // 添加語言相關資訊到模型
        model.addAttribute("currentLocale", locale.toLanguageTag());
        model.addAttribute("welcomeMessage", messageSource.getMessage("welcome.message", null, locale));
        
        return "welcome";
    }
    
    @GetMapping("/login")
    public String login(HttpSession session, Model model) throws Exception {
        // 生成 code_verifier
        String codeVerifier = generateCodeVerifier();
        // 生成 code_challenge
        String codeChallenge = generateCodeChallenge(codeVerifier);
        
        // 將 code_verifier 存入 session 供後續使用
        session.setAttribute("code_verifier", codeVerifier);
        
        String oauthUrl = String.format(
            "https://accounts.google.com/o/oauth2/v2/auth?" +
            "client_id=%s" +
            "&redirect_uri=%s" +
            "&response_type=code" +
            "&scope=%s" +
            "&access_type=offline" +
            "&prompt=consent" +
            "&code_challenge=%s" +
            "&code_challenge_method=S256",
            OAuth2Constants.CLIENT_ID,
            OAuth2Constants.REDIRECT_URI,
            OAuth2Constants.SCOPE,
            codeChallenge
        );
        
        model.addAttribute("oauthUrl", oauthUrl);
        return "login";
    }

    @PostMapping("/generate-jks")
    @ResponseBody
    public JksResponse generateJks() {
        try {
            // 生成隨機密碼
            String keystorePassword = generateRandomPassword();
            String keyPassword = generateRandomPassword();
            String keyAlias = "key0";
            String tempDir = System.getProperty("java.io.tmpdir");
            String keystorePath = tempDir + "/keystore.jks";

            // 使用 keytool 生成 keystore
            String[] command = {
                "keytool",
                "-genkey",
                "-v",
                "-keystore", keystorePath,
                "-alias", keyAlias,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "10000",
                "-storepass", keystorePassword,
                "-keypass", keyPassword,
                "-dname", "CN=Android Debug,O=Android,C=US"
            };

            ProcessBuilder pb = new ProcessBuilder(command);
            Process p = pb.start();
            p.waitFor();

            // 讀取生成的 keystore 文件並轉換為 base64
            File keystoreFile = new File(keystorePath);
            byte[] keystoreBytes = new byte[(int) keystoreFile.length()];
            FileInputStream fis = new FileInputStream(keystoreFile);
            fis.read(keystoreBytes);
            fis.close();

            // 刪除暫時文件
            keystoreFile.delete();

            String keystoreBase64 = Base64.getEncoder().encodeToString(keystoreBytes);

            return new JksResponse(
                keystoreBase64,
                keystorePassword,
                keyAlias,
                keyPassword
            );

        } catch (Exception e) {
            e.printStackTrace();
            Locale locale = LocaleContextHolder.getLocale();
            String errorMsg = messageSource.getMessage("jks.generate.error", null, "Failed to generate JKS", locale);
            throw new RuntimeException(errorMsg + ": " + e.getMessage());
        }
    }

    @PostMapping("/verify-jks")
    @ResponseBody
    public VerifyResponse verifyJks(@RequestBody JksVerifyRequest request) {
        String tempDir = System.getProperty("java.io.tmpdir");
        Path keystorePath = Path.of(tempDir, "keystore");
        File keystoreDir = keystorePath.toFile();
        File jksFile = new File(keystoreDir, "keystore.jks");

        try {
            // 確保目錄存在
            if (!keystoreDir.exists()) {
                keystoreDir.mkdirs();
            }

            // 解碼 Base64 並寫入檔案
            byte[] keystoreData = Base64.getDecoder().decode(request.getKeystoreBase64());
            try (FileOutputStream fos = new FileOutputStream(jksFile)) {
                fos.write(keystoreData);
            }

            // 準備 keytool 命令
            String[] command = {
                "keytool",
                "-list",
                "-alias", request.getKeyAlias(),
                "-keystore", jksFile.getAbsolutePath(),
                "-storepass", request.getKeystorePassword(),
                "-keypass", request.getKeyPassword()
            };

            // 執行 keytool 命令
            ProcessBuilder pb = new ProcessBuilder(command);
            Process p = pb.start();
            
            // 讀取命令輸出並處理錯誤
            String error = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            
            int exitCode = p.waitFor();

            // 清理臨時文件
            jksFile.delete();
            keystoreDir.delete();

            if (exitCode == 0) {
                Locale locale = LocaleContextHolder.getLocale();
                String successMsg = messageSource.getMessage("jks.verify.success", null, "JKS verification successful!", locale);
                return new VerifyResponse(true, successMsg);
            } else {
                Locale locale = LocaleContextHolder.getLocale();
                String failMsg = messageSource.getMessage("jks.verify.fail", null, "JKS verification failed", locale);
                return new VerifyResponse(false, failMsg + ": " + error);
            }

        } catch (Exception e) {
            // 確保清理臨時文件
            jksFile.delete();
            keystoreDir.delete();
            
            Locale locale = LocaleContextHolder.getLocale();
            String errorMsg = messageSource.getMessage("jks.verify.process.error", null, "Error during verification process", locale);
            return new VerifyResponse(false, errorMsg + ": " + e.getMessage());
        }
    }

    @PostMapping("/convert-jks")
    @ResponseBody
    public JksConvertResponse convertJks(@RequestParam("file") MultipartFile file) {
        try {
            // 將檔案內容轉換為 Base64
            String base64Content = Base64.getEncoder().encodeToString(file.getBytes());
            Locale locale = LocaleContextHolder.getLocale();
            String successMsg = messageSource.getMessage("jks.convert.success", null, "Successfully converted JKS file", locale);
            return new JksConvertResponse(true, successMsg, base64Content);
        } catch (Exception e) {
            e.printStackTrace();
            Locale locale = LocaleContextHolder.getLocale();
            String errorMsg = messageSource.getMessage("jks.convert.error", null, "Conversion failed", locale);
            return new JksConvertResponse(false, errorMsg + ": " + e.getMessage(), null);
        }
    }

    @GetMapping("/docs/{fileName:.+}")
    public String viewDocument(@PathVariable String fileName, Model model) {
        try {
            // 從 resources/docs 目錄讀取文件
            ClassPathResource resource = new ClassPathResource("docs/" + fileName);
            String markdown = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            
            // 移除 YAML front matter 並取得標題
            String content = removeYamlFrontMatter(markdown);
            String title = extractTitle(markdown);
            
            // 將 Markdown 轉換為 HTML
            String html = markdownService.convertToHtml(content);
            
            // 添加到模型
            model.addAttribute("content", html);
            model.addAttribute("title", title);
            
            return "docs/document";
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
    }

    private String removeYamlFrontMatter(String markdown) {
        if (markdown.startsWith("---")) {
            int endIndex = markdown.indexOf("---", 3);
            if (endIndex != -1) {
                // 返回 front matter 之後的內容，並移除多餘的空行
                return markdown.substring(endIndex + 3).trim();
            }
        }
        return markdown;
    }

    private String extractTitle(String markdown) {
        if (markdown.startsWith("---")) {
            int endIndex = markdown.indexOf("---", 3);
            if (endIndex != -1) {
                String frontMatter = markdown.substring(3, endIndex).trim();
                for (String line : frontMatter.split("\n")) {
                    if (line.startsWith("title:")) {
                        return line.substring("title:".length()).trim();
                    }
                }
            }
        }
        return "Document";
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new SecureRandom();
        for (int i = 0; i < 10; i++) {
            int index = random.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    private String generateCodeVerifier() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] codeVerifier = new byte[32];
        secureRandom.nextBytes(codeVerifier);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifier);
    }
    
    private String generateCodeChallenge(String codeVerifier) throws Exception {
        byte[] bytes = codeVerifier.getBytes(StandardCharsets.US_ASCII);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}

class JksResponse {
    private String keystoreBase64;
    private String keystorePassword;
    private String keyAlias;
    private String keyPassword;

    public JksResponse(String keystoreBase64, String keystorePassword, String keyAlias, String keyPassword) {
        this.keystoreBase64 = keystoreBase64;
        this.keystorePassword = keystorePassword;
        this.keyAlias = keyAlias;
        this.keyPassword = keyPassword;
    }

    // Getters
    public String getKeystoreBase64() { return keystoreBase64; }
    public String getKeystorePassword() { return keystorePassword; }
    public String getKeyAlias() { return keyAlias; }
    public String getKeyPassword() { return keyPassword; }
}

class JksVerifyRequest {
    private String keystoreBase64;
    private String keystorePassword;
    private String keyAlias;
    private String keyPassword;

    // Getters and Setters
    public String getKeystoreBase64() { return keystoreBase64; }
    public void setKeystoreBase64(String keystoreBase64) { this.keystoreBase64 = keystoreBase64; }
    
    public String getKeystorePassword() { return keystorePassword; }
    public void setKeystorePassword(String keystorePassword) { this.keystorePassword = keystorePassword; }
    
    public String getKeyAlias() { return keyAlias; }
    public void setKeyAlias(String keyAlias) { this.keyAlias = keyAlias; }
    
    public String getKeyPassword() { return keyPassword; }
    public void setKeyPassword(String keyPassword) { this.keyPassword = keyPassword; }
}

class VerifyResponse {
    private boolean success;
    private String message;

    public VerifyResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    // Getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}

class JksConvertResponse {
    private boolean success;
    private String message;
    private String base64Content;

    public JksConvertResponse(boolean success, String message, String base64Content) {
        this.success = success;
        this.message = message;
        this.base64Content = base64Content;
    }

    // Getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getBase64Content() { return base64Content; }
}