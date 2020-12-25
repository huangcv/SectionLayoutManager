package androidx.recyclerview.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 吸顶LayoutManager
 * 注意（Attention）：
 * <p>
 * 1. 未处理反向布局问题(The condition of reverse is not processed.)
 *
 * @author Rango on 2020/11/17
 */
public class StickLayoutManager extends LinearLayoutManager {
    private final String tag = "StickLayoutManager";
    /**
     * 最多吸顶个数
     */
    private int maxSectionCount = 1;

    /**
     * 存储所有 section position
     * 在滚动的过程中进行更新这个已经实现，记录所有的Section的Position，但是有以下情况需要考虑
     * 1. adapter.notifyDataSetChanged()一系列方法调用的时候 更新问题
     * 2. scrollToPosition -- 更新问题
     * 3. 快速滚动的时候有些ViewHolder的绘制过程是省略的
     * 4. notifyItemRemoved() 使用viewHolder.isInvalid()判断
     */
    private SortedSet<Integer> sectionPositions = new TreeSet<>();

    /**
     * position < firstVisibleItemPosition的SectionViewHolder
     * 存储已经吸顶的Section（或已经滚动过去的SectionViewHolder）
     * 思考：
     * ViewHolder.layoutPosition  和 adapterPosition的思考
     */
    private final SectionCache sectionCache = new SectionCache();
    private final AtomicBoolean sectionCacheReset = new AtomicBoolean(false);

    private final RecyclerView.AdapterDataObserver adapterDataObserver = new RecyclerView.AdapterDataObserver() {
        @Override
        public void onChanged() {
            super.onChanged();
            sectionCache.clear();
            sectionCacheReset.set(true);
        }
    };

    public StickLayoutManager(Context context) {
        super(context);
    }


    @Override
    public void onAdapterChanged(@Nullable RecyclerView.Adapter oldAdapter, @Nullable RecyclerView.Adapter newAdapter) {
        super.onAdapterChanged(oldAdapter, newAdapter);
        sectionCache.clear();
        if (oldAdapter != null) {
            try {
                oldAdapter.unregisterAdapterDataObserver(adapterDataObserver);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        newAdapter.registerAdapterDataObserver(adapterDataObserver);
    }


    @Override
    void setRecyclerView(RecyclerView recyclerView) {
        super.setRecyclerView(recyclerView);
        if (recyclerView.getAdapter() != null) {
            try {
                recyclerView.getAdapter().unregisterAdapterDataObserver(adapterDataObserver);
            } catch (Exception e) {
                e.printStackTrace();
            }
            recyclerView.getAdapter().registerAdapterDataObserver(adapterDataObserver);
        }
    }


    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        removeAllSections();
        super.onLayoutChildren(recycler, state);
        postRelayoutChildren();
    }


    /**
     * 整体思路，
     * 在手指上滑（向上滚动 dy > 0）的时候,SectionViewHolder不能进入Holder缓存，需要独立缓存
     * 在手指下滑（向下滚动 dy < 0）的时候，SectionViewHolder在离开吸顶位置的时候需要将该ViewHolder重新放入缓存池，
     * 正常显示在列表中
     *
     * @param dy       >0 是手指向上滑动
     * @param recycler
     * @param state
     * @return
     */
    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        try {
            preRelayoutChildren(dy);
            //走LinearLayoutManager绘制流程
            return super.scrollVerticallyBy(dy, recycler, state);
        } finally {
            //走LinearLayoutManager绘制流程绘制完成后进行界面二次渲染
            postRelayoutChildren();
        }
    }

    private void preRelayoutChildren(int dy) {
        //遍历当前屏幕已经显示出来的ViewHolder，并从中过滤出需要吸顶的ViewHolder并将其保存到 sectionCache
        for (int i = 0; i < getChildCount(); i++) {
            View itemView = getChildAt(i);
            RecyclerView.ViewHolder vh = getViewHolderByView(itemView);
            if (!isSticker(vh) || sectionCache.peek() == vh) {
                continue;
            }
            if (dy > 0 && vh.itemView.getTop() < dy) {
                sectionCache.push(vh);
            } else {
                break;
            }
        }
        removeAllSections();
    }

    /**
     * 检查栈顶 -- 同步状态，在向下滚动（手指下滑）的时候，由于吸顶的ViewHolder都没有进入Recycler的缓存，所以在向
     * 下滚动的时候RecyclerView会重新创建ViewHolder实例，我们需要将其替换为我们自定义缓存中保存的实例。
     */
    private void replaceAttachedViewHolder() {
        for (RecyclerView.ViewHolder removedViewHolder : sectionCache.clearTop(findFirstVisibleItemPosition())) {
            for (int i = 0; i < getChildCount(); i++) {
                RecyclerView.ViewHolder attachedViewHolder = getViewHolderByView(getChildAt(i));
                if (removedViewHolder.getLayoutPosition() == attachedViewHolder.getLayoutPosition()) {
                    View attachedItemView = attachedViewHolder.itemView;
                    int left = attachedItemView.getLeft();
                    int top = attachedItemView.getTop();
                    int bottom = attachedItemView.getBottom();
                    int right = attachedItemView.getRight();

                    removeView(attachedItemView);
                    addView(removedViewHolder.itemView, i);
                    removedViewHolder.itemView.layout(left, top, right, bottom);
                    break;
                }
            }
        }
    }

