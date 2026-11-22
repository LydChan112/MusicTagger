package com.musictagger.app.ui;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.musictagger.app.App;
import com.musictagger.app.R;
import com.musictagger.app.databinding.ActivityMainBinding;
import com.musictagger.app.model.MatchItem;
import com.musictagger.app.provider.ProviderHub;
import com.musictagger.app.service.BatchSession;
import com.musictagger.app.storage.SafStore;
import com.musictagger.app.util.Diag;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<DocumentFile> songs = new ArrayList<>();
    private SongAdapter adapter;
    private ActivityResultLauncher<Uri> picker;
    private Uri treeUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        adapter = new SongAdapter(songs, new SongAdapter.Listener() {
            @Override
            public void onClick(DocumentFile file) {
                if (adapter.isSelectionMode()) {
                    adapter.toggle(file);
                    updateSelectionUi();
                } else {
                    openEditor(file);
                }
            }

            @Override
            public void onLongClick(DocumentFile file) {
                if (!adapter.isSelectionMode()) {
                    adapter.setSelectionMode(true);
                    adapter.toggle(file);
                    updateSelectionUi();
                }
            }
        });
        binding.list.setLayoutManager(new LinearLayoutManager(this));
        binding.list.setAdapter(adapter);

        picker = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(),
                this::onTreePicked);

        View.OnClickListener choose = v -> picker.launch(null);
        binding.fab.setOnClickListener(choose);
        binding.btnPick.setOnClickListener(choose);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (adapter.isSelectionMode()) {
                    exitSelection();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        Uri saved = SafStore.savedTree(this);
        if (saved == null) {
            showEmpty(true);
        } else {
            load(saved);
        }
    }

    private void onTreePicked(Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        SafStore.saveTree(this, uri);
        load(uri);
    }

    private void load(Uri uri) {
        treeUri = uri;
        setBusy(true);
        io.execute(() -> {
            List<DocumentFile> found;
            try {
                found = SafStore.listAll(this, uri);
            } catch (Exception e) {
                found = new ArrayList<>();
            }
            Collator collator = Collator.getInstance(Locale.CHINA);
            found.sort(Comparator.comparing(f -> f.getName() == null ? "" : f.getName(), collator));
            final List<DocumentFile> result = found;
            main.post(() -> {
                setBusy(false);
                songs.clear();
                songs.addAll(result);
                adapter.notifyDataSetChanged();
                showEmpty(result.isEmpty());
                updateSelectionUi();
                if (result.isEmpty()) {
                    snack("这个文件夹里没有找到音频文件");
                } else {
                    snack("已加载 " + result.size() + " 首歌曲");
                }
            });
        });
    }

    private void openEditor(DocumentFile file) {
        Intent i = new Intent(this, EditActivity.class);
        i.putExtra(EditActivity.EXTRA_URI, file.getUri().toString());
        i.putExtra(EditActivity.EXTRA_NAME, file.getName());
        if (treeUri != null) {
            i.putExtra(EditActivity.EXTRA_TREE, treeUri.toString());
        }
        startActivity(i);
    }

    // ---------- selection ----------

    private void updateSelectionUi() {
        boolean selecting = adapter.isSelectionMode();
        if (selecting) {
            binding.toolbar.setTitle(getString(R.string.selected_count, adapter.selectedCount()));
        } else {
            binding.toolbar.setTitle(R.string.app_name);
        }
        binding.fab.setVisibility(selecting ? View.GONE : View.VISIBLE);
        invalidateOptionsMenu();
    }

    private void exitSelection() {
        adapter.setSelectionMode(false);
        updateSelectionUi();
    }

    // ---------- batch review ----------

    private void startReview() {
        final List<DocumentFile> targets = adapter.selectedFiles();
        if (targets.isEmpty()) {
            snack("请先选择歌曲");
            return;
        }
        if (!hasNetwork()) {
            snack("没有网络，无法在线匹配");
            return;
        }
        List<MatchItem> items = new ArrayList<>();
        for (DocumentFile file : targets) {
            MatchItem item = new MatchItem();
            item.file = file;
            item.fileName = file.getName() == null ? "" : file.getName();
            items.add(item);
        }
        BatchSession.set(items, treeUri);
        startActivity(new Intent(this, MatchReviewActivity.class));
        exitSelection();
    }

    private boolean hasNetwork() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return true;
            }
            android.net.Network network = cm.getActiveNetwork();
            if (network == null) {
                return false;
            }
            android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null
                    && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true;
        }
    }

    // ---------- menu ----------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean selecting = adapter != null && adapter.isSelectionMode();
        menu.findItem(R.id.action_select).setVisible(!selecting && !songs.isEmpty());
        menu.findItem(R.id.action_network).setVisible(!selecting);
        menu.findItem(R.id.action_settings).setVisible(!selecting);
        menu.findItem(R.id.action_theme).setVisible(!selecting);
        menu.findItem(R.id.action_select_all).setVisible(selecting);
        menu.findItem(R.id.action_batch).setVisible(selecting);
        menu.findItem(R.id.action_cancel).setVisible(selecting);
        menu.findItem(R.id.action_theme).setChecked(isDark());
        if (selecting && adapter.selectedCount() == songs.size() && !songs.isEmpty()) {
            menu.findItem(R.id.action_select_all).setTitle(R.string.select_none);
        } else {
            menu.findItem(R.id.action_select_all).setTitle(R.string.select_all);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_select) {
            adapter.setSelectionMode(true);
            updateSelectionUi();
            return true;
        }
        if (id == R.id.action_select_all) {
            boolean all = adapter.selectedCount() == songs.size();
            adapter.selectAll(!all);
            updateSelectionUi();
            return true;
        }
        if (id == R.id.action_batch) {
            startReview();
            return true;
        }
        if (id == R.id.action_cancel) {
            exitSelection();
            return true;
        }
        if (id == R.id.action_network) {
            runDiagnostics();
            return true;
        }
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (id == R.id.action_theme) {
            toggleTheme();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void runDiagnostics() {
        setBusy(true);
        snack("正在测试各来源…");
        io.execute(() -> {
            List<ProviderHub.Health> health = ProviderHub.probe();
            final StringBuilder sb = new StringBuilder();
            boolean any = false;
            for (ProviderHub.Health h : health) {
                sb.append(h.label()).append('\n');
                if (h.ok) {
                    any = true;
                }
            }
            if (!any) {
                sb.append("\n所有来源都无法访问：可能是网络限制，换个网络或稍后再试。");
            }
            final String text = sb.toString();
            main.post(() -> {
                setBusy(false);
                new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle(R.string.network_diag)
                        .setMessage(text)
                        .setPositiveButton("复制", (d, w) -> Diag.copy(MainActivity.this, text))
                        .setNegativeButton("关闭", null)
                        .show();
            });
        });
    }

    private boolean isDark() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void toggleTheme() {
        boolean dark = isDark();
        int target = dark ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
        getSharedPreferences(App.PREFS, MODE_PRIVATE).edit().putInt(App.KEY_NIGHT, target).apply();
        AppCompatDelegate.setDefaultNightMode(target);
        recreate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (songs.isEmpty() && treeUri != null) {
            load(treeUri);
        }
    }

    private void showEmpty(boolean empty) {
        binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void setBusy(boolean busy) {
        binding.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) {
            binding.progress.setIndeterminate(true);
        }
    }

    private void snack(String msg) {
        Snackbar.make(binding.getRoot(), msg, Snackbar.LENGTH_SHORT).show();
    }
}
