package tungbui.drawer;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Luoi the 16:9. De tiet kiem RAM: chi giu ten + thong tin nhe cho moi app,
 * icon/banner tai khi hien ra va luu trong bo nho dem nho (LruCache).
 */
public class ApplicationAdapter extends RecyclerView.Adapter<ApplicationAdapter.VH> {

  public static class Entry {
    public final String packageName;
    public final CharSequence label;
    public final ActivityInfo info;

    public Entry(String packageName, CharSequence label, ActivityInfo info) {
      this.packageName = packageName;
      this.label = label;
      this.info = info;
    }
  }

  public interface Listener {
    void onClick(Entry entry);
    void onLongClick(Entry entry);
  }

  private final Context context;
  private final PackageManager pm;
  private final List<Entry> items = new ArrayList<>();
  private final Set<String> hidden;
  private final int tileW, tileH, iconPx;
  private final Listener listener;
  private String movingPkg;

  private final LruCache<String, Drawable> iconCache = new LruCache<>(48);
  private final LruCache<String, Drawable> bannerCache = new LruCache<>(24);
  private final Set<String> noBanner = new HashSet<>();

  public ApplicationAdapter(Context context, int tileW, int tileH, int iconPx,
                            Set<String> hidden, Listener listener) {
    this.context = context;
    this.pm = context.getPackageManager();
    this.tileW = tileW;
    this.tileH = tileH;
    this.iconPx = iconPx;
    this.hidden = hidden;
    this.listener = listener;
  }

  public void setItems(List<Entry> newItems) {
    items.clear();
    items.addAll(newItems);
    iconCache.evictAll();
    bannerCache.evictAll();
    noBanner.clear();
    notifyDataSetChanged();
  }

  public int indexOf(String pkg) {
    for (int i = 0; i < items.size(); i++) {
      if (items.get(i).packageName.equals(pkg)) return i;
    }
    return -1;
  }

  public Entry getEntry(int position) {
    return items.get(position);
  }

  /** Doi cho 2 o (che do di chuyen). */
  public void swap(int a, int b) {
    Collections.swap(items, a, b);
    notifyItemChanged(a);
    notifyItemChanged(b);
  }

  public void setMoving(String pkg) {
    String old = movingPkg;
    movingPkg = pkg;
    int a = old == null ? -1 : indexOf(old);
    int b = pkg == null ? -1 : indexOf(pkg);
    if (a >= 0) notifyItemChanged(a);
    if (b >= 0) notifyItemChanged(b);
  }

  private Drawable bannerOf(Entry e) {
    if (noBanner.contains(e.packageName)) return null;
    Drawable d = bannerCache.get(e.packageName);
    if (d != null) return d;
    try { d = e.info.loadBanner(pm); } catch (Throwable ignored) { }
    if (d == null) noBanner.add(e.packageName);
    else bannerCache.put(e.packageName, d);
    return d;
  }

  private Drawable iconOf(Entry e) {
    Drawable d = iconCache.get(e.packageName);
    if (d == null) {
      d = e.info.applicationInfo.loadIcon(pm);
      iconCache.put(e.packageName, d);
    }
    return d;
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  @Override
  public VH onCreateViewHolder(ViewGroup parent, int viewType) {
    View v = LayoutInflater.from(context).inflate(R.layout.item_app, parent, false);
    ViewGroup.LayoutParams lp = v.getLayoutParams();
    lp.width = tileW;
    lp.height = tileH;
    v.setLayoutParams(lp);
    VH h = new VH(v);
    ViewGroup.LayoutParams ip = h.icon.getLayoutParams();
    ip.width = iconPx;
    ip.height = iconPx;
    h.icon.setLayoutParams(ip);
    return h;
  }

  @Override
  public void onBindViewHolder(final VH h, int position) {
    final Entry e = items.get(position);
    Drawable banner = bannerOf(e);
    if (banner != null) {
      h.banner.setImageDrawable(banner);
      h.banner.setVisibility(View.VISIBLE);
      h.info.setVisibility(View.GONE);
    } else {
      h.banner.setImageDrawable(null);
      h.banner.setVisibility(View.GONE);
      h.info.setVisibility(View.VISIBLE);
      h.icon.setImageDrawable(iconOf(e));
      h.name.setText(e.label);
    }
    h.card.setAlpha(hidden.contains(e.packageName) ? 0.35f : 1f);   // app dang an: mo di
    h.root.setActivated(e.packageName.equals(movingPkg));
    h.root.setContentDescription(e.label);
    h.root.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (listener != null) listener.onClick(e);
      }
    });
    h.root.setOnLongClickListener(new View.OnLongClickListener() {
      @Override
      public boolean onLongClick(View view) {
        if (listener != null) listener.onLongClick(e);
        return true;
      }
    });
  }

  static class VH extends RecyclerView.ViewHolder {
    final View root;
    final View card;
    final ImageView banner;
    final View info;
    final ImageView icon;
    final TextView name;

    VH(View v) {
      super(v);
      root = v;
      card = v.findViewById(R.id.tile_card);
      banner = (ImageView) v.findViewById(R.id.tile_banner);
      info = v.findViewById(R.id.tile_info);
      icon = (ImageView) v.findViewById(R.id.app_icon);
      name = (TextView) v.findViewById(R.id.app_name);
      // Phong to nhe khi duoc chon bang phim dieu khien
      v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View view, boolean hasFocus) {
          float s = hasFocus ? 1.08f : 1f;
          view.animate().scaleX(s).scaleY(s).setDuration(120).start();
        }
      });
    }
  }
}
