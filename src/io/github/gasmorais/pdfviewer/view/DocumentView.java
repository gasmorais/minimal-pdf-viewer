package io.github.gasmorais.pdfviewer.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowInsets;
import android.widget.OverScroller;

import io.github.gasmorais.pdfviewer.R;
import io.github.gasmorais.pdfviewer.document.PdfDocument;

public final class DocumentView extends View {

    private static final float MIN_ZOOM = 1f;
    private static final float MAX_ZOOM = 6f;
    private static final float DOUBLE_TAP_ZOOM = 2.5f;
    private static final float DETAIL_THRESHOLD = 1.05f;
    private static final long DETAIL_DELAY_MS = 120;
    private static final long COUNTER_DURATION_MS = 1500;
    private static final int PREFETCH_MARGIN = 2;
    private static final int POOL_CAPACITY = 6;
    private static final int CACHE_MEMORY_FRACTION = 4;

    private final PdfDocument document;
    private final BitmapPool pool = new BitmapPool(POOL_CAPACITY);
    private final PageCache cache;
    private final SparseArray<DetailTile> details = new SparseArray<>();
    private final PageCounter counter;
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private final OverScroller scroller;
    private final Paint pagePaint = new Paint();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF pageArea = new RectF();
    private final int backgroundColor;
    private final float spacing;
    private final Runnable hideCounter = this::hideCounter;
    private final Runnable renderDetails = this::renderDetails;

    private PageLayout layout;
    private int topInset;
    private int bottomInset;
    private float zoom = MIN_ZOOM;
    private float offsetX;
    private float offsetY;
    private boolean flinging;
    private boolean counterVisible;
    private volatile int firstVisible;
    private volatile int lastVisible;
    private volatile int detailGeneration;

    public DocumentView(Context context, PdfDocument document) {
        super(context);
        this.document = document;
        int cacheBytes = (int) (Runtime.getRuntime().maxMemory() / CACHE_MEMORY_FRACTION);
        cache = new PageCache(cacheBytes, pool);
        counter = new PageCounter(context);
        scroller = new OverScroller(context);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
        backgroundColor = context.getColor(R.color.background);
        pagePaint.setColor(context.getColor(R.color.page));
        spacing = getResources().getDimension(R.dimen.page_spacing);
    }

