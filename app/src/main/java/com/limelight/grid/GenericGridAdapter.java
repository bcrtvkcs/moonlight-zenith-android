package com.limelight.grid;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.limelight.R;

import java.util.ArrayList;

public abstract class GenericGridAdapter<T> extends BaseAdapter {
    protected final Context context;
    private int layoutId;
    final ArrayList<T> itemList = new ArrayList<>();
    private final LayoutInflater inflater;

    private static class ViewHolder {
        ImageView imgView;
        ImageView overlayView;
        TextView txtView;
        ProgressBar prgView;
        int layoutId;
    }

    GenericGridAdapter(Context context, int layoutId) {
        this.context = context;
        this.layoutId = layoutId;

        this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    void setLayoutId(int layoutId) {
        if (layoutId != this.layoutId) {
            this.layoutId = layoutId;

            // Force the view to be redrawn with the new layout
            notifyDataSetInvalidated();
        }
    }

    public void clear() {
        itemList.clear();
    }

    @Override
    public int getCount() {
        return itemList.size();
    }

    @Override
    public Object getItem(int i) {
        return itemList.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    public abstract void populateView(View parentView, ImageView imgView, ProgressBar prgView, TextView txtView, ImageView overlayView, T obj);

    @Override
    public View getView(int i, View convertView, ViewGroup viewGroup) {
        ViewHolder holder;
        if (convertView == null || !(convertView.getTag() instanceof ViewHolder) || ((ViewHolder) convertView.getTag()).layoutId != layoutId) {
            convertView = inflater.inflate(layoutId, viewGroup, false);
            holder = new ViewHolder();
            holder.layoutId = layoutId;
            holder.imgView = convertView.findViewById(R.id.grid_image);
            holder.overlayView = convertView.findViewById(R.id.grid_overlay);
            holder.txtView = convertView.findViewById(R.id.grid_text);
            holder.prgView = convertView.findViewById(R.id.grid_spinner);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        populateView(convertView, holder.imgView, holder.prgView, holder.txtView, holder.overlayView, itemList.get(i));

        return convertView;
    }
}
