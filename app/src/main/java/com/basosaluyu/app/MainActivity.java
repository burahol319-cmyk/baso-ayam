package com.basosaluyu.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.*;
import android.widget.Toast;
import androidx.webkit.WebViewAssetLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int REQ_FILE = 1;
    private static final String HOST = "https://appassets.androidplatform.net";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
                       .addPathHandler("/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();

        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (u.toString().startsWith(HOST)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) {}
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                try { startActivityForResult(p.createIntent(), REQ_FILE); }
                catch (Exception e) { filePathCallback = null; return false; }
                return true;
            }
        });
        // Download file hasil export (blob:) ke folder Download
        web.setDownloadListener((url, ua, cd, mime, len) -> {
            if (url.startsWith("blob:")) {
                String name = URLUtil.guessFileName(url, cd, mime);
                web.evaluateJavascript("(function(){var x=new XMLHttpRequest();x.open('GET','" + url + "',true);x.responseType='blob';"
                    + "x.onload=function(){var r=new FileReader();r.onloadend=function(){AndroidBridge.save(r.result,'" + name.replace("'", "") + "','" + mime + "');};r.readAsDataURL(x.response);};x.send();})();", null);
            }
        });

        if (b == null) web.loadUrl(HOST + "/www/index.html"); else web.restoreState(b);
    }

    class Bridge {
        @JavascriptInterface public void save(String dataUrl, String name, String mime) {
            try {
                byte[] data = Base64.decode(dataUrl.substring(dataUrl.indexOf(',') + 1), Base64.DEFAULT);
                OutputStream os; 
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    v.put(MediaStore.Downloads.MIME_TYPE, mime);
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    os = getContentResolver().openOutputStream(u);
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    dir.mkdirs();
                    os = new FileOutputStream(new File(dir, name));
                }
                os.write(data); os.close();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Tersimpan di folder Download: " + name, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Gagal menyimpan file", Toast.LENGTH_LONG).show());
            }
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE && filePathCallback != null) {
            filePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            filePathCallback = null;
        }
    }
    @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }
    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
