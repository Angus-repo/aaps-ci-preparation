package com.angus.oauth2.web;

public class OAuth2Constants {
    public static final String CLIENT_ID = System.getenv("OAUTH_CLIENT_ID");
    public static final String CLIENT_SECRET = System.getenv("OAUTH_CLIENT_SECRET");
    public static final String REDIRECT_URI = System.getenv("OAUTH_REDIRECT_URI");
    public static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    public static final String SCOPE = "https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.appdata";
}