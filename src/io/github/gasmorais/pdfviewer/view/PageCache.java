package io.github.gasmorais.pdfviewer.view;

import android.graphics.Bitmap;
import android.util.LruCache;
import android.util.SparseBooleanArray;

final class PageCache {

    private final LruCache<Integer, Bitmap> pages;
    private final SparseBooleanArray pending = new SparseBooleanArray();
    private int version;

    PageCache(int maxBytes, BitmapPool pool) {
        pages = new LruCache<Integer, Bitmap>(maxBytes) {
            @Override
            protected int sizeOf(Integer page, Bitmap bitmap) {
                return bitmap.getAllocationByteCount();
            }

            @Override
            protected void entryRemoved(boolean evicted, Integer page, Bitmap oldValue, Bitmap newValue) {
                if (oldValue != newValue) {
                    pool.release(oldValue);
                }
            }
        };
    }

    int version() {
        return version;
    }

    Bitmap get(int page) {
        return pages.get(page);
    }

    boolean reserve(int page) {
        if (pending.get(page) || pages.get(page) != null) {
            return false;
        }
        pending.put(page, true);
        return true;
    }

    boolean store(int requestVersion, int page, Bitmap bitmap) {
        if (requestVersion != version) {
            return false;
        }
        pending.delete(page);
        pages.put(page, bitmap);
        return true;
    }

    void cancel(int requestVersion, int page) {
        if (requestVersion == version) {
            pending.delete(page);
        }
    }

    void invalidate() {
        version++;
        pending.clear();
        pages.evictAll();
    }
}
