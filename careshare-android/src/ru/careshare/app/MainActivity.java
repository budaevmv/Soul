package ru.careshare.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.print.PrintManager;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {
    WebView web, printView;
    ValueCallback<Uri[]> fileCb;
    File camFile;
    static final int REQ_FILE = 42;
    static final String HOST = "app.careshare.local", ORIGIN = "https://" + HOST + "/";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#F6F2EE"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String path = u.getPath();
                if (path == null || path.equals("/")) path = "/index.html";
                path = path.substring(1);
                String mime = path.endsWith(".js") ? "application/javascript" : path.endsWith(".css") ? "text/css" : path.endsWith(".png") ? "image/png" : "text/html";
                try { return new WebResourceResponse(mime, "UTF-8", getAssets().open(path)); }
                catch (Exception e) { return new WebResourceResponse("text/plain", "UTF-8", 404, "Not found", null, null); }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url.startsWith(ORIGIN)) return false;
                open(url);
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent pick = p.createIntent();
                boolean image = false;
                for (String t : p.getAcceptTypes()) if (t != null && t.startsWith("image")) image = true;
                Intent chooser = pick;
                camFile = null;
                if (image) {
                    pick = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
                    camFile = new File(Files.dir(MainActivity.this), "cam_" + System.currentTimeMillis() + ".jpg");
                    Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    cam.putExtra(MediaStore.EXTRA_OUTPUT, Files.uri(camFile.getName()));
                    cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    chooser = Intent.createChooser(pick, "Фото");
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
                }
                try { startActivityForResult(chooser, REQ_FILE); }
                catch (Exception e) { fileCb = null; return false; }
                return true;
            }
        });
        setContentView(web);
        if (state != null) web.restoreState(state);
        else web.loadUrl(ORIGIN + "index.html");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req != REQ_FILE || fileCb == null) { super.onActivityResult(req, res, data); return; }
        Uri[] out = null;
        if (res == RESULT_OK) {
            if (data != null && data.getData() != null) out = new Uri[]{data.getData()};
            else if (data != null && data.getClipData() != null && data.getClipData().getItemCount() > 0) out = new Uri[]{data.getClipData().getItemAt(0).getUri()};
            else if (camFile != null && camFile.exists() && camFile.length() > 0) out = new Uri[]{Files.uri(camFile.getName())};
        }
        fileCb.onReceiveValue(out);
        fileCb = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.csResume&&window.csResume()", null);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.csBack&&window.csBack()", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String handled) {
                if (!"true".equals(handled)) MainActivity.super.onBackPressed();
            }
        });
    }

    void open(String url) {
        try {
            Intent i;
            if (url.startsWith("mailto:") || url.startsWith("sms:") || url.startsWith("smsto:")) i = new Intent(Intent.ACTION_SENDTO, Uri.parse(url));
            else if (url.startsWith("tel:")) i = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
            else i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Нет приложения, чтобы открыть это", Toast.LENGTH_SHORT).show();
        }
    }

    void start(Intent i) {
        try { startActivity(i); }
        catch (Exception e) { Toast.makeText(this, "Не удалось открыть", Toast.LENGTH_SHORT).show(); }
    }

    /** Мост JavaScript ↔ Android */
    class Bridge {
        @JavascriptInterface public void setReminders(String json, boolean on) { Rx.save(MainActivity.this, json, on); }
        @JavascriptInterface public void markDose(String date, String med, String time, boolean on) { Rx.markDose(MainActivity.this, date, med, time, on); }
        @JavascriptInterface public String takePending() { return Rx.takePending(MainActivity.this); }

        @JavascriptInterface public void openUrl(final String url) {
            runOnUiThread(new Runnable() { public void run() { open(url); } });
        }

        @JavascriptInterface public void share(String title, String text) {
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TEXT, text);
            start(Intent.createChooser(i, title));
        }

        @JavascriptInterface public void shareFile(String name, String content) {
            try {
                File f = new File(Files.dir(MainActivity.this), name.replaceAll("[^A-Za-z0-9._-]", "_"));
                FileOutputStream o = new FileOutputStream(f);
                o.write(content.getBytes("UTF-8"));
                o.close();
                Intent i = new Intent(Intent.ACTION_SEND).setType("application/json")
                        .putExtra(Intent.EXTRA_STREAM, Files.uri(f.getName()))
                        .putExtra(Intent.EXTRA_SUBJECT, "Данные CareShare")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                start(Intent.createChooser(i, "Отправить данные CareShare"));
            } catch (Exception e) {
                runOnUiThread(new Runnable() { public void run() { Toast.makeText(MainActivity.this, "Не удалось сохранить файл", Toast.LENGTH_SHORT).show(); } });
            }
        }

        @JavascriptInterface public void addToCalendar(String title, String loc, String desc, String start, String end) {
            Intent i = new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, Long.parseLong(start))
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, Long.parseLong(end))
                    .putExtra(CalendarContract.Events.TITLE, title)
                    .putExtra(CalendarContract.Events.EVENT_LOCATION, loc)
                    .putExtra(CalendarContract.Events.DESCRIPTION, desc);
            start(i);
        }

        @JavascriptInterface public void printHtml(final String html) {
            runOnUiThread(new Runnable() {
                public void run() {
                    printView = new WebView(MainActivity.this);
                    printView.setWebViewClient(new WebViewClient() {
                        @Override public void onPageFinished(WebView v, String url) {
                            PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                            pm.print("CareShare — экстренный лист", v.createPrintDocumentAdapter("CareShare"), null);
                        }
                    });
                    printView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
                }
            });
        }

        @JavascriptInterface public void sms(String phone, String body) {
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phone)).putExtra("sms_body", body);
            start(i);
        }

        @JavascriptInterface public void email(String to, String subject, String body) {
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                    .putExtra(Intent.EXTRA_EMAIL, new String[]{to}).putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body);
            start(i);
        }

        @JavascriptInterface public void testNotify() {
            new Handler(getMainLooper()).postDelayed(new Runnable() {
                public void run() { Rx.show(MainActivity.this, "test", "💊 Проверка напоминаний", "Так будут выглядеть напоминания CareShare.", null); }
            }, 5000);
        }

        @JavascriptInterface public String notifState() {
            if (Build.VERSION.SDK_INT >= 33) return checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED ? "granted" : "denied";
            return "granted";
        }

        @JavascriptInterface public void requestNotif() {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(new Runnable() { public void run() { requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7); } });
            }
        }

        @JavascriptInterface public boolean exactAllowed() { return Rx.exactAllowed(MainActivity.this); }

        @JavascriptInterface public void openExactSettings() {
            start(new Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM", Uri.parse("package:" + getPackageName())));
        }

        @JavascriptInterface public void openBatterySettings() {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
            try { startActivity(i); }
            catch (Exception e) { start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (web != null) web.evaluateJavascript("window.csResume&&window.csResume()", null);
    }
}
