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
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppDrawerActivity extends Activity {
  private static final String TAG = "QuangDrawer";

  private PackageManager packageManager;
  private ApplicationAdapter adapter;
  private RecyclerView grid;
  private TextView loadingView;
  private TextView countView;
  private int loadGeneration = 0;
  private final ExecutorService loader = Executors.newSingleThreadExecutor();

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
    // khong lam gi
  }

  private int dp(int v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  // Cai dat/go app -> cap nhat luoi
  private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      loadApps();
    }
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    packageManager = getPackageManager();

    grid = (RecyclerView) findViewById(R.id.grid);
    loadingView = (TextView) findViewById(R.id.loading);
    countView = (TextView) findViewById(R.id.apps_count);

    // So cot va kich thuoc the theo chieu rong man hinh (the ti le 16:9)
    DisplayMetrics dm = getResources().getDisplayMetrics();
    int contentW = dm.widthPixels - 2 * dp(48);
    int span = Math.max(3, contentW / dp(200));
    int colW = contentW / span;
    int tileW = colW - 2 * dp(7);
    int tileH = tileW * 9 / 16;

    adapter = new ApplicationAdapter(this, tileH, new ApplicationAdapter.OnAppClick() {
      @Override
      public void onClick(ApplicationAdapter.Entry entry) {
        launch(entry.packageName);
      }
    });
    grid.setLayoutManager(new GridLayoutManager(this, span));
    grid.setAdapter(adapter);
    grid.setItemAnimator(null);

    IntentFilter f = new IntentFilter();
    f.addAction(Intent.ACTION_PACKAGE_ADDED);
    f.addAction(Intent.ACTION_PACKAGE_REMOVED);
    f.addAction(Intent.ACTION_PACKAGE_REPLACED);
    f.addDataScheme("package");
    registerReceiver(packageReceiver, f);

    loadApps();
  }

  @Override
  protected void onDestroy() {
    try { unregisterReceiver(packageReceiver); } catch (Exception ignored) { }
    loader.shutdownNow();
    super.onDestroy();
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

  /** Quet danh sach ung dung o luong nen (nhan, icon, banner), xong thi do len luoi. */
  private void loadApps() {
    final int gen = ++loadGeneration;
    if (adapter.getItemCount() == 0) loadingView.setVisibility(View.VISIBLE);
    try {
      loader.execute(new Runnable() {
        @Override
        public void run() {
          final List<ApplicationAdapter.Entry> result = queryApps();
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              if (gen != loadGeneration || isDestroyed()) return;
              showApps(result);
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
      Drawable icon = ri.activityInfo.applicationInfo.loadIcon(packageManager);
      Drawable banner = null;
      try { banner = ri.activityInfo.loadBanner(packageManager); } catch (Throwable ignored) { }
      list.add(new ApplicationAdapter.Entry(pkg, label, icon, banner));
    }

    final Collator collator = Collator.getInstance();
    Collections.sort(list, new Comparator<ApplicationAdapter.Entry>() {
      @Override
      public int compare(ApplicationAdapter.Entry a, ApplicationAdapter.Entry b) {
        return collator.compare(a.label.toString(), b.label.toString());
      }
    });
    return list;
  }

  private void showApps(List<ApplicationAdapter.Entry> list) {
    adapter.setItems(list);
    loadingView.setVisibility(View.GONE);
    countView.setText(getString(R.string.apps_count, list.size()));
    // chon san o dau tien de dieu khien tu xa dung duoc ngay
    if (!list.isEmpty() && getCurrentFocus() == null) {
      grid.post(new Runnable() {
        @Override
        public void run() {
          RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(0);
          if (vh != null) vh.itemView.requestFocus();
        }
      });
    }
  }
}