    public void releaseMemory() {
        cache.invalidate();
        discardDetails();
        pool.clear();
        invalidate();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
        if (bars.top != topInset || bars.bottom != bottomInset) {
            topInset = bars.top;
            bottomInset = bars.bottom;
            rebuildLayout();
            invalidate();
        }
        return insets;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        float fraction = layout == null ? 0f : (offsetY + oldHeight / 2f) / (layout.total() * zoom);
        rebuildLayout();
        if (layout != null) {
            offsetY = fraction * layout.total() * zoom - height / 2f;
            clampOffsets();
        }
        showCounter();
        scheduleDetails();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(hideCounter);
        removeCallbacks(renderDetails);
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (!scaleDetector.isInProgress()) {
            gestureDetector.onTouchEvent(event);
        }
        int action = event.getActionMasked();
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && !flinging) {
            scheduleDetails();
        }
        return true;
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            offsetX = scroller.getCurrX();
            offsetY = scroller.getCurrY();
            clampOffsets();
            postInvalidateOnAnimation();
        } else if (flinging) {
            flinging = false;
            scheduleDetails();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(backgroundColor);
        if (layout == null || layout.count() == 0) {
            return;
        }
        int first = layout.firstEndingAfter(offsetY / zoom);
        int last = Math.min(layout.pageAt((offsetY + getHeight()) / zoom), layout.count() - 1);
        firstVisible = first;
        lastVisible = last;

        canvas.save();
        canvas.translate(-offsetX, -offsetY);
        canvas.scale(zoom, zoom);
        for (int i = first; i <= last; i++) {
            drawPage(canvas, i);
        }
        for (int i = 0, n = details.size(); i < n; i++) {
            DetailTile tile = details.valueAt(i);
            if (tile.zoom == zoom) {
                canvas.drawBitmap(tile.bitmap, null, tile.area, bitmapPaint);
            }
        }
        canvas.restore();

        if (first > 0) {
            requestPage(first - 1);
        }
        if (last < layout.count() - 1) {
            requestPage(last + 1);
        }
        if (counterVisible) {
            int current = layout.pageAt((offsetY + getHeight() / 2f) / zoom);
            counter.draw(canvas, current, layout.count(), getWidth() / 2f, getHeight() - bottomInset);
        }
    }

    private void drawPage(Canvas canvas, int page) {
        pageArea.set(0, layout.top(page), layout.width(), layout.bottom(page));
        Bitmap bitmap = cache.get(page);
        if (bitmap == null) {
            canvas.drawRect(pageArea, pagePaint);
            requestPage(page);
        } else {
            canvas.drawBitmap(bitmap, null, pageArea, bitmapPaint);
        }
    }

    private void rebuildLayout() {
        if (getWidth() == 0) {
            return;
        }
        PageLayout next = new PageLayout(document, getWidth(), topInset, bottomInset, spacing);
        if (layout == null || layout.width() != next.width()) {
            cache.invalidate();
            discardDetails();
            pool.clear();
        }
        layout = next;
        clampOffsets();
    }

    private void requestPage(int page) {
        if (!cache.reserve(page)) {
            return;
        }
        int version = cache.version();
        int height = Math.max(1, Math.round(layout.height(page)));
        Bitmap target = pool.acquire(layout.width(), height);
        document.render(page, target, null,
                () -> page >= firstVisible - PREFETCH_MARGIN && page <= lastVisible + PREFETCH_MARGIN,
                bitmap -> {
                    if (cache.store(version, page, bitmap)) {
                        invalidate();
                    } else {
                        pool.release(bitmap);
                    }
                },
                bitmap -> {
                    cache.cancel(version, page);
                    pool.release(bitmap);
                });
    }

    private void renderDetails() {
        int generation = ++detailGeneration;
        if (layout == null || layout.count() == 0) {
            return;
        }
        if (zoom <= DETAIL_THRESHOLD) {
            discardDetails();
            invalidate();
            return;
        }
        float scale = zoom;
        float left = offsetX / scale;
        float top = offsetY / scale;
        float right = (offsetX + getWidth()) / scale;
        float bottom = (offsetY + getHeight()) / scale;
        int first = layout.firstEndingAfter(top);
        int last = Math.min(layout.pageAt(bottom), layout.count() - 1);
        discardDetailsOutside(first, last, scale);

        for (int page = first; page <= last; page++) {
            RectF area = new RectF(0, layout.top(page), layout.width(), layout.bottom(page));
            if (!area.intersect(left, top, right, bottom)) {
                continue;
            }
            int width = Math.round(area.width() * scale);
            int height = Math.round(area.height() * scale);
            if (width < 1 || height < 1) {
                continue;
            }
            float pageScale = layout.width() / document.width(page) * scale;
            Matrix transform = new Matrix();
            transform.setScale(pageScale, pageScale);
            transform.postTranslate(-area.left * scale, -(area.top - layout.top(page)) * scale);
            int target = page;
            document.render(page, pool.acquire(width, height), transform,
                    () -> generation == detailGeneration,
                    bitmap -> applyDetail(generation, target, new DetailTile(scale, area, bitmap)),
                    pool::release);
        }
    }

    private void applyDetail(int generation, int page, DetailTile tile) {
        if (generation != detailGeneration) {
            pool.release(tile.bitmap);
            return;
        }
        DetailTile previous = details.get(page);
        if (previous != null) {
            pool.release(previous.bitmap);
        }
        details.put(page, tile);
        invalidate();
    }

    private void discardDetailsOutside(int first, int last, float currentZoom) {
        for (int i = details.size() - 1; i >= 0; i--) {
            int page = details.keyAt(i);
            DetailTile tile = details.valueAt(i);
            if (page < first || page > last || tile.zoom != currentZoom) {
                pool.release(tile.bitmap);
                details.removeAt(i);
            }
        }
    }

    private void discardDetails() {
        for (int i = 0, n = details.size(); i < n; i++) {
            pool.release(details.valueAt(i).bitmap);
        }
        details.clear();
    }

    private void zoomTo(float target, float focusX, float focusY) {
        float clamped = Math.max(MIN_ZOOM, Math.min(target, MAX_ZOOM));
        float factor = clamped / zoom;
        offsetX = (offsetX + focusX) * factor - focusX;
        offsetY = (offsetY + focusY) * factor - focusY;
        zoom = clamped;
        clampOffsets();
        showCounter();
        invalidate();
    }

    private void clampOffsets() {
        offsetX = Math.max(0, Math.min(offsetX, maxOffsetX()));
        offsetY = Math.max(0, Math.min(offsetY, maxOffsetY()));
    }

    private int maxOffsetX() {
        return (int) Math.max(0, getWidth() * zoom - getWidth());
    }

    private int maxOffsetY() {
        return layout == null ? 0 : (int) Math.max(0, layout.total() * zoom - getHeight());
    }

    private void showCounter() {
        counterVisible = true;
        removeCallbacks(hideCounter);
        postDelayed(hideCounter, COUNTER_DURATION_MS);
    }

    private void hideCounter() {
        counterVisible = false;
        invalidate();
    }

    private void scheduleDetails() {
        removeCallbacks(renderDetails);
        postDelayed(renderDetails, DETAIL_DELAY_MS);
    }

    private final class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            zoomTo(zoom * detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
            return true;
        }
    }

    private final class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent event) {
            scroller.forceFinished(true);
            flinging = false;
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent start, MotionEvent current, float distanceX, float distanceY) {
            offsetX += distanceX;
            offsetY += distanceY;
            clampOffsets();
            showCounter();
            invalidate();
            return true;
        }

        @Override
        public boolean onFling(MotionEvent start, MotionEvent current, float velocityX, float velocityY) {
            scroller.fling((int) offsetX, (int) offsetY, (int) -velocityX, (int) -velocityY,
                    0, maxOffsetX(), 0, maxOffsetY());
            flinging = true;
            postInvalidateOnAnimation();
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent event) {
            zoomTo(zoom > (MIN_ZOOM + DOUBLE_TAP_ZOOM) / 2f ? MIN_ZOOM : DOUBLE_TAP_ZOOM,
                    event.getX(), event.getY());
            scheduleDetails();
            return true;
        }
    }
}
