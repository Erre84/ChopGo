package com.erre84.chopdeck;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public final class MainActivity extends Activity {
    static final String HOST = "app.chopdeck.local";
    static final String BASE = "https://" + HOST + "/";
    static final int REQ_FILE = 11, REQ_PROJ = 12, REQ_PERM = 13;

    private WebView web, browser;
    private LinearLayout browserBox;
    private TextView urlView;
    private ValueCallback<Uri[]> fileCb;
    private boolean resumed = false;
    private OutputStream saveOut;
    private Uri saveUri;
    private String saveName = "";
    private boolean pendingCapture = false;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        setupMain(web);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        browserBox = buildBrowser();
        browserBox.setVisibility(View.GONE);
        root.addView(browserBox, new FrameLayout.LayoutParams(-1, -1));
        root.setFitsSystemWindows(true);
        setContentView(root);
        web.loadUrl(BASE + "index.html");
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
    }

    /* ---------------- WebView principal ---------------- */
    private void setupMain(WebView w) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setTextZoom(100);
        w.setBackgroundColor(Color.rgb(0xCF, 0xC9, 0xBC));
        w.addJavascriptInterface(new Bridge(), "Android");
        w.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return serve(u.getPath());
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                openBrowser(u.toString());
                return true;
            }
        });
        w.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "video/*"});
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try { startActivityForResult(Intent.createChooser(i, "Abrir audio"), REQ_FILE); }
                catch (ActivityNotFoundException e) { fileCb = null; return false; }
                return true;
            }
        });
    }

    private static final Map<String, String> MIME = new HashMap<>();
    static {
        MIME.put("html", "text/html"); MIME.put("js", "application/javascript"); MIME.put("css", "text/css");
        MIME.put("wav", "audio/wav"); MIME.put("png", "image/png"); MIME.put("svg", "image/svg+xml");
        MIME.put("ttf", "font/ttf"); MIME.put("woff2", "font/woff2"); MIME.put("json", "application/json");
    }

    private WebResourceResponse serve(String path) {
        try {
            if (path == null || path.equals("/")) path = "/index.html";
            String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
            String mime = MIME.containsKey(ext) ? MIME.get(ext) : "application/octet-stream";
            InputStream in;
            if (path.startsWith("/capture/")) {
                File f = new File(new File(getCacheDir(), "capture"), new File(path).getName());
                in = new FileInputStream(f);
            } else {
                in = getAssets().open("www" + path);
            }
            WebResourceResponse res = new WebResourceResponse(mime, mime.startsWith("text") ? "utf-8" : null, in);
            Map<String, String> h = new HashMap<>();
            h.put("Cache-Control", "no-store");
            h.put("Access-Control-Allow-Origin", "*");
            res.setResponseHeaders(h);
            return res;
        } catch (Exception e) {
            return new WebResourceResponse("text/plain", "utf-8", 404, "Not found", null, null);
        }
    }

    /* ---------------- Navegador interno (Discogs, Samplette, etc.) ---------------- */
    private Button barButton(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(true);
        b.setTextColor(Color.rgb(0xEE, 0xE8, 0xDA));
        b.setBackgroundColor(Color.rgb(0x4A, 0x48, 0x43));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(24, 0, 24, 0);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout buildBrowser() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.rgb(0x34, 0x33, 0x2F));
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(8, 8, 8, 8);
        bar.setBackgroundColor(Color.rgb(0xBD, 0xB6, 0xA7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 110);
        lp.setMargins(0, 0, 8, 0);
        bar.addView(barButton("✕ Deck", v -> closeBrowser()), lp);
        bar.addView(barButton("◀", v -> { if (browser.canGoBack()) browser.goBack(); }), lp);
        urlView = new TextView(this);
        urlView.setTextColor(Color.rgb(0x24, 0x22, 0x1E));
        urlView.setTextSize(12);
        urlView.setSingleLine(true);
        urlView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(urlView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(barButton("Chrome", v -> external(browser.getUrl())), lp);
        box.addView(bar, new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        browser = new WebView(this);
        WebSettings s = browser.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        browser.setWebChromeClient(new WebChromeClient());
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String sc = r.getUrl().getScheme();
                if ("http".equals(sc) || "https".equals(sc)) return false;
                external(r.getUrl().toString());
                return true;
            }
            @Override public void onPageFinished(WebView v, String url) { urlView.setText(url); }
        });
        box.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1f));
        return box;
    }

    void openBrowser(String url) {
        runOnUiThread(() -> {
            browserBox.setVisibility(View.VISIBLE);
            urlView.setText(url);
            browser.loadUrl(url);
            CaptureService.ignore = false;
            js("window.cdHidden&&cdHidden(true)");
        });
    }

    void closeBrowser() {
        browserBox.setVisibility(View.GONE);
        browser.onPause();
        browser.loadUrl("about:blank");
        browser.onResume();
        CaptureService.ignore = resumed;
        js("window.cdHidden&&cdHidden(false)");
    }

    void external(String url) {
        if (url == null) return;
        try {
            Intent i = Intent.parseUri(url, url.startsWith("intent:") ? Intent.URI_INTENT_SCHEME : 0);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            toast("No hay ninguna app para abrir este enlace");
        }
    }

    void js(String code) { runOnUiThread(() -> web.evaluateJavascript(code, null)); }
    void toast(String m) { runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show()); }

    /* ---------------- Captura ---------------- */
    void requestCapture() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingCapture = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_PERM);
            return;
        }
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJ);
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] g) {
        super.onRequestPermissionsResult(code, p, g);
        if (code == REQ_PERM && pendingCapture) {
            pendingCapture = false;
            if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) requestCapture();
            else js("window.cdCapture&&cdCapture('denied')");
        }
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_FILE) {
            Uri[] uris = null;
            if (result == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    uris = new Uri[n];
                    for (int i = 0; i < n; i++) uris[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) uris = new Uri[]{data.getData()};
            }
            if (fileCb != null) fileCb.onReceiveValue(uris);
            fileCb = null;
        } else if (code == REQ_PROJ) {
            if (result == RESULT_OK && data != null) {
                Intent s = new Intent(this, CaptureService.class).putExtra("result", result).putExtra("data", data);
                startForegroundService(s);
                js("window.cdCapture&&cdCapture('on')");
            } else js("window.cdCapture&&cdCapture('denied')");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        web.onResume();
        CaptureService.ignore = browserBox.getVisibility() != View.VISIBLE;
        js("window.cdResume&&cdResume()");
    }

    @Override protected void onPause() {
        resumed = false;
        CaptureService.ignore = false;
        js("window.cdHidden&&cdHidden(true)");
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (browserBox.getVisibility() == View.VISIBLE) {
            if (browser.canGoBack()) browser.goBack(); else closeBrowser();
        } else moveTaskToBack(true);
    }

    private static void le16(ByteArrayOutputStream o, int v) { o.write(v & 255); o.write((v >> 8) & 255); }
    private static void le32(ByteArrayOutputStream o, int v) { le16(o, v); le16(o, v >> 16); }

    /* ---------------- Puente JS ---------------- */
    final class Bridge {
        @JavascriptInterface public String info() {
            CaptureService c = CaptureService.current;
            boolean on = CaptureService.isRunning();
            double fresh = c == null ? 0 : c.fresh();
            float db = c == null ? -96 : c.db;
            String err = CaptureService.error.replace("\"", "'");
            return "{\"capturing\":" + on + ",\"fresh\":" + String.format(java.util.Locale.US, "%.2f", fresh)
                    + ",\"db\":" + String.format(java.util.Locale.US, "%.1f", db) + ",\"error\":\"" + err + "\"}";
        }
        @JavascriptInterface public void startCapture() { runOnUiThread(MainActivity.this::requestCapture); }
        @JavascriptInterface public void stopCapture() {
            Intent s = new Intent(MainActivity.this, CaptureService.class).setAction("stop");
            try { startService(s); } catch (Exception ignored) { }
        }
        /** Escribe lo capturado pendiente a un WAV y devuelve su nombre ("" si no hay audio). */
        @JavascriptInterface public String takeCapture(int seconds) {
            CaptureService c = CaptureService.current;
            if (c == null) return "";
            short[] pcm = c.take(Math.max(1, Math.min(seconds, CaptureService.MAXS)));
            if (pcm.length == 0) return "";
            try {
                File dir = new File(getCacheDir(), "capture");
                dir.mkdirs();
                File[] old = dir.listFiles();
                if (old != null) for (File f : old) f.delete();
                String name = "captura_" + System.currentTimeMillis() + ".wav";
                FileOutputStream out = new FileOutputStream(new File(dir, name));
                ByteArrayOutputStream h = new ByteArrayOutputStream();
                int bytes = pcm.length * 2;
                h.write('R'); h.write('I'); h.write('F'); h.write('F'); le32(h, 36 + bytes);
                h.write('W'); h.write('A'); h.write('V'); h.write('E');
                h.write('f'); h.write('m'); h.write('t'); h.write(' '); le32(h, 16); le16(h, 1); le16(h, 2);
                le32(h, CaptureService.SR); le32(h, CaptureService.SR * 4); le16(h, 4); le16(h, 16);
                h.write('d'); h.write('a'); h.write('t'); h.write('a'); le32(h, bytes);
                out.write(h.toByteArray());
                byte[] buf = new byte[65536];
                int k = 0;
                for (short s : pcm) {
                    buf[k++] = (byte) (s & 255);
                    buf[k++] = (byte) ((s >> 8) & 255);
                    if (k == buf.length) { out.write(buf); k = 0; }
                }
                out.write(buf, 0, k);
                out.close();
                return name;
            } catch (Exception e) {
                return "";
            }
        }
        @JavascriptInterface public void openBrowser(String url) { MainActivity.this.openBrowser(url); }
        @JavascriptInterface public void openExternal(String url) { runOnUiThread(() -> external(url)); }
        @JavascriptInterface public void openYouTube() {
            runOnUiThread(() -> {
                Intent i = getPackageManager().getLaunchIntentForPackage("com.google.android.youtube");
                if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                else external("https://m.youtube.com");
            });
        }
        @JavascriptInterface public boolean saveBegin(String name) {
            try {
                saveName = name;
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, name.endsWith(".wav") ? "audio/wav" : "application/zip");
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ChopDeck");
                v.put(MediaStore.Downloads.IS_PENDING, 1);
                saveUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                saveOut = saveUri == null ? null : getContentResolver().openOutputStream(saveUri);
                return saveOut != null;
            } catch (Exception e) { saveOut = null; return false; }
        }
        @JavascriptInterface public boolean saveChunk(String b64) {
            try { saveOut.write(Base64.decode(b64, Base64.DEFAULT)); return true; }
            catch (Exception e) { return false; }
        }
        @JavascriptInterface public String saveEnd() {
            try {
                saveOut.close();
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(saveUri, v, null, null);
                return "Descargas/ChopDeck/" + saveName;
            } catch (Exception e) { return ""; }
        }
        @JavascriptInterface public void share() {
            final Uri u = saveUri;
            if (u == null) return;
            runOnUiThread(() -> {
                Intent s = new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, u)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(s, "Enviar"));
            });
        }
    }
}
