---
title: Privacy Policy
---

# Privacy Policy

This application complies with the following privacy policy:

1. We only access your Google account data (such as Google Drive, email, and basic profile info) with your explicit authorization.
2. The application may request the following permissions to support its core features:
   - `userinfo.profile`: Read your public Google profile (e.g., name and profile picture).
   - `userinfo.email`: Read your Google email address to identify the user.
   - `drive.file`: Create, access, and sync files in your Google Drive created or opened by this application.
3. All accessed data is used solely for essential application functions such as file upload, queries, and synchronization. Your data will never be shared with third parties.
4. We do not store, log, or track any personal information or account content.
5. All communication with Google APIs is encrypted via HTTPS to ensure data security.
6. You may revoke access at any time, and the application will immediately stop accessing your data.

## Optional Microsoft OneDrive authorization

When you select OneDrive, the application requests `offline_access` and the Microsoft Graph delegated permission `Files.ReadWrite.AppFolder`, which limits file access to this application's OneDrive folder. Google remains the default and is not authorized when you choose OneDrive.

To complete Microsoft's native PKCE authorization from the local HTML page, the authorization code, one-time PKCE verifier, public Client ID and local callback URI pass through this tool's HTTPS service. The service exchanges them with Microsoft and returns the refresh token to your browser. It does not retain these values or intentionally log request or response bodies. Responses containing tokens are marked `no-store`.

The resulting credential is displayed for you to copy into a GitHub Actions secret. It is not uploaded to GitHub automatically. You may revoke the application's Microsoft permissions at any time. Subsequent file uploads and token refreshes are performed by the CI workflow you configure, not by this preparation page.

If you have any questions about this privacy policy, feel free to contact us:  
📧 lh0528@gmail.com
