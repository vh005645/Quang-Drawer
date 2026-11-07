/*
 * Copyright (C) 2015 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package quang.drawer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppDrawerActivity extends Activity {
  private static final String TAG = "QuangDrawer";
  private static final String PREFS = "quang_drawer";
  private static final String KEY_ORDER = "order";
  private static final String KEY_HIDDEN = "hidden";
  private static final int REQ_WALLPAPER = 77;

  private static final int SPAN = 9;        // 9 the moi hang, hien 2 hang (giong anh mau)
  private static final int ROWS = 2;

  private PackageManager packageManager;
  private SharedPreferences prefs;
  private ApplicationAdapter adapter;
  private RecyclerView grid;
  private GridLayoutManager layoutManager;
  private TextView moveHint;
  private int loadGeneration = 0;
  private final ExecutorService loader = Executors.newSingleThreadExecutor();

  // Toan bo app theo thu tu nguoi dung sap xep (ke ca app dang an); luoi chi hien phan con lai
  private List<ApplicationAdapter.Entry> master = new ArrayList<>();
  private final Set<String> hiddenSet = new HashSet<>();
  private boolean showHidden = false;
  private String movingPkg = null;

  // ---- Danh sach app duoc goi day moi khi vao man hinh chinh ----
  // Launcher nay la man hinh chinh nen he thong tu mo no moi lan bat TV / thuc day tu che do cho.
  // Moi lan do, launcher goi lan luot cac app ben duoi day.
  //
  // 1) TV Guard: mo muc khoi dong TRONG SUOT (BootTrigger, khong ve gi len man hinh). GIU NGUYEN.
  // 2) Hai app phu tro: chi biet ten goi -> tu tim man hinh khoi dong cua app do.
  //    Neu muon chi dinh ro thanh phan, dien o cot thu 2 dang "ten.goi/ten.lop" (hoac "ten.goi/.Lop").
  private static final String TVGUARD_PKG = "com.tvguard.app";
  private static final String TVGUARD_TRIGGER = "com.tvguard.app.BootTrigger";

  private static final String[][] EXTRA_WAKE = {
      {"tb.voice.mstore", null},
      {"tungbui.mitv.cstore", null},
  };

  private static final long WAKE_COOLDOWN_MS = 2 * 60 * 1000L;
  private static final long WAKE_FIRST_DELAY_MS = 500;
  private static final long WAKE_STEP_MS = 1000;
  private static long lastWake = -1;
  private final Handler handler = new Handler(Looper.getMainLooper());

  private void wakeAll() {
    long now = SystemClock.elapsedRealtime();
    if (lastWake >= 0 && now >= lastWake && now - lastWake < WAKE_COOLDOWN_MS) return;
    lastWake = now;   // dat truoc de khong lap lai khi man hinh nay duoc hien lai

    // TV Guard truoc, y nhu ban cu
    handler.postDelayed(new Runnable() {
      @Override
      public void run() {
        if (isFinishing()) return;
        wakeTvGuard();
      }
    }, WAKE_FIRST_DELAY_MS);

    // Roi lan luot cac app phu tro, moi app cach nhau 1 giay de khong tranh nhau man hinh
    long delay = WAKE_FIRST_DELAY_MS;
    for (final String[] target : EXTRA_WAKE) {
      delay += WAKE_STEP_MS;
      handler.postDelayed(new Runnable() {
        @Override
        public void run() {
          if (isFinishing()) return;
          wakePackage(target[0], target[1]);
        }
      }, delay);
    }
  }

  private void wakeTvGuard() {
    try {
      Intent i = new Intent();
      i.setComponent(new ComponentName(TVGUARD_PKG, TVGUARD_TRIGGER));
      if (i.resolveActivity(getPackageManager()) != null) {
        startActivity(i);
      }
    } catch (Throwable ignored) {
      // Khong co TV Guard hoac bi chan: bo qua, launcher van hoat dong binh thuong
    }
  }

  private void wakePackage(String pkg, String component) {
    try {
      Intent i = buildWakeIntent(pkg, component);
      if (i == null) {
        Log.w(TAG, "Khong tim thay man hinh de goi " + pkg + " (chua cai hoac khong co activity)");
        return;
      }
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(i);
    } catch (Throwable t) {
      Log.w(TAG, "Goi " + pkg + " loi: " + t);
    }
  }

  private Intent buildWakeIntent(String pkg, String component) throws Exception {
    PackageManager pm = getPackageManager();
    // a) thanh phan chi dinh ro
    if (component != null) {
      String cls = component.startsWith(".") ? pkg + component : component;
      Intent i = new Intent().setComponent(new ComponentName(pkg, cls));
      return i.resolveActivity(pm) != null ? i : null;
    }
    // b) man hinh launcher / leanback cua app
    Intent i = pm.getLaunchIntentForPackage(pkg);
    if (i == null) i = pm.getLeanbackLaunchIntentForPackage(pkg);
    if (i != null) return i;
    // c) app khong co icon: lay activity dau tien cho phep goi tu ngoai
    PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
    if (info.activities != null) {
      for (ActivityInfo a : info.activities) {
        if (a.exported && a.enabled) {
          return new Intent().setComponent(new ComponentName(a.packageName, a.name));
        }
      }
    }
    return null;
  }

  @Override
  protected void onResume() {
    super.onResume();
    wakeAll();
  }

  // La man hinh chinh: bam Back khong duoc thoat (neu thoat, he thong mo lai ngay)
  @Override
  public void onBackPressed() {
    if (movingPkg != null) endMove();
  }

  private int dp(int v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  // Cai dat/go app -> cap nhat luoi
  private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (movingPkg == null) loadApps();
    }
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    packageManager = getPackageManager();
    prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
    hiddenSet.addAll(prefs.getStringSet(KEY_HIDDEN, new HashSet<String>()));

    grid = (RecyclerView) findViewById(R.id.grid);
    moveHint = (TextView) findViewById(R.id.move_hint);

    // Bo cuc theo anh mau: le 5,5% hai ben, 9 the / hang (16:9), icon ~45% chieu cao the
    DisplayMetrics dm = getResources().getDisplayMetrics();
    int side = Math.round(dm.widthPixels * 0.055f);
    findViewById(R.id.content).setPadding(side, dp(24), side, dp(18));
    int margin = dp(4);
    int colW = (dm.widthPixels - 2 * side) / SPAN;
    int tileW = colW - 2 * margin;
    int tileH = tileW * 9 / 16;
    int iconPx = Math.round(tileH * 0.45f);
    ViewGroup.LayoutParams glp = grid.getLayoutParams();
    glp.height = ROWS * (tileH + 2 * margin) + grid.getPaddingTop() + grid.getPaddingBottom();
    grid.setLayoutParams(glp);

    adapter = new ApplicationAdapter(this, tileW, tileH, iconPx, hiddenSet,
        new ApplicationAdapter.Listener() {
          @Override
          public void onClick(ApplicationAdapter.Entry entry) {
            if (movingPkg != null) { endMove(); return; }
            launch(entry.packageName);
          }

          @Override
          public void onLongClick(ApplicationAdapter.Entry entry) {
            if (movingPkg == null) showAppMenu(entry);
          }
        });
    layoutManager = new GridLayoutManager(this, SPAN);
    grid.setLayoutManager(layoutManager);
    grid.setHasFixedSize(true);
    grid.setAdapter(adapter);
    grid.setItemAnimator(null);

    findViewById(R.id.btn_input).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) { openInputPicker(); }
    });
    findViewById(R.id.btn_wifi).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) { openWifi(); }
    });
    View btnSettings = findViewById(R.id.btn_settings);
    btnSettings.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) { openSettings(); }
    });
    // Nhan giu nut Cai dat: tuy chon launcher (hinh nen, hien app da an)
    btnSettings.setOnLongClickListener(new View.OnLongClickListener() {
      @Override
      public boolean onLongClick(View v) { showLauncherMenu(); return true; }
    });

    IntentFilter f = new IntentFilter();
    f.addAction(Intent.ACTION_PACKAGE_ADDED);
    f.addAction(Intent.ACTION_PACKAGE_REMOVED);
    f.addAction(Intent.ACTION_PACKAGE_REPLACED);
    f.addDataScheme("package");
    registerReceiver(packageReceiver, f);

    loadWallpaperAsync();
    loadApps();
  }

  @Override
  protected void onDestroy() {
    try { unregisterReceiver(packageReceiver); } catch (Exception ignored) { }
    loader.shutdownNow();
    super.onDestroy();
  }

  // ---- Nut tren cung: Cai dat, WiFi, Nguon vao ----
  private void openSettings() {
    if (startSettings(Settings.ACTION_SETTINGS)) return;
    String[] candidates = {"com.android.tv.settings", "com.xiaomi.mitv.settings", "com.android.settings"};
    for (String pkg : candidates) {
      try {
        Intent i = packageManager.getLaunchIntentForPackage(pkg);
        if (i == null) i = packageManager.getLeanbackLaunchIntentForPackage(pkg);
        if (i != null) { startActivity(i); return; }
      } catch (Exception ignored) { }
    }
    Toast.makeText(this, R.string.settings_fail, Toast.LENGTH_LONG).show();
  }

  private void openWifi() {
    if (!startSettings(Settings.ACTION_WIFI_SETTINGS)) openSettings();
  }

  private boolean startSettings(String action) {
    try {
      startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /** Liet ke cac nguon vao ngoai (HDMI, AV...) cua TV roi mo nguon duoc chon. */
  private void openInputPicker() {
    try {
      TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
      List<TvInputInfo> all = tim == null ? null : tim.getTvInputList();
      final List<TvInputInfo> inputs = new ArrayList<>();
      List<CharSequence> labels = new ArrayList<>();
      if (all != null) {
        for (TvInputInfo info : all) {
          if (!info.isPassthroughInput()) continue;
          CharSequence l = info.loadCustomLabel(this);
          if (l == null || l.length() == 0) l = info.loadLabel(this);
          inputs.add(info);
          labels.add(l);
        }
      }
      if (inputs.isEmpty()) {
        Toast.makeText(this, R.string.no_input, Toast.LENGTH_LONG).show();
        return;
      }
      new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
          .setTitle(R.string.input_source)
          .setItems(labels.toArray(new CharSequence[0]), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
              try {
                Uri uri = TvContract.buildChannelUriForPassthroughInput(inputs.get(which).getId());
                startActivity(new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
              } catch (Throwable t) {
                Toast.makeText(AppDrawerActivity.this, R.string.input_fail, Toast.LENGTH_LONG).show();
              }
            }
          })
          .show();
    } catch (Throwable t) {
      Log.w(TAG, "Input picker loi: " + t);
      Toast.makeText(this, R.string.no_input, Toast.LENGTH_LONG).show();
    }
  }

  private void launch(String packageName) {
    try {
      Intent intent = packageManager.getLaunchIntentForPackage(packageName);
      if (intent == null) {
        intent = packageManager.getLeanbackLaunchIntentForPackage(packageName);
      }
      if (intent != null) {
        startActivity(intent);
      }
    } catch (ActivityNotFoundException e) {
      Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
    } catch (Exception e2) {
      Toast.makeText(this, e2.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  // ---- Hinh nen: 1 anh da thu nho dung bang man hinh, RGB_565 (~4 MB), khong giu gi them ----
  private File wallpaperFile() {
    return new File(getFilesDir(), "wallpaper.jpg");
  }

  private File externalWallpaper() {
    File dir = getExternalFilesDir(null);
    return dir == null ? null : new File(dir, "wallpaper.jpg");
  }

  private void loadWallpaperAsync() {
    try {
      loader.execute(new Runnable() {
        @Override
        public void run() {
          File ext = externalWallpaper();
          if (ext != null && ext.exists()) {          // anh chep tay vao thu muc cua app
            if (importWallpaper(Uri.fromFile(ext))) ext.delete();
          }
          File f = wallpaperFile();
          if (!f.exists()) return;
          BitmapFactory.Options o = new BitmapFactory.Options();
          o.inPreferredConfig = Bitmap.Config.RGB_565;
          final Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
          if (b == null) return;
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              if (isDestroyed()) return;
              getWindow().setBackgroundDrawable(new BitmapDrawable(getResources(), b));
            }
          });
        }
      });
    } catch (Exception ignored) { }
  }

  private void resetWallpaper() {
    wallpaperFile().delete();
    getWindow().setBackgroundDrawableResource(R.drawable.bg_launcher);
    Toast.makeText(this, R.string.wallpaper_ok, Toast.LENGTH_SHORT).show();
  }

  private void chooseWallpaper() {
    File ext = externalWallpaper();
    if (ext != null && ext.exists()) {        // da co anh chep san: dung luon
      loadWallpaperAsync();
      return;
    }
    try {
      Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
      startActivityForResult(i, REQ_WALLPAPER);
    } catch (Exception e) {
      String path = ext == null ? "Android/data/" + getPackageName() + "/files" : ext.getParent();
      Toast.makeText(this, getString(R.string.wallpaper_no_picker, path), Toast.LENGTH_LONG).show();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode != REQ_WALLPAPER || resultCode != RESULT_OK || data == null
        || data.getData() == null) return;
    final Uri uri = data.getData();
    try {
      loader.execute(new Runnable() {
        @Override
        public void run() {
          final boolean ok = importWallpaper(uri);
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              if (isDestroyed()) return;
              Toast.makeText(AppDrawerActivity.this,
                  ok ? R.string.wallpaper_ok : R.string.wallpaper_fail, Toast.LENGTH_SHORT).show();
              if (ok) loadWallpaperAsync();
            }
          });
        }
      });
    } catch (Exception ignored) { }
  }

  /** Doc anh, cat giua cho vua ti le man hinh, thu nho bang man hinh roi luu JPEG. */
  private boolean importWallpaper(Uri uri) {
    Bitmap src = null, crop = null, out = null;
    try {
      DisplayMetrics dm = getResources().getDisplayMetrics();
      int w = dm.widthPixels, h = dm.heightPixels;
      BitmapFactory.Options o = new BitmapFactory.Options();
      o.inJustDecodeBounds = true;
      InputStream in = getContentResolver().openInputStream(uri);
      BitmapFactory.decodeStream(in, null, o);
      in.close();
      int s = 1;
      while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2;
      o = new BitmapFactory.Options();
      o.inSampleSize = s;
      o.inPreferredConfig = Bitmap.Config.RGB_565;
      in = getContentResolver().openInputStream(uri);
      src = BitmapFactory.decodeStream(in, null, o);
      in.close();
      if (src == null) return false;
      float scale = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
      int cw = Math.min(src.getWidth(), Math.round(w / scale));
      int ch = Math.min(src.getHeight(), Math.round(h / scale));
      crop = Bitmap.createBitmap(src, (src.getWidth() - cw) / 2, (src.getHeight() - ch) / 2, cw, ch);
      out = Bitmap.createScaledBitmap(crop, w, h, true);
      File tmp = new File(getFilesDir(), "wallpaper.tmp");
      FileOutputStream fo = new FileOutputStream(tmp);
      out.compress(Bitmap.CompressFormat.JPEG, 88, fo);
      fo.close();
      return tmp.renameTo(wallpaperFile());
    } catch (Throwable t) {
      Log.w(TAG, "Doi hinh nen loi: " + t);
      return false;
    } finally {
      if (crop != null && crop != src) crop.recycle();
      if (out != null && out != crop) out.recycle();
      if (src != null) src.recycle();
    }
  }

  // ---- Nhan giu nut Cai dat: tuy chon launcher ----
  private void showLauncherMenu() {
    CharSequence[] items = {
        getString(R.string.menu_wallpaper),
        getString(R.string.menu_wallpaper_reset),
        getString(showHidden ? R.string.menu_hide_hidden : R.string.menu_show_hidden)
    };
    new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
        .setTitle(R.string.menu_title)
        .setItems(items, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface d, int which) {
            if (which == 0) chooseWallpaper();
            else if (which == 1) resetWallpaper();
            else {
              showHidden = !showHidden;
              refreshDisplay(0);
            }
          }
        })
        .show();
  }

  // ---- Nhan giu Enter tren app: Di chuyen / An / Hien lai ----
  private void showAppMenu(final ApplicationAdapter.Entry entry) {
    final boolean isHidden = hiddenSet.contains(entry.packageName);
    CharSequence[] items = {
        getString(R.string.menu_move),
        getString(isHidden ? R.string.menu_unhide : R.string.menu_hide)
    };
    new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
        .setTitle(entry.label)
        .setItems(items, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface d, int which) {
            if (which == 0) {
              startMove(entry.packageName);
            } else {
              int pos = adapter.indexOf(entry.packageName);
              if (isHidden) hiddenSet.remove(entry.packageName);
              else hiddenSet.add(entry.packageName);
              prefs.edit().putStringSet(KEY_HIDDEN, new HashSet<>(hiddenSet)).apply();
              refreshDisplay(Math.max(pos, 0));
            }
          }
        })
        .show();
  }

  // ---- Che do di chuyen vi tri: phim mui ten de doi cho, OK de xong ----
  private void startMove(String pkg) {
    movingPkg = pkg;
    adapter.setMoving(pkg);
    moveHint.setVisibility(View.VISIBLE);
    refocus(adapter.indexOf(pkg));
  }

  private void endMove() {
    adapter.setMoving(null);
    movingPkg = null;
    moveHint.setVisibility(View.GONE);
    saveOrder();
  }

  private void moveBy(int delta) {
    int pos = adapter.indexOf(movingPkg);
    int to = pos + delta;
    if (pos < 0 || to < 0 || to >= adapter.getItemCount()) return;
    ApplicationAdapter.Entry a = adapter.getEntry(pos);
    ApplicationAdapter.Entry b = adapter.getEntry(to);
    int ia = master.indexOf(a), ib = master.indexOf(b);
    if (ia >= 0 && ib >= 0) Collections.swap(master, ia, ib);
    adapter.swap(pos, to);
    refocus(to);
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent e) {
    if (movingPkg != null) {
      boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
      switch (e.getKeyCode()) {
        case KeyEvent.KEYCODE_DPAD_LEFT:
          if (down) moveBy(-1);
          return true;
        case KeyEvent.KEYCODE_DPAD_RIGHT:
          if (down) moveBy(1);
          return true;
        case KeyEvent.KEYCODE_DPAD_UP:
          if (down) moveBy(-SPAN);
          return true;
        case KeyEvent.KEYCODE_DPAD_DOWN:
          if (down) moveBy(SPAN);
          return true;
        case KeyEvent.KEYCODE_DPAD_CENTER:
        case KeyEvent.KEYCODE_ENTER:
        case KeyEvent.KEYCODE_NUMPAD_ENTER:
        case KeyEvent.KEYCODE_BACK:
          if (down && e.getRepeatCount() == 0) endMove();
          return true;
        default:
          break;
      }
    }
    return super.dispatchKeyEvent(e);
  }

  private void saveOrder() {
    StringBuilder sb = new StringBuilder();
    for (ApplicationAdapter.Entry en : master) {
      if (sb.length() > 0) sb.append('\n');
      sb.append(en.packageName);
    }
    prefs.edit().putString(KEY_ORDER, sb.toString()).apply();
  }

  // ---- Tai danh sach ung dung ----
  private void loadApps() {
    final int gen = ++loadGeneration;
    try {
      loader.execute(new Runnable() {
        @Override
        public void run() {
          final List<ApplicationAdapter.Entry> result = queryApps();
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              if (gen != loadGeneration || isDestroyed()) return;
              master = result;
              boolean first = adapter.getItemCount() == 0;
              refreshDisplay(first ? 0 : -1);
            }
          });
        }
      });
    } catch (Exception ignored) {
      // executor da dong (dang thoat)
    }
  }

  private List<ApplicationAdapter.Entry> queryApps() {
    List<ResolveInfo> launchables = new ArrayList<>();

    Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
    launchables.addAll(packageManager.queryIntentActivities(mainIntent, 0));

    Intent leanbackIntent = new Intent(Intent.ACTION_MAIN, null);
    leanbackIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
    launchables.addAll(packageManager.queryIntentActivities(leanbackIntent, 0));

    Set<String> packageNames = new HashSet<>();
    List<ApplicationAdapter.Entry> list = new ArrayList<>();
    for (ResolveInfo ri : launchables) {
      String pkg = ri.activityInfo.packageName;
      if (getPackageName().equals(pkg)) continue;           // khong hien chinh launcher
      if (!packageNames.add(pkg)) continue;                  // moi app chi 1 o
      CharSequence label = ri.activityInfo.applicationInfo.loadLabel(packageManager);
      list.add(new ApplicationAdapter.Entry(pkg, label, ri.activityInfo));
    }

    // Mac dinh theo ten; roi ap thu tu nguoi dung da sap xep (app moi cai nam cuoi, theo ten)
    final Collator collator = Collator.getInstance();
    Collections.sort(list, new Comparator<ApplicationAdapter.Entry>() {
      @Override
      public int compare(ApplicationAdapter.Entry a, ApplicationAdapter.Entry b) {
        return collator.compare(a.label.toString(), b.label.toString());
      }
    });
    String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_ORDER, "");
    if (!saved.isEmpty()) {
      final Map<String, Integer> idx = new HashMap<>();
      String[] parts = saved.split("\n");
      for (int i = 0; i < parts.length; i++) idx.put(parts[i], i);
      Collections.sort(list, new Comparator<ApplicationAdapter.Entry>() {   // sort on dinh
        @Override
        public int compare(ApplicationAdapter.Entry a, ApplicationAdapter.Entry b) {
          Integer ia = idx.get(a.packageName), ib = idx.get(b.packageName);
          int x = ia == null ? Integer.MAX_VALUE : ia;
          int y = ib == null ? Integer.MAX_VALUE : ib;
          return x < y ? -1 : (x > y ? 1 : 0);
        }
      });
    }
    return list;
  }

  /** Dung lai luoi tu master (bo app an neu khong bat che do hien app an). */
  private void refreshDisplay(int focusPos) {
    List<ApplicationAdapter.Entry> shown = new ArrayList<>();
    for (ApplicationAdapter.Entry en : master) {
      if (showHidden || !hiddenSet.contains(en.packageName)) shown.add(en);
    }
    adapter.setItems(shown);
    if (focusPos >= 0 && !shown.isEmpty()) {
      refocus(Math.min(focusPos, shown.size() - 1));
    }
  }

  private void refocus(final int pos) {
    if (pos < 0) return;
    layoutManager.scrollToPosition(pos);
    grid.post(new Runnable() {
      @Override
      public void run() {
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) vh.itemView.requestFocus();
      }
    });
  }
}
