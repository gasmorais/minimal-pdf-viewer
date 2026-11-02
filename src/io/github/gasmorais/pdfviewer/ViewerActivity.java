package io.github.gasmorais.pdfviewer;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.gasmorais.pdfviewer.document.Failure;
import io.github.gasmorais.pdfviewer.document.PdfDocument;
import io.github.gasmorais.pdfviewer.file.TempFile;
import io.github.gasmorais.pdfviewer.view.DocumentView;

public final class ViewerActivity extends Activity {

    private static final String DOCUMENTS_DIRECTORY = "documents";

    private final ExecutorService copier = Executors.newSingleThreadExecutor();
    private PdfDocument document;
    private DocumentView documentView;
    private int currentRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        open(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        open(intent);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (documentView != null && level >= TRIM_MEMORY_UI_HIDDEN) {
            documentView.releaseMemory();
        }
    }

    @Override
    protected void onDestroy() {
        closeDocument();
        copier.shutdownNow();
        if (isFinishing()) {
            TempFile.clear(documentsDirectory());
        }
        super.onDestroy();
    }

    private void open(Intent intent) {
        Uri source = intent == null ? null : intent.getData();
        if (source == null) {
            showMessage(R.string.error_no_file);
            return;
        }
        int request = ++currentRequest;
        showMessage(R.string.opening);
        ContentResolver resolver = getContentResolver();
        File directory = documentsDirectory();
        copier.execute(() -> {
            try {
                File file = TempFile.copy(resolver, source, directory);
                runOnUiThread(() -> load(request, file));
            } catch (IOException | RuntimeException e) {
                runOnUiThread(() -> fail(request, R.string.error_read));
            }
        });
    }

    private void load(int request, File file) {
        if (!isCurrent(request)) {
            return;
        }
        PdfDocument.open(file, new PdfDocument.Listener() {
            @Override
            public void onOpened(PdfDocument opened) {
                if (isCurrent(request)) {
                    show(opened);
                } else {
                    opened.close();
                }
            }

            @Override
            public void onFailed(Failure failure) {
                fail(request, failure == Failure.PROTECTED ? R.string.error_protected : R.string.error_invalid);
            }
        });
    }

    private void show(PdfDocument opened) {
        closeDocument();
        document = opened;
        documentView = new DocumentView(this, opened);
        setContentView(documentView);
    }

    private void fail(int request, int message) {
        if (isCurrent(request)) {
            showMessage(message);
        }
    }

    private void showMessage(int message) {
        closeDocument();
        TextView text = new TextView(this);
        text.setText(message);
        text.setTextColor(getColor(R.color.message_text));
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.message_text_size));
        text.setGravity(Gravity.CENTER);
        setContentView(text);
    }

    private void closeDocument() {
        if (document != null) {
            document.close();
            document = null;
        }
        documentView = null;
    }

    private boolean isCurrent(int request) {
        return request == currentRequest && !isDestroyed();
    }

    private File documentsDirectory() {
        return new File(getCacheDir(), DOCUMENTS_DIRECTORY);
    }
}
