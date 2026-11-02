package io.github.gasmorais.pdfviewer.document;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class PdfDocument {

    public interface Listener {
        void onOpened(PdfDocument document);

        void onFailed(Failure failure);
    }

    private final ExecutorService queue;
    private final Handler main;
    private final ParcelFileDescriptor descriptor;
    private final PdfRenderer renderer;
    private final float[] widths;
    private final float[] heights;
    private volatile boolean closed;

    private PdfDocument(ExecutorService queue, Handler main, ParcelFileDescriptor descriptor,
                        PdfRenderer renderer, float[] widths, float[] heights) {
        this.queue = queue;
        this.main = main;
        this.descriptor = descriptor;
        this.renderer = renderer;
        this.widths = widths;
        this.heights = heights;
    }

    public static void open(File file, Listener listener) {
        ExecutorService queue = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());
        queue.execute(() -> {
            ParcelFileDescriptor descriptor = null;
            PdfRenderer renderer = null;
            try {
                descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                renderer = new PdfRenderer(descriptor);
                int count = renderer.getPageCount();
                float[] widths = new float[count];
                float[] heights = new float[count];
                for (int i = 0; i < count; i++) {
                    try (PdfRenderer.Page page = renderer.openPage(i)) {
                        widths[i] = page.getWidth();
                        heights[i] = page.getHeight();
                    }
                }
                PdfDocument document = new PdfDocument(queue, main, descriptor, renderer, widths, heights);
                main.post(() -> listener.onOpened(document));
            } catch (SecurityException e) {
                fail(queue, main, descriptor, renderer, listener, Failure.PROTECTED);
            } catch (IOException | RuntimeException e) {
                fail(queue, main, descriptor, renderer, listener, Failure.INVALID);
            }
        });
    }

    public int pageCount() {
        return widths.length;
    }

    public float width(int page) {
        return widths[page];
    }

    public float height(int page) {
        return heights[page];
    }

    public void render(int page, Bitmap target, Matrix transform, BooleanSupplier stillNeeded,
                       Consumer<Bitmap> onRendered, Consumer<Bitmap> onSkipped) {
        try {
            queue.execute(() -> {
                if (closed || !stillNeeded.getAsBoolean()) {
                    main.post(() -> onSkipped.accept(target));
                    return;
                }
                try (PdfRenderer.Page source = renderer.openPage(page)) {
                    target.eraseColor(Color.WHITE);
                    source.render(target, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    main.post(() -> onRendered.accept(target));
                } catch (IllegalStateException e) {
                    main.post(() -> onSkipped.accept(target));
                }
            });
        } catch (RejectedExecutionException e) {
            onSkipped.accept(target);
        }
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        queue.execute(() -> {
            renderer.close();
            closeQuietly(descriptor);
        });
        queue.shutdown();
    }

    private static void fail(ExecutorService queue, Handler main, ParcelFileDescriptor descriptor,
                             PdfRenderer renderer, Listener listener, Failure failure) {
        if (renderer != null) {
            renderer.close();
        }
        closeQuietly(descriptor);
        queue.shutdown();
        main.post(() -> listener.onFailed(failure));
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        try {
            descriptor.close();
        } catch (IOException ignored) {
        }
    }
}
