package quang.drawer;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Luoi ung dung dang the 16:9: co banner thi hien banner, khong co thi hien icon + ten. */
public class ApplicationAdapter extends RecyclerView.Adapter<ApplicationAdapter.VH> {

  /** Mot ung dung hien tren luoi (da tai san nhan/icon/banner o luong nen). */
  public static class Entry {
    public final String packageName;
    public final CharSequence label;
    public final Drawable icon;
    public final Drawable banner;   // co the null

    public Entry(String packageName, CharSequence label, Drawable icon, Drawable banner) {
      this.packageName = packageName;
      this.label = label;
      this.icon = icon;
      this.banner = banner;
    }
  }

  public interface OnAppClick {
    void onClick(Entry entry);
  }

  private final Context context;
  private final List<Entry> items = new ArrayList<>();
  private final int tileHeightPx;
  private final OnAppClick listener;

  public ApplicationAdapter(Context context, int tileHeightPx, OnAppClick listener) {
    this.context = context;
    this.tileHeightPx = tileHeightPx;
    this.listener = listener;
  }

  public void setItems(List<Entry> newItems) {
    items.clear();
    items.addAll(newItems);
    notifyDataSetChanged();
  }

  public Entry getEntry(int position) {
    return items.get(position);
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  @Override
  public VH onCreateViewHolder(ViewGroup parent, int viewType) {
    View v = LayoutInflater.from(context).inflate(R.layout.item_app, parent, false);
    ViewGroup.LayoutParams lp = v.getLayoutParams();
    lp.height = tileHeightPx;
    v.setLayoutParams(lp);
    return new VH(v);
  }

  @Override
  public void onBindViewHolder(final VH h, int position) {
    final Entry e = items.get(position);
    h.name.setText(e.label);
    if (e.banner != null) {
      h.banner.setImageDrawable(e.banner);
      h.banner.setVisibility(View.VISIBLE);
      h.info.setVisibility(View.GONE);
    } else {
      h.banner.setImageDrawable(null);
      h.banner.setVisibility(View.GONE);
      h.info.setVisibility(View.VISIBLE);
      h.icon.setImageDrawable(e.icon);
    }
    h.root.setContentDescription(e.label);
    h.root.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (listener != null) listener.onClick(e);
      }
    });
  }

  static class VH extends RecyclerView.ViewHolder {
    final View root;
    final ImageView banner;
    final View info;
    final ImageView icon;
    final TextView name;

    VH(View v) {
      super(v);
      root = v;
      banner = (ImageView) v.findViewById(R.id.tile_banner);
      info = v.findViewById(R.id.tile_info);
      icon = (ImageView) v.findViewById(R.id.app_icon);
      name = (TextView) v.findViewById(R.id.app_name);
      final float lift = 12f * v.getResources().getDisplayMetrics().density;
      // Phong to nhe + noi len khi duoc chon bang phim dieu khien
      v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View view, boolean hasFocus) {
          float s = hasFocus ? 1.10f : 1f;
          view.animate().scaleX(s).scaleY(s).translationZ(hasFocus ? lift : 0f)
              .setDuration(130).start();
        }
      });
    }
  }
}
