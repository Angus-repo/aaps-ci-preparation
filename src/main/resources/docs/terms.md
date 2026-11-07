---
title: Terms of Service
---

# Terms of Service

Welcome to our application. By using this service, you agree to the following terms:

1. This is a free tool designed to prepare CI credentials for Google Drive or Microsoft OneDrive.
2. By using the application, you agree to authorize access to certain parts of your Google account via OAuth2. The application may request the following permissions:
   - `/auth/userinfo.profile`: Read your public Google profile (e.g., name and profile picture).
   - `/auth/userinfo.email`: Read your Google email address.
   - `/auth/drive.file`: View and manage Google Drive files created or opened with this app.
3. All features are provided "as is". The developer makes no warranties regarding service availability or data integrity and shall not be liable for any loss or damage.
4. Users must not use this application for illegal activities or in violation of Google's or Microsoft's API usage policies.
5. The developer reserves the right to modify these terms at any time without prior notice. It is recommended that users review the latest version periodically.

For optional OneDrive authorization, the application requests `offline_access` and Microsoft Graph `Files.ReadWrite.AppFolder`. The local page uses this tool's HTTPS service to exchange a one-time PKCE authorization code. The generated `ONEDRIVE_OAUTH2` credential requires a compatible CI upload workflow; generating a credential alone does not add OneDrive support to that workflow.

If you have any questions or concerns, feel free to contact us:  
📧 lh0528@gmail.com
