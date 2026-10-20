package ms.sreeram.attendance;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;

public class Main extends Activity {
    WebView web;
    volatile String pending, exportJson;
    static final int RQ_EXPORT = 11, RQ_IMPORT = 12;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        pending = getIntent().getStringExtra("open");
        web = new WebView(this);
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        web.setBackgroundColor(night ? 0xFF0E161C : 0xFFEEF2F5);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return !url.startsWith("file:"); }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
        Alarm.schedule(this);
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        String o = i.getStringExtra("open");
        if (o != null && web != null) web.evaluateJavascript("window.__open && window.__open(" + JSONObject.quote(o) + ")", null);
    }

    @Override public void onBackPressed() {
        web.evaluateJavascript("window.__back ? window.__back() : false", v -> { if (!"true".equals(v)) finish(); });
    }

    @Override protected void onActivityResult(int rq, int rs, Intent data) {
        super.onActivityResult(rq, rs, data);
        if (rs != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        try {
            if (rq == RQ_EXPORT && exportJson != null) {
                try (OutputStream os = getContentResolver().openOutputStream(u)) { os.write(exportJson.getBytes("UTF-8")); }
                Toast.makeText(this, "Backup saved", Toast.LENGTH_SHORT).show();
            } else if (rq == RQ_IMPORT) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(u), "UTF-8"))) {
                    String line; while ((line = r.readLine()) != null) sb.append(line).append('\n');
                }
                web.evaluateJavascript("window.__import(" + JSONObject.quote(sb.toString()) + ")", null);
            }
        } catch (Exception e) { Toast.makeText(this, "Could not read or write that file", Toast.LENGTH_SHORT).show(); }
    }

    class Bridge {
        @JavascriptInterface public void sync(String days, String set) {
            Store.p(Main.this).edit().putString("days", days).putString("set", set).apply();
            Alarm.schedule(Main.this);
            CalWidget.refreshAll(Main.this);
        }
        @JavascriptInterface public String pending() { String p = pending; pending = null; return p; }
        @JavascriptInterface public void askNotifPermission() {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1));
        }
        @JavascriptInterface public void testNotification() {
            askNotifPermission();
            Alarm.notify(Main.this, "Attendance reminders are on", "You'll get a daily nudge and a weekly summary at the times you set.", 99);
        }
        @JavascriptInterface public void exportData(String json) {
            exportJson = json;
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "attendance-backup.json");
                startActivityForResult(i, RQ_EXPORT);
            });
        }
        @JavascriptInterface public void importData() {
            runOnUiThread(() -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), RQ_IMPORT));
        }
    }
}
