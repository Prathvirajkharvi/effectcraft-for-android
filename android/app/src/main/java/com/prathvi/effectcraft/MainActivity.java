package com.prathvi.effectcraft;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/** Offline Android host for EffectCraft's existing web/WASM build. */
public final class MainActivity extends Activity {
    private static final String TAG = "EffectCraftAndroid";
    private static final String APP_HOST = "appassets.androidplatform.net";
    private static final String APP_URL = "https://" + APP_HOST + "/assets/web/index.html?frameworkers=1&nosw";
    private static final int PICK_FILES_REQUEST = 4107;

    private WebView webView;
    private ValueCallback<Uri[]> pendingFileCallback;
    private final DownloadBridge downloadBridge = new DownloadBridge();

    private static final String DOWNLOAD_PATCH = "(function(){"
            + "if(window.__effectCraftAndroidDownloadPatch)return;window.__effectCraftAndroidDownloadPatch=true;"
            + "document.addEventListener('click',function(event){"
            + "var node=event.target;var link=node&&node.closest?node.closest('a[download]'):null;"
            + "if(!link||!link.href||!link.href.startsWith('blob:'))return;"
            + "var bridge=window.EffectCraftAndroid;if(!bridge)return;"
            + "event.preventDefault();event.stopImmediatePropagation();"
            + "(async function(){var fileName=link.download||'EffectCraft-export.bin';"
            + "var blob=await (await fetch(link.href)).blob();"
            + "if(!bridge.beginDownload(fileName,blob.type||'application/octet-stream'))throw new Error('Could not create the output file');"
            + "var chunkSize=196608;"
            + "for(var offset=0;offset<blob.size;offset+=chunkSize){"
            + "var bytes=new Uint8Array(await blob.slice(offset,offset+chunkSize).arrayBuffer());"
            + "var binary='';for(var i=0;i<bytes.length;i+=32768){binary+=String.fromCharCode.apply(null,bytes.subarray(i,Math.min(i+32768,bytes.length)));}"
            + "if(!bridge.writeChunk(btoa(binary)))throw new Error('Could not write export data');}"
            + "if(!bridge.finishDownload())throw new Error('Could not finish the output file');"
            + "})().catch(function(error){try{bridge.abortDownload();}catch(ignore){}console.error('EffectCraft Android download failed:',error);});"
            + "},true);})();";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.rgb(16, 17, 20));
        getWindow().setNavigationBarColor(Color.rgb(16, 17, 20));

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(27, 28, 31));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.addJavascriptInterface(downloadBridge, "EffectCraftAndroid");

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setDefaultTextEncodingName("UTF-8");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                WebResourceResponse original = assetLoader.shouldInterceptRequest(uri);
                if (original == null) return null;
                Map<String, String> headers = new HashMap<>();
                if (original.getResponseHeaders() != null) headers.putAll(original.getResponseHeaders());
                headers.put("Cross-Origin-Opener-Policy", "same-origin");
                headers.put("Cross-Origin-Embedder-Policy", "require-corp");
                headers.put("Cross-Origin-Resource-Policy", "same-origin");
                int statusCode = original.getStatusCode();
                String reasonPhrase = original.getReasonPhrase();

                // AssetLoader responses may have an unset status code (-1).
                // The extended WebResourceResponse constructor requires 100-599.
                if (statusCode < 100 || statusCode > 599) {
                    statusCode = 200;
                    reasonPhrase = "OK";
                } else if (reasonPhrase == null || reasonPhrase.trim().isEmpty()) {
                    reasonPhrase = statusCode == 200 ? "OK" : "Response";
                }

                return new WebResourceResponse(
                        original.getMimeType(), original.getEncoding(), statusCode,
                        reasonPhrase, headers, original.getData());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equalsIgnoreCase(uri.getScheme()) && APP_HOST.equalsIgnoreCase(uri.getHost())) {
                    return false;
                }
                // External links leave the trusted, local app origin and open in the system browser.
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (Exception e) { Log.w(TAG, "No browser available for " + uri, e); }
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null && url.startsWith("https://" + APP_HOST + "/assets/web/")) {
                    view.evaluateJavascript(DOWNLOAD_PATCH, null);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) Log.e(TAG, "Page load failed: " + error.getDescription());
            }

            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                Log.e(TAG, "WebView renderer exited; crashed=" + detail.didCrash());
                showRendererError();
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFileCallback != null) pendingFileCallback.onReceiveValue(null);
                pendingFileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(intent, PICK_FILES_REQUEST);
                    return true;
                } catch (Exception e) {
                    pendingFileCallback = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, "File picker is unavailable", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                Log.d(TAG, message.message() + " (" + message.sourceId() + ":" + message.lineNumber() + ")");
                return true;
            }
        });

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(27, 28, 31));
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        // Create DecorView before accessing system bar insets.
        hideStatusBar();
        webView.loadUrl(APP_URL);
    }

    private void hideStatusBar() {
        Window window = getWindow();
        if (window == null) return;

        View decorView = window.getDecorView();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                return;
            }
        }

        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FILES_REQUEST) return;
        Uri[] selected = null;
        if (resultCode == RESULT_OK && data != null) {
            ClipData clip = data.getClipData();
            if (clip != null) {
                selected = new Uri[clip.getItemCount()];
                for (int i = 0; i < clip.getItemCount(); i++) selected[i] = clip.getItemAt(i).getUri();
            } else if (data.getData() != null) {
                selected = new Uri[]{data.getData()};
            }
        }
        if (pendingFileCallback != null) {
            pendingFileCallback.onReceiveValue(selected);
            pendingFileCallback = null;
        }
    }

    private void showRendererError() {
        runOnUiThread(() -> {
            android.widget.TextView message = new android.widget.TextView(this);
            message.setTextColor(Color.WHITE);
            message.setTextSize(16);
            message.setPadding(28, 28, 28, 28);
            message.setText("EffectCraft's graphics view stopped unexpectedly. Close and reopen the app.\n\nOn some phones, updating Android System WebView or switching to a newer Chrome/WebView version can help.");
            setContentView(message);
        });
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (pendingFileCallback != null) {
            pendingFileCallback.onReceiveValue(null);
            pendingFileCallback = null;
        }
        downloadBridge.abortDownload();
        if (webView != null) {
            webView.removeJavascriptInterface("EffectCraftAndroid");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    /** Receives large browser Blob exports in bounded chunks to avoid a huge base64 bridge call. */
    private final class DownloadBridge {
        private Uri outputUri;
        private OutputStream output;

        @JavascriptInterface
        public synchronized boolean beginDownload(String requestedName, String mimeType) {
            abortDownloadLocked();
            try {
                String safeName = requestedName == null ? "EffectCraft-export.bin" : requestedName;
                safeName = safeName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                if (safeName.isEmpty()) safeName = "EffectCraft-export.bin";
                if (safeName.length() > 120) safeName = safeName.substring(0, 120);
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, safeName);
                values.put(MediaStore.MediaColumns.MIME_TYPE,
                        mimeType == null || mimeType.isEmpty() ? "application/octet-stream" : mimeType);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/EffectCraft");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                outputUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (outputUri == null) return false;
                output = getContentResolver().openOutputStream(outputUri, "w");
                if (output == null) {
                    getContentResolver().delete(outputUri, null, null);
                    outputUri = null;
                    return false;
                }
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Could not start export", e);
                abortDownloadLocked();
                return false;
            }
        }

        @JavascriptInterface
        public synchronized boolean writeChunk(String base64) {
            if (output == null || base64 == null) return false;
            try {
                output.write(Base64.decode(base64, Base64.DEFAULT));
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Could not write export chunk", e);
                abortDownloadLocked();
                return false;
            }
        }

        @JavascriptInterface
        public synchronized boolean finishDownload() {
            if (output == null || outputUri == null) return false;
            try {
                output.flush();
                output.close();
                output = null;
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContentResolver().update(outputUri, values, null, null);
                outputUri = null;
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Export saved in Downloads/EffectCraft", Toast.LENGTH_LONG).show());
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Could not finish export", e);
                abortDownloadLocked();
                return false;
            }
        }

        @JavascriptInterface
        public synchronized void abortDownload() {
            abortDownloadLocked();
        }

        private void abortDownloadLocked() {
            if (output != null) {
                try { output.close(); } catch (IOException ignored) { }
                output = null;
            }
            if (outputUri != null) {
                try { getContentResolver().delete(outputUri, null, null); }
                catch (Exception e) { Log.w(TAG, "Could not remove partial export", e); }
                outputUri = null;
            }
        }
    }
}