    /**
     * 重布局
     */
    private void postRelayoutChildren() {
        if (sectionCacheReset.getAndSet(false)) {
            int firstVisibleItemPosition = findFirstVisibleItemPosition();
            for (int i = 0; i < firstVisibleItemPosition; i++) {
                if (mRecyclerView.getAdapter() instanceof StickAdapter) {
                    StickAdapter stickAdapter = (StickAdapter) mRecyclerView.getAdapter();
                    if (stickAdapter.shouldStick(i)) {
                        View itemView = mRecyclerView.mRecycler.getViewForPosition(i);
                        sectionCache.push(getViewHolderByView(itemView));
                    }
                }
            }
        }

        //取栈顶第一个显示出来的ViewHolder跟sectionCache中的栈顶ViewHolder做比较，过滤掉相等的情况，防止重复绘制
        RecyclerView.ViewHolder attachedViewHolder = getViewHolderByView(getChildAt(0));
        RecyclerView.ViewHolder cachedSectionViewHolder = sectionCache.peek();
        if (isSticker(attachedViewHolder)
                && cachedSectionViewHolder != null
                && cachedSectionViewHolder.getLayoutPosition() == attachedViewHolder.getLayoutPosition()) {
            removeViewAt(0);
        }
        replaceAttachedViewHolder();
        updateSectionLayout();
    }

    /**
     * 更新吸顶的ViewHolder
     */
    private void updateSectionLayout() {
        RecyclerView.ViewHolder section = sectionCache.peek();
        if (section != null) {
            View itemView = section.itemView;
            if (!itemView.isAttachedToWindow()) {
                addView(itemView);
            }
            View subItem = getChildAt(1);
            if (isSticker(getViewHolderByView(subItem))) {
                //将当前正在吸顶显示的Section向上顶或向下拉
                int h = itemView.getMeasuredHeight();
                int top = Math.min(0, -(h - subItem.getTop()));
                int bottom = Math.min(h, subItem.getTop());
                itemView.layout(0, top, itemView.getMeasuredWidth(), bottom);
            } else {
                itemView.layout(0, 0, itemView.getMeasuredWidth(), itemView.getMeasuredHeight());
            }
        }
    }


    /**
     * 删除所有的吸顶View,阻止进入系统的回收复用策略
     */
    private void removeAllSections() {
        Iterator<RecyclerView.ViewHolder> it = sectionCache.iterator();
        while (it.hasNext()) {
            RecyclerView.ViewHolder viewHolder = it.next();
            removeView(viewHolder.itemView);
            if (viewHolder.isInvalid()) {
                it.remove();
            }
        }
    }

    /**
     * 工具方法
     *
     * @param viewHolder .
     * @return true 是需要吸顶的ViewHolder
     */
    private boolean isSticker(RecyclerView.ViewHolder viewHolder) {
        if (viewHolder != null && mRecyclerView.getAdapter() instanceof StickAdapter) {
            return ((StickAdapter) mRecyclerView.getAdapter()).shouldStick(viewHolder.getLayoutPosition());
        } else {
            return false;
        }
    }

    private RecyclerView.ViewHolder getViewHolderByView(View view) {
        return RecyclerView.getChildViewHolderInt(view);
    }

    @Override
    public void onItemsRemoved(@NonNull RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsRemoved(recyclerView, positionStart, itemCount);
        Iterator<RecyclerView.ViewHolder> it = sectionCache.iterator();
        while (it.hasNext()) {
            RecyclerView.ViewHolder viewHolder = it.next();
            int oldLayoutPosition = viewHolder.getLayoutPosition();
            if (oldLayoutPosition == RecyclerView.NO_POSITION) {
                removeView(viewHolder.itemView);
                it.remove();
                continue;
            }
            int positionEnd = (positionStart + itemCount);
            if (positionStart <= oldLayoutPosition && oldLayoutPosition < positionEnd) {
                it.remove();
                continue;
            }
            if (oldLayoutPosition >= positionEnd && !viewHolder.itemView.isAttachedToWindow()) {
                viewHolder.offsetPosition(-itemCount, true);
                if (viewHolder.getLayoutPosition() < 0) {
                    it.remove();
                }
            }
        }
    }

    @Override
    public void onItemsChanged(@NonNull RecyclerView recyclerView) {
        super.onItemsChanged(recyclerView);
        //TODO：未实现
    }

    @Override
    public void onItemsAdded(@NonNull RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsAdded(recyclerView, positionStart, itemCount);
        //TODO：未实现
    }

    @Override
    public void onItemsMoved(@NonNull RecyclerView recyclerView, int from, int to, int itemCount) {
        super.onItemsMoved(recyclerView, from, to, itemCount);
        //TODO：未实现
    }

    @Override
    public void onItemsUpdated(@NonNull RecyclerView recyclerView, int positionStart, int itemCount, @Nullable Object payload) {
        super.onItemsUpdated(recyclerView, positionStart, itemCount, payload);
        //TODO：未实现
    }

    @Override
    public void onItemsUpdated(@NonNull RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsUpdated(recyclerView, positionStart, itemCount);
        //TODO：未实现
    }

    @Override
    public void scrollToPosition(int position) {
        scrollToPositionWithOffset(position, 0);
        //TODO：未实现
    }

    @Override
    public void scrollToPositionWithOffset(int position, int offset) {
        super.scrollToPositionWithOffset(position, offset);
        //TODO：未实现
    }

    public interface StickAdapter {
        boolean shouldStick(int adapterPosition);
    }

}
