package io.github.gasmorais.pdfviewer.view;

import io.github.gasmorais.pdfviewer.document.PdfDocument;

final class PageLayout {

    private final int width;
    private final float[] tops;
    private final float[] heights;
    private final float total;

    PageLayout(PdfDocument document, int width, float topInset, float bottomInset, float spacing) {
        int count = document.pageCount();
        this.width = width;
        this.tops = new float[count];
        this.heights = new float[count];
        float y = topInset + spacing;
        for (int i = 0; i < count; i++) {
            tops[i] = y;
            heights[i] = document.height(i) * (width / document.width(i));
            y += heights[i] + spacing;
        }
        this.total = y + bottomInset;
    }

    int width() {
        return width;
    }

    int count() {
        return tops.length;
    }

    float total() {
        return total;
    }

    float top(int page) {
        return tops[page];
    }

    float bottom(int page) {
        return tops[page] + heights[page];
    }

    float height(int page) {
        return heights[page];
    }

    int firstEndingAfter(float y) {
        int low = 0;
        int high = tops.length - 1;
        int result = tops.length;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (bottom(mid) >= y) {
                result = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return result;
    }

    int pageAt(float y) {
        int low = 0;
        int high = tops.length - 1;
        int result = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (tops[mid] <= y) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }
}
