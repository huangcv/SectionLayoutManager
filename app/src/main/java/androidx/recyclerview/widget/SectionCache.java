package androidx.recyclerview.widget;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * 吸顶ViewHolder的缓存, 没有显示出来（已经显示出来的不在次队列中）
 *
 * @author Rango on 2020/11/17
 */
public class SectionCache extends Stack<RecyclerView.ViewHolder> {
    private Map<Integer, RecyclerView.ViewHolder>
            filterMap = new HashMap<>(16, 64);

    @Override
    public RecyclerView.ViewHolder push(RecyclerView.ViewHolder vh) {
        if (vh == null) {
            return null;
        }
        //进入缓存队列移除View
        if (vh.itemView.isAttachedToWindow()) {
            vh.mOwnerRecyclerView.removeView(vh.itemView);
        }
        int position = vh.getLayoutPosition();
        //避免存在重复的Value
        if (containsPosition(position)) {
            //返回null说明没有添加成功
            return null;
        }
        filterMap.put(position, vh);
        return super.push(vh);
    }

    public void clearInvalidValue() {
        Iterator<RecyclerView.ViewHolder> it = iterator();
        while (it.hasNext()) {
            RecyclerView.ViewHolder viewHolder = it.next();
            if (!viewHolder.isInvalid()) {
                it.remove();
            }
        }
    }

    public RecyclerView.ViewHolder getByAdapterPosition(int adapterPosition) {
        return filterMap.get(adapterPosition);
    }


    public boolean containsPosition(int position) {
        return filterMap.containsKey(position);
    }

    @Override
    public synchronized RecyclerView.ViewHolder peek() {
        if (size() == 0) {
            return null;
        }
        return super.peek();
    }

    /**
     * 栈顶清理，在快速滚动的情境下可能会出现一次多个吸顶的ViewHolder出栈的情况，这个时候需要
     * 根据LayoutPosition清理栈顶，保证栈内ViewHolder和列表当前的状态一致。
     *
     * @param layoutPosition 大于position的内容会被清理
     */
    public List<RecyclerView.ViewHolder> clearTop(int layoutPosition) {
        List<RecyclerView.ViewHolder> removedViewHolders = new LinkedList<>();
        Iterator<RecyclerView.ViewHolder> it = iterator();
        while (it.hasNext()) {
            RecyclerView.ViewHolder top = it.next();
            if (top.getLayoutPosition() > layoutPosition) {
                it.remove();
                filterMap.remove(top.getLayoutPosition());
                removedViewHolders.add(top);
            }
        }
        return removedViewHolders;
    }

    /**
     * 指定出栈个数
     *
     * @param count 出栈数量
     * @return
     */
    public List<RecyclerView.ViewHolder> pop(int count) {
        List<RecyclerView.ViewHolder> result = new LinkedList<>();
        for (int i = count; i > 0; i--) {
            if (!isEmpty()) {
                RecyclerView.ViewHolder viewHolder = pop();
                filterMap.remove(viewHolder.getLayoutPosition());
                result.add(viewHolder);
            }
        }
        return result;
    }

    @Override
    public boolean remove(@Nullable Object o) {
        if (o instanceof RecyclerView.ViewHolder) {
            filterMap.remove(((RecyclerView.ViewHolder) o).getLayoutPosition());
        }
        return super.remove(o);
    }

    @Override
    public void clear() {
        super.clear();
        filterMap.clear();
    }
}
