package mega.privacy.android.app.components;


import android.content.Context;
import android.content.res.TypedArray;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.ViewGroup;

public class NewGridRecyclerView extends RecyclerView {
    
    private CustomizedGridLayoutManager manager;
    private LinearLayoutManager mLinearLayoutManager;

    public int columnWidth = -1;
    private boolean isWrapContent = false;
    private int widthTotal = 0;
    private int spanCount = 2;

    public NewGridRecyclerView(Context context) {
        super(context);
        init(context, null);
    }
    
    public NewGridRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }
    
    public NewGridRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }
    
    private void init(Context context, AttributeSet attrs) {
        if (attrs != null) {
            int[] attrsArray = {
                    android.R.attr.columnWidth
            };
            TypedArray array = context.obtainStyledAttributes(attrs, attrsArray);
            columnWidth = array.getDimensionPixelSize(0, -1);
            array.recycle();
        }
        
        manager = new CustomizedGridLayoutManager(getContext(), 1);
        setLayoutManager(manager);
        calculateSpanCount();
    }
    
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);

        if(!isWrapContent){
            if (columnWidth > 0) {
                calculateSpanCount();
            }
        }
        else{
            ViewGroup.LayoutParams params = getLayoutParams();
            if (columnWidth > 0) {
                calculateSpanCount();
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                setLayoutParams(params);
            }
        }
    }
    
    public void calculateSpanCount() {
        spanCount = Math.max(2, getScreenX() / columnWidth);
        manager.setSpanCount(spanCount);
    }
    
    private int getScreenX() {
        return getResources().getDisplayMetrics().widthPixels;
    }
    
    public int getSpanCount() {
        return spanCount;
    }
    
    public void setWrapContent(){
        isWrapContent = true;
        invalidate();
    }
    
    public int findFirstCompletelyVisibleItemPosition() {
        LinearLayoutManager layoutManager = (LinearLayoutManager)getLayoutManager();
        if (layoutManager == null) return RecyclerView.NO_POSITION;
        return layoutManager.findFirstCompletelyVisibleItemPosition();
    }
    
    public int findFirstVisibleItemPosition() {
        LinearLayoutManager layoutManager = (LinearLayoutManager)getLayoutManager();
        if (layoutManager == null) return RecyclerView.NO_POSITION;
        return layoutManager.findFirstVisibleItemPosition();
    }

    /**
     * Empower the RecyclerView to change to Linear Layout as needed
     */
    public void switchToLinear() {
        mLinearLayoutManager = new LinearLayoutManager(getContext());
        setLayoutManager(mLinearLayoutManager);
    }

    /**
     * Turn back to use the well-configured CustomizedGridLayoutManager
     */
    public void switchBackToGrid() {
        mLinearLayoutManager = null;
        setLayoutManager(manager);
        calculateSpanCount();
    }
}
