package mega.privacy.android.app.meeting;

import android.view.MotionEvent;
import android.view.View;

public class OnDragTouchListener implements View.OnTouchListener {
    private View mView;
    private View mParent;
    private boolean isDragging;
    private int width;
    private float maxLeft;
    private float maxRight;
    private float dX;
    private int height;
    private float maxTop;
    private float maxBottom;
    private float dY;
    private OnDragActionListener mOnDragActionListener;

    private int toolbarHeight;
    private int bottomSheetHeight;

    public OnDragTouchListener(View view, View parent) {
        this(view, parent, null);
    }

    public OnDragTouchListener(View view, View parent, OnDragActionListener onDragActionListener) {
        initListener(view, parent);
        setOnDragActionListener(onDragActionListener);
    }

    public void setOnDragActionListener(OnDragActionListener onDragActionListener) {
        mOnDragActionListener = onDragActionListener;
    }

    public void initListener(View view, View parent) {
        mView = view;
        mParent = parent;
        isDragging = false;
    }

    public void updateBounds() {
        updateViewBounds();
        updateParentBounds();
    }

    public void updateViewBounds() {
        width = mView.getWidth();
        dX = 0;
        height = mView.getHeight();
        dY = 0;
    }

    public void updateParentBounds() {
        maxLeft = 0;
        maxRight = maxLeft + mParent.getWidth();

        maxTop = toolbarHeight;
        maxBottom = (bottomSheetHeight > 0) ? bottomSheetHeight : mParent.getHeight();
    }

    public void setToolbarHeight(int toolbarHeight) {
        this.toolbarHeight = toolbarHeight;
    }

    public void setBottomSheetHeight(int bottomSheetHeight) {
        this.bottomSheetHeight = bottomSheetHeight;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (isDragging) {
            float[] bounds = new float[4];
            // LEFT
            bounds[0] = event.getRawX() + dX;
            if (bounds[0] < maxLeft) {
                bounds[0] = maxLeft;
            }
            // RIGHT
            bounds[2] = bounds[0] + width;
            if (bounds[2] > maxRight) {
                bounds[2] = maxRight;
                bounds[0] = bounds[2] - width;
            }
            // TOP
            bounds[1] = event.getRawY() + dY;
            if (bounds[1] < maxTop) {
                bounds[1] = maxTop;
            }
            // BOTTOM
            bounds[3] = bounds[1] + height;
            if (bounds[3] > maxBottom) {
                bounds[3] = maxBottom;
                bounds[1] = bounds[3] - height;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    // Sticky
                    float x = bounds[0] > (float) (mParent.getWidth() / 2)
                            ? mParent.getWidth() - mView.getWidth()
                            : 0;

                    mView.animate().x(x).y(bounds[1]).setDuration(0).start();
                    onDragFinish();
                    break;
                case MotionEvent.ACTION_MOVE:
                    mView.animate().x(bounds[0]).y(bounds[1]).setDuration(0).start();

                    break;
            }
            return true;
        } else {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (mOnDragActionListener != null) {
                    mOnDragActionListener.onDragStart(mView);
                }
                isDragging = true;
                updateBounds();
                dX = v.getX() - event.getRawX();
                dY = v.getY() - event.getRawY();
                return true;
            }
        }
        return false;
    }

    private void onDragFinish() {
        if (mOnDragActionListener != null) {
            mOnDragActionListener.onDragEnd(mView);
        }

        dX = 0;
        dY = 0;
        isDragging = false;
    }

    /**
     * Callback used to indicate when the drag is finished
     */
    public interface OnDragActionListener {
        /**
         * Called when drag event is started
         *
         * @param view The view dragged
         */
        void onDragStart(View view);

        /**
         * Called when drag event is completed
         *
         * @param view The view dragged
         */
        void onDragEnd(View view);
    }
}