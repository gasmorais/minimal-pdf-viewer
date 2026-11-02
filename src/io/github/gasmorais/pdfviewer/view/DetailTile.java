package io.github.gasmorais.pdfviewer.view;

import android.graphics.Bitmap;
import android.graphics.RectF;

final class DetailTile {

    final float zoom;
    final RectF area;
    final Bitmap bitmap;

    DetailTile(float zoom, RectF area, Bitmap bitmap) {
        this.zoom = zoom;
        this.area = area;
        this.bitmap = bitmap;
    }
}
