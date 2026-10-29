package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/** One explicit figure gesture mode; temporary transforms never enter page history. */
final class NotebookFigureEditor {
    interface Listener { void complete(NotebookFigure figure, boolean delete); }
    private final View anchor;
    private final PopupWindow popup;
    private final Listener listener;
    private NotebookFigure figure;
    private boolean completed;
    private NotebookFigureLayer images;
    NotebookFigureEditor(Context context, View anchor, NotebookPage page, int index, Bitmap background, Listener listener) {
        this.anchor = anchor; this.listener = listener; figure = page.figures.get(index);
        LinearLayout root = new LinearLayout(context); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        TextView hint = new TextView(context); hint.setText("Drag figure to move · drag lower-right corner to resize");
        hint.setTextColor(Color.BLACK); hint.setTextSize(13); hint.setGravity(Gravity.CENTER); root.addView(hint);
        View editor = new View(context) {
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            final RectF box = new RectF();
            float scale, left, top, downX, downY;
            NotebookFigure start;
            boolean resizing, moving;
            void geometry() {
                scale = Math.min(getWidth() / (float) page.width, getHeight() / (float) page.height);
                left = (getWidth() - page.width * scale) / 2; top = (getHeight() - page.height * scale) / 2;
                box.set(left + figure.x * scale, top + figure.y * scale,
                    left + (figure.x + figure.width) * scale, top + (figure.y + figure.height) * scale);
            }
            @Override protected void onDraw(Canvas canvas) {
                geometry(); paint.setStyle(Paint.Style.FILL);
                canvas.drawBitmap(background, null, new RectF(left, top, left + page.width * scale, top + page.height * scale), paint);
                images.prepare(java.util.Collections.singletonList(new NotebookFigureLayer.Request(figure, figure.width * (double) scale, figure.height * (double) scale)));
                int saved = canvas.save(); canvas.translate(left, top); canvas.scale(scale, scale);
                images.draw(canvas, figure); canvas.restoreToCount(saved);
                paint.setColor(Color.BLACK); paint.setStrokeWidth(2); paint.setStyle(Paint.Style.STROKE); canvas.drawRect(box, paint);
                paint.setStyle(Paint.Style.FILL); canvas.drawCircle(box.right, box.bottom, 10 * getResources().getDisplayMetrics().density, paint);
            }
            @Override public boolean onTouchEvent(MotionEvent event) {
                geometry(); float x = event.getX(), y = event.getY();
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    float reach = 28 * getResources().getDisplayMetrics().density;
                    resizing = Math.abs(x - box.right) < reach && Math.abs(y - box.bottom) < reach;
                    moving = resizing || box.contains(x, y); start = figure; downX = x; downY = y;
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE && moving && scale > 0) {
                    if (resizing) {
                        float max = Math.min((page.width - start.x) / start.width, (page.height - start.y) / start.height);
                        float factor = Math.max(Math.min(.02f, max), Math.min(max, 1 + (x - downX) / (scale * start.width)));
                        figure = start.placed(start.x, start.y, start.width * factor, start.height * factor);
                    } else figure = start.placed(Math.max(0, Math.min(page.width - start.width, start.x + (x - downX) / scale)),
                        Math.max(0, Math.min(page.height - start.height, start.y + (y - downY) / scale)), start.width, start.height);
                    invalidate(); return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL && start != null) { figure = start; invalidate(); }
                if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) moving = false;
                return true;
            }
        };
        images = new NotebookFigureLayer(new NotebookFigureLayer.Listener() {
            public void changed() { editor.invalidate(); }
            public void failed(java.io.IOException error) { editor.invalidate(); }
        });
        editor.setContentDescription("Figure placement: drag to move, lower-right handle to resize");
        root.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout actions = new LinearLayout(context); root.addView(actions);
        String[] labels = {"Cancel", "Delete", "Apply"};
        for (int i = 0; i < labels.length; i++) {
            final int choice = i; NotebookButton button = new NotebookButton(context); button.setText(labels[i]);
            button.setOnClickListener(v -> finish(choice == 2 ? figure : null, choice == 1));
            actions.addView(button, new LinearLayout.LayoutParams(0, Math.round(44 * context.getResources().getDisplayMetrics().density), 1));
        }
        popup = new PopupWindow(root, anchor.getWidth(), anchor.getHeight(), true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.WHITE));
        popup.setClippingEnabled(false); popup.setIsLaidOutInScreen(true); popup.setAnimationStyle(0);
        popup.setOnDismissListener(() -> { if (!completed) finish(null, false); });
    }
    void show() {
        int[] location = new int[2]; anchor.getLocationOnScreen(location);
        if (!NotebookPopupHost.show(popup, anchor, Gravity.TOP | Gravity.LEFT, location[0], location[1])) finish(null, false);
    }
    void cancel() { finish(null, false); }
    void dismiss() { completed = true; images.close(); popup.dismiss(); }
    private void finish(NotebookFigure figure, boolean delete) {
        if (completed) return;
        completed = true; images.close(); popup.dismiss(); listener.complete(figure, delete);
    }
}
