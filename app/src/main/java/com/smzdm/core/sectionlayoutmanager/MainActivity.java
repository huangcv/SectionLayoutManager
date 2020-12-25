package com.smzdm.core.sectionlayoutmanager;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StickLayoutManager;

import com.smzdm.core.sectionlayoutmanager.holders.ItemViewHolder;
import com.smzdm.core.sectionlayoutmanager.holders.SectionViewHolder;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    static final String tag = "MainActivity";
    private RecyclerView rlv;
    private List<DataBean> dataSource = new ArrayList<>(50);
    private MyAdapter adapter = new MyAdapter();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        rlv = findViewById(R.id.rlv);
        rlv.setAdapter(adapter);
        rlv.setLayoutManager(new StickLayoutManager(this));
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn:
//                rlv.getLayoutManager().removeAndRecycleViewAt(0,rlv.getLayoutManager().)
                adapter.notifyDataSetChanged();
                break;
            case R.id.btnRemove:
                if (dataSource.isEmpty()) {
                    return;
                }
                dataSource.remove(0);
                dataSource.remove(0);
                adapter.notifyItemRangeRemoved(0,2);
                break;
            case R.id.btnScroll:
                rlv.scrollToPosition(((LinearLayoutManager) rlv.getLayoutManager()).findFirstVisibleItemPosition() + 10);
                break;
            case R.id.btnReset:
                adapter = new MyAdapter();
                rlv.setAdapter(adapter);
                adapter.notifyDataSetChanged();
                break;
            default:
//                        rlv.scrollToPosition(10)
//                rlv.smoothScrollToPosition(35)
//                rlv.getAdapter().notifyItemChanged(44)
                break;
        }
    }

    class DataBean {
        String data;
        boolean section = false;

        public DataBean(String data, boolean section) {
            this.data = data;
            this.section = section;
        }
    }

    class MyAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        public MyAdapter() {
            dataSource.clear();
            for (int i = 0; i < 50; i++) {
                dataSource.add(new DataBean("测试数据" + i, (i % 5) == 3));
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 0) {
                return new SectionViewHolder(parent);
            }
            return new ItemViewHolder(parent);
        }

        @Override
        public int getItemViewType(int position) {
            return dataSource.get(position).section ? 0 : 1;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Log.i(tag, "onBindViewHolder");
            DataBean data = dataSource.get(position);
            if (getItemViewType(position) == 1) {
                ((ItemViewHolder) holder).tv.setText(data.data + ",\nadapterPosition: " + position);
            } else {
                ((SectionViewHolder) holder).tv.setText(data.data +":" + position);
            }
        }

        @Override
        public int getItemCount() {
            return dataSource.size();
        }
    }
}