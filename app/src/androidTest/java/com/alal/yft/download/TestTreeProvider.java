package com.alal.yft.download;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

/**
 * v1.0.2 item 28: a real documents provider for {@code ChosenFolderInstrumentedTest}, in the test
 * APK's own process, as a phone's storage is in its own: {@link #ROOT_ID} is the folder (a
 * directory in this APK's files) and "root:YFT/&lt;name&gt;" an entry at its top. Android checks
 * each call the app makes against the access the folder picker gives
 * ({@link TestTreeAccessProvider} gives it here). Like a phone's storage, it adds the type's
 * extension to a new file's name that lacks it, and gives a taken name a " (n)" suffix.
 *
 * <p>Java with Android's own classes only: this process loads the test APK alone, and the test
 * APK leaves out the libraries the app already has, Kotlin's among them. The first version, in
 * Kotlin, stopped this process at its first call (emulator smoke #179).
 */
public final class TestTreeProvider extends DocumentsProvider {
    public static final String ROOT_ID = "root:YFT";

    private static final String PREFIX = ROOT_ID + "/";
    private static final int MAX_SUFFIX = 32;
    private static final String OCTET_STREAM = "application/octet-stream";
    private static final String[] ROOT_COLUMNS = {
        Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS,
    };
    private static final String[] DOCUMENT_COLUMNS = {
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_FLAGS,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
    };

    /** The folder's directory, in the test APK's own files. */
    public static File folder(Context context) {
        return new File(context.getFilesDir(), "tree/YFT");
    }

    /** Deletes {@code file} and all inside it; true when nothing of it is left. */
    static boolean deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        return file.delete() || !file.exists();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : ROOT_COLUMNS);
        cursor.newRow()
            .add(Root.COLUMN_ROOT_ID, "root")
            .add(Root.COLUMN_DOCUMENT_ID, ROOT_ID)
            .add(Root.COLUMN_TITLE, "YFT test tree")
            .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_IS_CHILD);
        return cursor;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection)
        throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : DOCUMENT_COLUMNS);
        addDocument(cursor, documentId, existing(documentId));
        return cursor;
    }

    @Override
    public Cursor queryChildDocuments(
        String parentDocumentId,
        String[] projection,
        String sortOrder
    ) throws FileNotFoundException {
        File folder = existing(parentDocumentId);
        if (!ROOT_ID.equals(parentDocumentId)) {
            throw new FileNotFoundException("Not the folder");
        }
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : DOCUMENT_COLUMNS);
        File[] children = folder.listFiles();
        if (children != null) {
            // One directory, so the paths sort as the names do.
            Arrays.sort(children);
            for (File child : children) {
                addDocument(cursor, PREFIX + child.getName(), child);
            }
        }
        return cursor;
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        return ROOT_ID.equals(parentDocumentId) && documentId.startsWith(PREFIX);
    }

    @Override
    public ParcelFileDescriptor openDocument(
        String documentId,
        String mode,
        CancellationSignal signal
    ) throws FileNotFoundException {
        File file = existing(documentId);
        if (!file.isFile()) {
            throw new FileNotFoundException("Not a file");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName)
        throws FileNotFoundException {
        File parent = existing(parentDocumentId);
        if (!ROOT_ID.equals(parentDocumentId)) {
            throw new FileNotFoundException("Not the folder");
        }
        File file = unique(parent, nameFor(mimeType, displayName));
        boolean made;
        try {
            made = Document.MIME_TYPE_DIR.equals(mimeType) ? file.mkdir() : file.createNewFile();
        } catch (IOException error) {
            throw new IllegalStateException("Cannot create the document", error);
        }
        if (!made) {
            throw new IllegalStateException("Cannot create the document");
        }
        return PREFIX + file.getName();
    }

    @Override
    public String renameDocument(String documentId, String displayName)
        throws FileNotFoundException {
        File file = existing(documentId);
        if (ROOT_ID.equals(documentId)) {
            throw new UnsupportedOperationException("The folder keeps its name");
        }
        File target = unique(file.getParentFile(), displayName);
        if (!file.renameTo(target)) {
            throw new IllegalStateException("Cannot rename the document");
        }
        return PREFIX + target.getName();
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        File file = existing(documentId);
        if (!deleteRecursively(file)) {
            throw new IllegalStateException("Cannot delete the document");
        }
    }

    private File existing(String documentId) throws FileNotFoundException {
        File file = fileOf(documentId);
        if (!file.exists()) {
            throw new FileNotFoundException("Missing document");
        }
        return file;
    }

    private File fileOf(String documentId) throws FileNotFoundException {
        Context context = getContext();
        if (context == null) {
            throw new FileNotFoundException("No context yet");
        }
        File folder = folder(context);
        if (ROOT_ID.equals(documentId)) {
            return folder;
        }
        if (!documentId.startsWith(PREFIX)) {
            throw new FileNotFoundException("Not a document");
        }
        String name = documentId.substring(PREFIX.length());
        boolean plain = !name.trim().isEmpty() && name.indexOf('/') < 0 &&
            !name.equals(".") && !name.equals("..");
        if (!plain) {
            throw new FileNotFoundException("Not a document");
        }
        return new File(folder, name);
    }

    private static void addDocument(MatrixCursor cursor, String documentId, File file) {
        boolean directory = file.isDirectory();
        int flags = directory
            ? Document.FLAG_DIR_SUPPORTS_CREATE | Document.FLAG_SUPPORTS_DELETE
            : Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_DELETE |
                Document.FLAG_SUPPORTS_RENAME;
        Object size = directory ? null : Long.valueOf(file.length());
        cursor.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, documentId)
            .add(Document.COLUMN_DISPLAY_NAME, file.getName())
            .add(Document.COLUMN_MIME_TYPE, mimeTypeOf(file))
            .add(Document.COLUMN_FLAGS, flags)
            .add(Document.COLUMN_SIZE, size)
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
    }

    /** A new file's name, with its type's extension, as a phone's storage gives it. */
    private static String nameFor(String mimeType, String displayName) {
        if (Document.MIME_TYPE_DIR.equals(mimeType) || OCTET_STREAM.equals(mimeType)) {
            return displayName;
        }
        MimeTypeMap types = MimeTypeMap.getSingleton();
        String wanted = types.getExtensionFromMimeType(mimeType);
        if (wanted == null) {
            return displayName;
        }
        String extension = extensionOf(displayName);
        String typeOfName = types.getMimeTypeFromExtension(extension.toLowerCase(Locale.ROOT));
        if (mimeType.equals(typeOfName) || extension.equals(wanted)) {
            return displayName;
        }
        return displayName + "." + wanted;
    }

    /**
     * {@code name} in {@code parent}, or the first free "name (n).ext", as a phone's storage
     * does.
     */
    private static File unique(File parent, String name) {
        File first = new File(parent, name);
        if (!first.exists()) {
            return first;
        }
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int suffix = 1; suffix <= MAX_SUFFIX; suffix++) {
            File candidate = new File(parent, stem + " (" + suffix + ")" + extension);
            if (!candidate.exists()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Too many documents share this name");
    }

    private static String mimeTypeOf(File file) {
        if (file.isDirectory()) {
            return Document.MIME_TYPE_DIR;
        }
        String extension = extensionOf(file.getName()).toLowerCase(Locale.ROOT);
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return type != null ? type : OCTET_STREAM;
    }

    /** What follows the name's last dot, or "" without one. */
    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }
}
