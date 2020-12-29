package androidx.recyclerview.widget;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
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
    private final AtomicBoolean sectionCacheResetFlag = new AtomicBoolean(false);
    /**
     * 记录当前吸顶的Section的下边元的位置信息，超过该区域去的ViewHolder需要出发Section的回收逻辑
     * （对比单吸顶方案的重要区别）
     */
    private int maxSectionSpace = getPaddingTop();
    private int minSectionSpace = maxSectionSpace;

    private final LinkedList<RecyclerView.ViewHolder> attachedSectionCache = new LinkedList<>();

    public StickLayoutManager(Context context) {
        super(context);
        maxSectionCount = 1;
    }

    public StickLayoutManager(Context context, int stickCount) {
        super(context);
        maxSectionCount = Math.max(1, stickCount);
    }

    @Override
    public void onAdapterChanged(@Nullable RecyclerView.Adapter oldAdapter,
                                 @Nullable RecyclerView.Adapter newAdapter) {
        super.onAdapterChanged(oldAdapter, newAdapter);
        resetCache();
    }


    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        recyclerAllAttachedSection();
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
            removeDuplicateSection();
            replaceAttachedViewHolder();
            postRelayoutChildren();

        }
    }

    /**
     * 检查栈顶 -- 同步状态，在向下滚动（手指下滑）的时候，由于吸顶的ViewHolder都没有进入Recycler的缓存，所以在向
     * 下滚动的时候RecyclerView会重新创建ViewHolder实例，我们需要将其替换为我们自定义缓存中保存的实例。
     */
    private void replaceAttachedViewHolder() {
        for (RecyclerView.ViewHolder removedViewHolder : attachedSectionCache) {
            for (int i = 0; i < getChildCount(); i++) {
                RecyclerView.ViewHolder attachedViewHolder = getViewHolderByView(getChildAt(i));
                if (removedViewHolder.getLayoutPosition() == attachedViewHolder.getLayoutPosition()) {
                    View attachedItemView = attachedViewHolder.itemView;
                    removeView(attachedItemView);
                    break;
                }
            }
        }
    }

    /**
     * 预处理吸顶ViewHolder布局
     *
     * @param dy 滚动距离
     */
    private void preRelayoutChildren(int dy) {
        if (dy == 0) {
            return;
        }
        recyclerAllAttachedSection();
        //遍历当前屏幕已经显示出来的ViewHolder，并从中过滤出需要吸顶的ViewHolder并将其保存到 sectionCache
        for (int i = 0; i < getChildCount(); i++) {
            View itemView = getChildAt(i);
            RecyclerView.ViewHolder vh = getViewHolderByView(itemView);
            if (!isSticker(vh)) {
                continue;
            }
            if (vh.itemView.getTop() <= minSectionSpace + dy) {
                if (dy > 0) {
                    sectionCache.push(vh);
                }
            } else {
                if (dy < 0) {
                    sectionCache.remove(vh);
                }
            }
            break;
        }
    }

    private int getSectionBaseLine() {
        if (attachedSectionCache.isEmpty()) {
            return getPaddingTop();
        }
        RecyclerView.ViewHolder viewHolder = attachedSectionCache.getLast();
        if (viewHolder == null) {
            return getPaddingTop();
        }
        return viewHolder.itemView.getBottom();
    }

    private void removeDuplicateSection() {
        int baseLine = getSectionBaseLine();
        for (int i = 0; i < getChildCount(); i++) {
            View itemView = getChildAt(i);
            RecyclerView.ViewHolder vh = getViewHolderByView(itemView);
            if (!isSticker(vh) || attachedSectionCache.contains(vh)) {
                continue;
            }
            if (itemView.getTop() <= baseLine) {
                removeView(itemView);
            }
        }
    }

    private void recyclerAllAttachedSection() {
        //回收所有的吸顶ViewHolder,阻止进入系统的回收复用策略
        Iterator<RecyclerView.ViewHolder> it = attachedSectionCache.iterator();
        while (it.hasNext()) {
            RecyclerView.ViewHolder vh = it.next();
            sectionCache.push(vh);
            it.remove();
        }
    }


    /**
     * 获取当前吸顶Section的Bottom，如果没有吸顶Section
     *
     * @return .
     */
    private int getAttachedSectionBottom() {
        RecyclerView.ViewHolder lastSection = attachedSectionCache.getLast();
        return lastSection == null ? getPaddingTop() : lastSection.itemView.getBottom();
    }

    /**
     * 吸顶的Section需要布局在该区域内
     *
     * @param sections .
     * @return 高度
     */
    private void measureSectionSpace(List<RecyclerView.ViewHolder> sections) {
        if (sections == null || sections.isEmpty()) {
            return;
        }
        maxSectionSpace = getPaddingTop();
        minSectionSpace = maxSectionSpace;
        for (int i = 0; i < sections.size(); i++) {
            RecyclerView.ViewHolder section = sections.get(i);
            int sectionHeight = section.itemView.getMeasuredHeight();
            //TODO：这里计算的时候还需要考虑 ItemDecoration
            if (i != 0) {
                minSectionSpace += sectionHeight;
            }
            maxSectionSpace += sectionHeight;
        }
    }

    /**
     * 重布局
     */
    private void postRelayoutChildren() {
        if (sectionCacheResetFlag.getAndSet(false)) {
            recollectSections();
        }

        if (sectionCache.isEmpty() && attachedSectionCache.isEmpty()) {
            return;
        }
        attachedSectionCache.addAll(sectionCache.pop(maxSectionCount));
        //测量Section
        measureSectionSpace(attachedSectionCache);

        int sectionLayoutBaseLine = maxSectionSpace;
        for (int i = 0; i < getChildCount(); i++) {
            View itemView = getChildAt(i);
            RecyclerView.ViewHolder viewHolder = getViewHolderByView(itemView);
            if (!isSticker(viewHolder)) {
                continue;
            }
            int vTop = itemView.getTop();
            if (vTop < minSectionSpace) {
                //TODO: error 不应该出现这种情况
                //throw new RuntimeException("Section Space cal error.");
            } else if (vTop < maxSectionSpace) {
                sectionLayoutBaseLine = vTop;
            } else {
                sectionLayoutBaseLine = maxSectionSpace;
            }
        }

        for (RecyclerView.ViewHolder section : attachedSectionCache) {
            if (section == null || section.itemView == null) {
                continue;
            }
            View itemView = section.itemView;
            if (!itemView.isAttachedToWindow()) {
                addView(itemView);
            }
            int top = sectionLayoutBaseLine - itemView.getMeasuredHeight();
            itemView.layout(0, top, itemView.getMeasuredWidth(), sectionLayoutBaseLine);
            sectionLayoutBaseLine -= itemView.getMeasuredHeight();
        }
    }


    /**
     * 重新搜集所有的Section
     */
    private void recollectSections() {
        resetCache();
        if (!(mRecyclerView.getAdapter() instanceof StickAdapter)) {
            return;
        }
        StickAdapter stickAdapter = (StickAdapter) mRecyclerView.getAdapter();
        int firstVisibleItemPosition = findFirstVisibleItemPosition();
        for (int i = 0; i < firstVisibleItemPosition; i++) {
            if (stickAdapter.shouldStick(i)) {
                View itemView = mRecyclerView.mRecycler.getViewForPosition(i);
                RecyclerView.ViewHolder viewHolder = getViewHolderByView(itemView);
                if (viewHolder != null) {
                    //noinspection unchecked
                    mRecyclerView.getAdapter().bindViewHolder(viewHolder, i);
                }
                sectionCache.push(viewHolder);
            }
        }
    }

    private void resetCache() {
        sectionCache.clear();
        attachedSectionCache.clear();
        sectionCacheResetFlag.set(true);
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
        resetCache();
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
        //遍历Adapter把所有的Section取出
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