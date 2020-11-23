package androidx.recyclerview.widget;

import android.content.Context;

/**
 * @author Rango on 2020/11/17
 */
public class MyLLL extends LinearLayoutManager {
    public MyLLL(Context context, int orientation, boolean reverseLayout) {
        super(context, orientation, reverseLayout);
        if (mOrientationHelper.mLayoutManager == null) {

        }
        RecyclerView.ViewHolder v = ((RecyclerView.LayoutParams) getChildAt(0).getLayoutParams()).mViewHolder;
        int a = v.mPosition;
        int b = v.mOldPosition;

    }
}
