package com.angus.aaps.ci.keystore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class HomeController {
    
    @Autowired
    private MessageSource messageSource;
    
    @GetMapping("/")
    public String home(Model model) {
        String googleSiteVerification = System.getenv("GOOGLE_SITE_VERIFICATION");
        model.addAttribute("googleSiteVerification", googleSiteVerification);
        return "index";
    }

    @PostMapping("/generate-jks")
    @ResponseBody
    public JksResponse generateJks() {
        try {
            String keystorePassword = generateRandomPassword();
            String keyPassword = generateRandomPassword();
            String keyAlias = "key0";
            String tempDir = System.getProperty("java.io.tmpdir");
            String keystorePath = tempDir + "/keystore_" + java.util.UUID.randomUUID() + ".jks";

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
                "-storetype", "JKS",
                "-dname", "CN=Android Debug,O=Android,C=US"
            };

            ProcessBuilder pb = new ProcessBuilder(command);
            Process p = pb.start();
            p.waitFor();

            File keystoreFile = new File(keystorePath);
            byte[] keystoreBytes = new byte[(int) keystoreFile.length()];
            FileInputStream fis = new FileInputStream(keystoreFile);
            fis.read(keystoreBytes);
            fis.close();

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
        String randomJksName = "keystore_" + java.util.UUID.randomUUID() + ".jks";
        File jksFile = new File(keystoreDir, randomJksName);

        try {
            if (!keystoreDir.exists()) {
                keystoreDir.mkdirs();
            }

            byte[] keystoreData = Base64.getDecoder().decode(request.getKeystoreBase64());
            try (FileOutputStream fos = new FileOutputStream(jksFile)) {
                fos.write(keystoreData);
            }

            String[] command = {
                "keytool",
                "-list",
                "-alias", request.getKeyAlias(),
                "-keystore", jksFile.getAbsolutePath(),
                "-storepass", request.getKeystorePassword(),
                "-keypass", request.getKeyPassword(),
                "-storetype", "JKS"
            };

            ProcessBuilder pb = new ProcessBuilder(command);
            Process p = pb.start();
            
            String error = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            
            int exitCode = p.waitFor();

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