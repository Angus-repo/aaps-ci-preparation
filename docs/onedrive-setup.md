# OneDrive authorization

`aaps-ci-preparation.html` offers Google Drive (the default) and Microsoft OneDrive above the JKS options. Google still exports `GDRIVE_OAUTH2`. OneDrive exports a separate `ONEDRIVE_OAUTH2` secret.

## Entra registration

1. Register an application in Microsoft Entra ID. To support personal OneDrive and work/school OneDrive, select **Accounts in any organizational directory and personal Microsoft accounts**. The authorization authority is `common`.
2. Under **Authentication → Add a platform → Mobile and desktop applications**, register the loopback callback used to open the HTML, for example `http://localhost/aaps-ci-preparation.html`. Keep the path and letter case identical. The page does not include `lang` in the Microsoft callback.
3. For a `localhost` native callback, Microsoft ignores the port during redirect matching. If the file manager opens `127.0.0.1` instead, register that actual URI, including its port, through the application manifest as documented by Microsoft. Do not silently replace the host: the authorization transaction is in the original origin's session storage.
4. Add the Microsoft Graph **delegated** permission `Files.ReadWrite.AppFolder`. The authorization request also includes `offline_access` to obtain a refresh token. No client secret is required, and no profile/email permissions are requested.
5. The public native Application (client) ID `4e301264-ecaa-4496-ba6b-edd49d0deb7b` is set in `defaultOneDriveClientId`. **Custom** accepts another native Client ID for testing. Do not put a client secret in the HTML or repository.

Do **not** register this callback as a single-page application (SPA). A SPA refresh token expires after 24 hours, including replacement tokens derived from it, which is unsuitable for this CI credential. A Web/confidential registration instead requires a client secret and does not match this implementation.

## Deploy the exchange endpoint before releasing the HTML

The existing Spring Boot application now exposes `POST /api/onedrive/token`. Deploy the updated application and ensure its HTTPS reverse proxy routes that path. The HTML currently points `oneDriveTokenExchangeUrl` to:

```text
https://aaps-ci-preparation.duckdns.org/api/onedrive/token
```

Change that constant if the application's actual public HTTPS address differs. Merely downloading the new HTML does not deploy this endpoint.

The local HTML initiates native Authorization Code + PKCE (S256), validates a random `state` and a ten-minute transaction lifetime, then posts the code, verifier, Client ID and exact callback URI to the service. This is needed because Microsoft's token endpoint does not support browser-origin redemption for a native client. The service sends a server-side request to the fixed Microsoft `common` token endpoint, without a client secret or a forwarded browser Origin header.

The service allows browser calls only from HTTP `localhost` or `127.0.0.1`, validates loopback callbacks and PKCE input, returns only the refresh token, and sends `Cache-Control: no-store`. It does not retain or intentionally log codes, verifiers or tokens. Keep HTTP request/response-body logging disabled at the server, proxy and APM layer. The HTML tells users that the HTTPS service participates in token exchange; tokens are not sent to that service for subsequent CI refreshes.

## CI secret contract

| Provider | Repository secret | Decoded Base64 contents |
| --- | --- | --- |
| Google Drive | `GDRIVE_OAUTH2` | `client_id\|refresh_token` |
| Microsoft OneDrive | `ONEDRIVE_OAUTH2` | `client_id\|refresh_token` |

Base64 is an encoding, not encryption. Store the generated value in GitHub Actions secrets, not source code. Selecting a different provider never puts its credentials under the other provider's label.

The consuming CI workflow must separately implement OneDrive uploads: exchange the refresh token at `https://login.microsoftonline.com/common/oauth2/v2.0/token` with `grant_type=refresh_token`, the Client ID and the Graph app-folder scope, then use `/me/drive/special/approot` for the application's files. This repository only prepares credentials; it does not contain or modify that upload workflow. Do not put `ONEDRIVE_OAUTH2` into a Google-only workflow.

Native refresh tokens are not permanent: Microsoft documents a default 90-day lifetime for non-SPA scenarios, subject to revocation and account policies. The consuming workflow must handle refreshed-token rotation and reauthorization. Continually reusing one unchanged GitHub secret must not be assumed to renew it indefinitely.

## Verification

```sh
mvn test
npm ci
npx playwright install --with-deps chromium
npm test
```

The browser tests mock the identity providers and do not log into an account. Set `CHROMIUM_PATH` to use an existing Chromium installation, and optionally set `PREVIEW_DIR` to capture mobile and desktop screenshots. After registering the loopback callback and deploying the endpoint, complete a real consent flow and verify the resulting credential in the OneDrive-capable CI workflow before release.

Official references:

- [Authorization Code flow and PKCE](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow)
- [Redirect URI and localhost restrictions](https://learn.microsoft.com/en-us/entra/identity-platform/reply-url)
- [Refresh token lifetime](https://learn.microsoft.com/en-us/entra/identity-platform/refresh-tokens)
- [OneDrive application folders](https://learn.microsoft.com/en-us/graph/onedrive-sharepoint-appfolder)
