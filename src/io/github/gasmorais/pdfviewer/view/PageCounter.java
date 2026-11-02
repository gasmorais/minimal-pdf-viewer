package io.github.gasmorais.pdfviewer.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import io.github.gasmorais.pdfviewer.R;

final class PageCounter {

    private final Resources resources;
    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF area = new RectF();
    private final float height;
    private final float padding;
    private final float margin;
    private final float baselineOffset;

    private int currentPage = -1;
    private int currentTotal = -1;
    private String label = "";
    private float labelWidth;

    PageCounter(Context context) {
        resources = context.getResources();
        height = resources.getDimension(R.dimen.counter_height);
        padding = resources.getDimension(R.dimen.counter_padding);
        margin = resources.getDimension(R.dimen.counter_margin);
        background.setColor(context.getColor(R.color.counter_background));
        text.setColor(context.getColor(R.color.counter_text));
        text.setTextSize(resources.getDimension(R.dimen.counter_text_size));
        text.setTextAlign(Paint.Align.CENTER);
        baselineOffset = -(text.ascent() + text.descent()) / 2f;
    }

    void draw(Canvas canvas, int page, int total, float centerX, float bottomLimit) {
        if (page != currentPage || total != currentTotal) {
            currentPage = page;
            currentTotal = total;
            label = resources.getString(R.string.page_counter, page + 1, total);
            labelWidth = text.measureText(label) + 2 * padding;
        }
        float centerY = bottomLimit - margin - height / 2f;
        area.set(centerX - labelWidth / 2f, centerY - height / 2f,
                centerX + labelWidth / 2f, centerY + height / 2f);
        canvas.drawRoundRect(area, height / 2f, height / 2f, background);
        canvas.drawText(label, centerX, centerY + baselineOffset, text);
    }
}
