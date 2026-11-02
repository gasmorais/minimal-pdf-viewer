package io.github.gasmorais.pdfviewer.view;

import android.graphics.Bitmap;

import java.util.ArrayDeque;
import java.util.Iterator;

final class BitmapPool {

    private final int capacity;
    private final ArrayDeque<Bitmap> free;

    BitmapPool(int capacity) {
        this.capacity = capacity;
        this.free = new ArrayDeque<>(capacity);
    }

    Bitmap acquire(int width, int height) {
        Iterator<Bitmap> iterator = free.iterator();
        while (iterator.hasNext()) {
            Bitmap candidate = iterator.next();
            if (candidate.getWidth() == width && candidate.getHeight() == height) {
                iterator.remove();
                return candidate;
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }

    void release(Bitmap bitmap) {
        if (free.size() >= capacity) {
            free.pollFirst();
        }
        free.addLast(bitmap);
    }

    void clear() {
        free.clear();
    }
}
