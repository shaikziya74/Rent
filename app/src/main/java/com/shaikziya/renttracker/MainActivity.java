package com.shaikziya.renttracker;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView web;

    private SharedPreferences prefs() {
        return getSharedPreferences("rt", MODE_PRIVATE);
    }

    private void js(String code) {
        runOnUiThread(() -> web.evaluateJavascript(code, null));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient());
        if (b != null) web.restoreState(b);
        else web.loadUrl("file:///android_asset/index.html");
    }

    class Bridge {
        @JavascriptInterface
        public boolean hasBackupFile() {
            return prefs().getString("uri", null) != null;
        }

        @JavascriptInterface
        public void chooseBackupFile() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, "rent-tracker-backup.json");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(i, 3);
            });
        }

        @JavascriptInterface
        public void autoBackup(final String text) {
            final String uri = prefs().getString("uri", null);
            if (uri == null) {
                js("onBackupResult(false," + JSONObject.quote("No backup file chosen") + ")");
                return;
            }
            new Thread(() -> {
                try (OutputStream o = getContentResolver().openOutputStream(Uri.parse(uri), "wt")) {
                    o.write(text.getBytes("UTF-8"));
                    js("onBackupResult(true,\"\")");
                } catch (Exception e) {
                    js("onBackupResult(false," + JSONObject.quote(String.valueOf(e.getMessage())) + ")");
                }
            }).start();
        }

        @JavascriptInterface
        public void restoreBackup() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, 2);
            });
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        try {
            if (req == 3) {
                try {
                    int fl = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(u, fl);
                } catch (Exception ignored) {
                }
                prefs().edit().putString("uri", u.toString()).apply();
                web.evaluateJavascript("onBackupChosen()", null);
            } else if (req == 2) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try (InputStream in = getContentResolver().openInputStream(u)) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                }
                String text = bo.toString("UTF-8");
                web.evaluateJavascript("onRestore(" + JSONObject.quote(text) + ")", null);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
