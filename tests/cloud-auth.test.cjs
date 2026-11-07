const { before, after, beforeEach, afterEach, test } = require('node:test');
const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../src/main/resources/static');
const clientId = '11111111-2222-3333-4444-555555555555';
const storageKey = 'aaps_ci_oauth_transaction';
let server, browser, baseUrl, context, page;
let pageErrors;

before(async () => {
  server = http.createServer((req, res) => {
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.end(fs.readFileSync(path.join(root, 'aaps-ci-preparation.html')));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}/aaps-ci-preparation.html`;
  browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, headless: true });
});

after(async () => {
  await browser?.close();
  if (server) await new Promise(resolve => server.close(resolve));
});

beforeEach(async () => {
  context = await browser.newContext({ locale: 'zh-TW', viewport: { width: 390, height: 844 } });
  page = await context.newPage();
  pageErrors = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  await page.route('**/*', route => {
    const url = new URL(route.request().url());
    if (url.hostname === '127.0.0.1') return route.continue();
    if (url.hostname === 'raw.githubusercontent.com') {
      const file = path.join(root, 'images', path.basename(url.pathname));
      if (fs.existsSync(file)) return route.fulfill({ body: fs.readFileSync(file), contentType: 'image/png' });
    }
    if (url.pathname.endsWith('/auth') || url.pathname.endsWith('/authorize')) return route.fulfill({ body: 'Mock identity provider', contentType: 'text/html' });
    // Do not require external CDNs or contact identity providers in automated tests.
    return route.fulfill({ body: '', contentType: url.pathname.endsWith('.css') ? 'text/css' : 'text/javascript' });
  });
  await page.goto(baseUrl + '?lang=zh_TW');
});

afterEach(async () => {
  await context.close();
  assert.deepEqual(pageErrors, [], 'No uncaught browser exceptions');
});

async function selectOneDrive() {
  await page.check('input[name="cloudProvider"][value="onedrive"]');
}

async function start(provider = 'onedrive') {
  if (provider === 'onedrive') {
    await selectOneDrive();
    await page.check('input[name="clientMode"][value="custom"]');
    await page.fill('#customClientId', clientId);
  }
  const requestPromise = page.waitForRequest(request => request.isNavigationRequest() && /\/auth(?:orize)?\?/.test(request.url()));
  await page.click('#startAuthBtn');
  const request = await requestPromise;
  const url = new URL(request.url());
  await page.waitForLoadState();
  // Return to the original origin without consuming the stored transaction.
  await page.goto(baseUrl + '?lang=zh_TW');
  const transaction = await page.evaluate(key => JSON.parse(sessionStorage.getItem(key)), storageKey);
  return { url, transaction };
}

async function callback(transaction, overrides = {}) {
  const params = new URLSearchParams({ code: 'test-auth-code', state: transaction.state, ...overrides });
  // Google includes language in its redirect; Microsoft must use the registered URI exactly.
  await page.goto(transaction.redirectUri + (transaction.redirectUri.includes('?') ? '&' : '?') + params.toString());
}

test('Google remains the default, cloud authorization precedes both JKS options, and mobile layout fits', async () => {
  assert.equal(await page.isChecked('input[name="cloudProvider"][value="google"]'), true);
  assert.equal(await page.textContent('#oauth2SecretLabel'), 'GDRIVE_OAUTH2:');
  const auth = await page.locator('#cloudAuth').boundingBox();
  const option1 = await page.locator('#option1').boundingBox();
  assert.ok(auth.y + auth.height <= option1.y);
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true);
  if (process.env.PREVIEW_DIR) {
    fs.mkdirSync(process.env.PREVIEW_DIR, { recursive: true });
    await page.screenshot({ path: path.join(process.env.PREVIEW_DIR, 'google-mobile.png'), fullPage: true });
  }
});

test('Provider switching isolates custom IDs, output, labels and the Google-only screenshot', async () => {
  await page.check('input[name="clientMode"][value="custom"]');
  await page.fill('#customClientId', 'test.apps.googleusercontent.com');
  await selectOneDrive();
  assert.equal(await page.textContent('#oauth2SecretLabel'), 'ONEDRIVE_OAUTH2:');
  assert.equal(await page.isVisible('#googleSecretExample'), false);
  assert.equal(await page.isVisible('#oneDriveNotConfigured'), false);
  await page.check('input[name="clientMode"][value="custom"]');
  await page.fill('#customClientId', clientId);
  await page.check('input[name="cloudProvider"][value="google"]');
  assert.equal(await page.inputValue('#customClientId'), 'test.apps.googleusercontent.com');
  await selectOneDrive();
  assert.equal(await page.inputValue('#customClientId'), clientId);
});

test('OneDrive rejects a malformed Client ID before navigation', async () => {
  await selectOneDrive();
  await page.check('input[name="clientMode"][value="custom"]');
  await page.fill('#customClientId', 'invalid');
  await page.click('#startAuthBtn');
  assert.match(await page.textContent('#authStatus'), /有效/);
  assert.ok(page.url().startsWith(baseUrl));
});

test('OneDrive sends native PKCE and offline app-folder scope without a secret or language in the callback', async () => {
  const { url, transaction } = await start();
  assert.equal(url.hostname, 'login.microsoftonline.com');
  assert.equal(url.searchParams.get('client_id'), clientId);
  assert.equal(url.searchParams.get('redirect_uri'), baseUrl);
  assert.equal(url.searchParams.get('scope'), 'offline_access https://graph.microsoft.com/Files.ReadWrite.AppFolder');
  assert.equal(url.searchParams.get('code_challenge_method'), 'S256');
  assert.equal(url.searchParams.get('code_challenge'), createHash('sha256').update(transaction.codeVerifier).digest('base64url'));
  assert.equal(url.searchParams.get('state'), transaction.state);
  assert.equal(url.searchParams.has('client_secret'), false);
});

test('OneDrive callback restores provider and language, exchanges once, and keeps the output separate', async () => {
  const { transaction } = await start();
  let exchanges = 0;
  await page.route('https://aaps-ci-preparation.duckdns.org/api/onedrive/token', async route => {
    exchanges++;
    const body = route.request().postDataJSON();
    assert.deepEqual(body, { clientId, code: 'test-auth-code', codeVerifier: transaction.codeVerifier, redirectUri: baseUrl });
    assert.equal('client_secret' in body, false);
    await route.fulfill({ json: { refresh_token: 'test-onedrive-refresh' } });
  });
  await callback(transaction);
  await page.waitForFunction(() => document.getElementById('oauth2Base64').value !== '');
  assert.equal(await page.isChecked('input[name="cloudProvider"][value="onedrive"]'), true);
  assert.equal(await page.inputValue('#langSelector'), 'zh_TW');
  assert.equal(Buffer.from(await page.inputValue('#oauth2Base64'), 'base64').toString(), clientId + '|test-onedrive-refresh');
  assert.equal(new URL(page.url()).searchParams.has('code'), false);
  assert.equal(await page.evaluate(key => sessionStorage.getItem(key), storageKey), null);
  assert.equal(exchanges, 1);
  await page.check('input[name="cloudProvider"][value="google"]');
  assert.equal(await page.inputValue('#oauth2Base64'), '');
  assert.equal(await page.textContent('#oauth2SecretLabel'), 'GDRIVE_OAUTH2:');
  await selectOneDrive();
  assert.notEqual(await page.inputValue('#oauth2Base64'), '');
  await page.selectOption('#langSelector', 'en');
  assert.notEqual(await page.inputValue('#oauth2Base64'), '');
  if (process.env.PREVIEW_DIR) {
    await page.selectOption('#langSelector', 'zh_TW');
    await page.screenshot({ path: path.join(process.env.PREVIEW_DIR, 'onedrive-mobile.png'), fullPage: true });
    await page.setViewportSize({ width: 1280, height: 1000 });
    await page.screenshot({ path: path.join(process.env.PREVIEW_DIR, 'onedrive-desktop.png'), fullPage: true });
  }
});

test('Google still uses its existing Client ID, token endpoint and two-part secret format', async () => {
  const { url, transaction } = await start('google');
  assert.equal(url.hostname, 'accounts.google.com');
  assert.equal(url.searchParams.get('client_id'), '705061051276-5ssikkg2ag39l7hj9t63saq549n3s2n5.apps.googleusercontent.com');
  assert.equal(url.searchParams.get('access_type'), 'offline');
  assert.equal(url.searchParams.get('scope'), 'https://www.googleapis.com/auth/drive.file');
  await page.route('https://oauth2.googleapis.com/token', async route => {
    const form = new URLSearchParams(route.request().postData());
    assert.equal(form.get('client_id'), transaction.clientId);
    assert.equal(form.get('code_verifier'), transaction.codeVerifier);
    assert.equal(form.get('redirect_uri'), transaction.redirectUri);
    await route.fulfill({ json: { refresh_token: 'test-google-refresh' } });
  });
  await callback(transaction);
  await page.waitForFunction(() => document.getElementById('oauth2Base64').value !== '');
  assert.equal(Buffer.from(await page.inputValue('#oauth2Base64'), 'base64').toString(), transaction.clientId + '|test-google-refresh');
});

test('Mismatched state is rejected before contacting a token endpoint', async () => {
  const { transaction } = await start();
  let exchanges = 0;
  await page.route('**/token', route => { exchanges++; return route.fulfill({ json: { refresh_token: 'should-not-use' } }); });
  await callback(transaction, { state: 'incorrect-state' });
  assert.match(await page.textContent('#authStatus'), /工作階段不符/);
  assert.equal(exchanges, 0);
  assert.equal(await page.inputValue('#oauth2Base64'), '');
});

test('Expired sessions are rejected without exchanging the code', async () => {
  const { transaction } = await start();
  await page.evaluate(({ key, value }) => sessionStorage.setItem(key, JSON.stringify(value)), {
    key: storageKey, value: { ...transaction, createdAt: Date.now() - 11 * 60 * 1000 }
  });
  await callback(transaction);
  assert.match(await page.textContent('#authStatus'), /已逾時/);
  assert.equal(await page.inputValue('#oauth2Base64'), '');
});

test('Provider denial restores OneDrive and displays a safe error', async () => {
  const { transaction } = await start();
  await page.goto(transaction.redirectUri + '?' + new URLSearchParams({ state: transaction.state, error: 'access_denied', error_description: '<script>untrusted</script>' }));
  assert.equal(await page.isChecked('input[name="cloudProvider"][value="onedrive"]'), true);
  assert.match(await page.textContent('#authStatus'), /access_denied/);
  assert.doesNotMatch(await page.textContent('#authStatus'), /script/);
  assert.equal(new URL(page.url()).searchParams.has('error_description'), false);
});

test('Missing refresh tokens, HTTP errors and network failures are not reported as success', async () => {
  for (const outcome of ['missing', 'http', 'network']) {
    await page.goto(baseUrl + '?lang=zh_TW');
    const { transaction } = await start();
    await page.route('https://aaps-ci-preparation.duckdns.org/api/onedrive/token', route => {
      if (outcome === 'network') return route.abort('failed');
      return route.fulfill({ status: outcome === 'http' ? 502 : 200,
        json: outcome === 'http' ? { error: 'invalid_grant' } : { access_token: 'test-access' } });
    });
    await callback(transaction);
    await page.waitForFunction(() => document.getElementById('authStatus').classList.contains('error'));
    assert.equal(await page.inputValue('#oauth2Base64'), '');
    assert.equal(await page.isDisabled('#startAuthBtn'), false);
    await page.unroute('https://aaps-ci-preparation.duckdns.org/api/onedrive/token');
  }
});

test('All available languages retain provider selection and use English fallback for new messages', async () => {
  await selectOneDrive();
  const languages = await page.locator('#langSelector option').evaluateAll(options => options.map(option => option.value));
  for (const lang of languages) {
    await page.selectOption('#langSelector', lang);
    assert.notEqual((await page.textContent('#cloudAuthTitle')).trim(), '');
    assert.equal(await page.textContent('#oauth2SecretLabel'), 'ONEDRIVE_OAUTH2:');
    assert.equal(await page.isChecked('input[name="cloudProvider"][value="onedrive"]'), true);
  }
});


test('Default OneDrive authorization uses the supplied native Client ID', async () => {
  await selectOneDrive();
  const requestPromise = page.waitForRequest(request => request.isNavigationRequest() && request.url().includes('/authorize?'));
  await page.click('#startAuthBtn');
  const request = await requestPromise;
  assert.equal(new URL(request.url()).searchParams.get('client_id'), '4e301264-ecaa-4496-ba6b-edd49d0deb7b');
});

test('An empty custom Client ID is rejected before navigation', async () => {
  await selectOneDrive();
  await page.check('input[name="clientMode"][value="custom"]');
  await page.fill('#customClientId', '   ');
  await page.click('#startAuthBtn');
  assert.match(await page.textContent('#authStatus'), /請先輸入 Client ID/);
  assert.ok(page.url().startsWith(baseUrl));
});
