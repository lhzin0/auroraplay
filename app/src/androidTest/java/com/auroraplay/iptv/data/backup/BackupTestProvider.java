package com.auroraplay.iptv.data.backup;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

/** Runs in the test APK's own process, using only Android/Java runtime classes. */
public class BackupTestProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    private File fixture(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || !name.matches("[a-zA-Z0-9_-]+\\.json")) {
            throw new IllegalArgumentException("Invalid fixture name");
        }
        File directory = new File(getContext().getCacheDir(), "backup-fixtures");
        directory.mkdirs();
        return new File(directory, name);
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(fixture(uri), ParcelFileDescriptor.parseMode(mode));
    }
    @Override public String getType(Uri uri) { return "application/json"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return fixture(uri).delete() ? 1 : 0; }
}
