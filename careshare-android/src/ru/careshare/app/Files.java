package ru.careshare.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Отдаёт файлы из cache/share другим приложениям (экспорт, снимки камеры). */
public class Files extends ContentProvider {
    static final String AUTH = "ru.careshare.app.files";

    static File dir(android.content.Context c) { File d = new File(c.getCacheDir(), "share"); d.mkdirs(); return d; }
    static Uri uri(String name) { return Uri.parse("content://" + AUTH + "/" + name); }

    File file(Uri u) throws FileNotFoundException {
        String n = u.getLastPathSegment();
        if (n == null || n.contains("/") || n.contains("..")) throw new FileNotFoundException();
        return new File(dir(getContext()), n);
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        File f = file(u);
        int m = mode.contains("w") ? (ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE) : ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(f, m);
    }

    @Override
    public String getType(Uri u) {
        String n = u.getLastPathSegment(); if (n == null) return null;
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".jpg")) return "image/jpeg";
        if (n.endsWith(".ics")) return "text/calendar";
        return "application/octet-stream";
    }

    @Override
    public Cursor query(Uri u, String[] proj, String sel, String[] args, String sort) {
        try {
            File f = file(u);
            MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch (Exception e) { return null; }
    }

    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
