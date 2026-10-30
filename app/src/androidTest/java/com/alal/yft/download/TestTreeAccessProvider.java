package com.alal.yft.download;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import java.io.File;

/**
 * v1.0.2 item 28: the folder picker's part for {@code ChosenFolderInstrumentedTest}. It runs in
 * the process of {@link TestTreeProvider}, whose documents only this APK may hand out, and it
 * gives the app the same lasting read and write access to the folder as the picker does
 * ({@link #GRANT}). It also takes that access back ({@link #REVOKE}), deletes the folder
 * ({@link #REMOVE}) and empties it ({@link #RESET}). Each call answers the folder's tree
 * address under {@link #TREE}. Java with Android's own classes only, as
 * {@link TestTreeProvider} explains.
 */
public final class TestTreeAccessProvider extends ContentProvider {
    /** Gives the package in the call's argument the picker's access to the folder. */
    public static final String GRANT = "grant";
    public static final String REVOKE = "revoke";
    public static final String REMOVE = "remove";
    public static final String RESET = "reset";
    public static final String TREE = "tree";

    private static final int ACCESS =
        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    /** What the folder picker gives for a picked folder. */
    private static final int PICKER_ACCESS = ACCESS |
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        if (context == null) {
            throw new IllegalStateException("No context yet");
        }
        Uri tree = DocumentsContract.buildTreeDocumentUri(
            context.getPackageName() + ".tree",
            TestTreeProvider.ROOT_ID
        );
        File folder = TestTreeProvider.folder(context);
        switch (method) {
            case GRANT:
                if (arg == null) {
                    throw new IllegalArgumentException("Which package?");
                }
                context.grantUriPermission(arg, tree, PICKER_ACCESS);
                break;
            case REVOKE:
                context.revokeUriPermission(tree, ACCESS);
                break;
            case REMOVE:
                TestTreeProvider.deleteRecursively(folder);
                break;
            case RESET:
                TestTreeProvider.deleteRecursively(folder);
                if (!folder.mkdirs()) {
                    throw new IllegalStateException("Cannot make the folder");
                }
                break;
            default:
                return super.call(method, arg, extras);
        }
        Bundle answer = new Bundle();
        answer.putString(TREE, tree.toString());
        return answer;
    }

    @Override
    public Cursor query(
        Uri uri,
        String[] projection,
        String selection,
        String[] selectionArgs,
        String sortOrder
    ) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }
}
