package com.musictagger.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.musictagger.app.R;
import com.musictagger.app.databinding.ActivityMatchReviewBinding;
import com.musictagger.app.model.MatchItem;
import com.musictagger.app.model.Track;
import com.musictagger.app.provider.ProviderHub;
import com.musictagger.app.service.AutoTagger;
import com.musictagger.app.service.BatchSession;
import com.musictagger.app.util.DuplicateUtil;
import com.musictagger.app.util.Settings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Two-phase batch flow: search everything first, show the matches with covers,
 * let the user pick alternatives, then write only the non-duplicate entries.
 */
public class MatchReviewActivity extends AppCompatActivity {

    private ActivityMatchReviewBinding binding;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private List<MatchItem> items;
    private MatchAdapter adapter;
    private volatile boolean cancelled;
    private boolean writing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMatchReviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> {
            if (!writing) {
                cancelled = true;
                finish();
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (writing) {
                    return;
                }
                cancelled = true;
                finish();
            }
        });

        items = BatchSession.items();
        if (items.isEmpty()) {
            finish();
            return;
        }
        adapter = new MatchAdapter(items, this::onItemClick);
        binding.list.setLayoutManager(new LinearLayoutManager(this));
        binding.list.setAdapter(adapter);
        binding.btnWrite.setOnClickListener(v -> writeAll());
        startSearch();
    }

    private void startSearch() {
        binding.progress.setVisibility(View.VISIBLE);
        binding.progress.setIndeterminate(true);
        binding.btnWrite.setEnabled(false);
        binding.summary.setText(R.string.searching);
        io.execute(() -> {
            final StringBuilder healthText = new StringBuilder();
            boolean any = false;
            for (ProviderHub.Health h : ProviderHub.probe()) {
                healthText.append(h.label()).append('\n');
                if (h.ok) {
                    any = true;
                }
            }
            if (!any) {
                main.post(() -> {
                    binding.progress.setVisibility(View.GONE);
                    new MaterialAlertDialogBuilder(MatchReviewActivity.this)
                            .setTitle("没有可用的歌词/封面来源")
                            .setMessage(healthText + "\n检查网络后重试。")
                            .setPositiveButton("关闭", (d, w) -> finish())
                            .setCancelable(false)
                            .show();
                });
                return;
            }
            int threads = Settings.searchThreads(this);
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CompletionService<Object[]> completion = new ExecutorCompletionService<>(pool);
            for (int i = 0; i < items.size(); i++) {
                final int index = i;
                final MatchItem item = items.get(i);
                completion.submit(() -> new Object[]{index, AutoTagger.match(this, item.file)});
            }
            int finished = 0;
            try {
                for (int i = 0; i < items.size(); i++) {
                    if (cancelled) {
                        break;
                    }
                    Future<Object[]> future = completion.take();
                    Object[] result = future.get();
                    final int index = (Integer) result[0];
                    applyOutcome(items.get(index), (AutoTagger.MatchOutcome) result[1]);
                    finished++;
                    final int doneCount = finished;
                    main.post(() -> {
                        binding.summary.setText(getString(R.string.searching_n, doneCount, items.size()));
                        adapter.notifyItemChanged(index);
                    });
                }
            } catch (Exception ignored) {
                // Cancelled or interrupted: keep whatever finished.
            } finally {
                pool.shutdownNow();
            }
            main.post(() -> {
                binding.progress.setVisibility(View.GONE);
                binding.summary.setText(R.string.review_hint);
                recomputeAll();
            });
        });
    }

    private void applyOutcome(MatchItem item, AutoTagger.MatchOutcome outcome) {
        item.existingTitle = outcome.existingTitle;
        item.existingArtist = outcome.existingArtist;
        item.candidates = outcome.candidates;
        if (outcome.error != null) {
            item.status = MatchItem.ERROR;
            item.note = outcome.error;
            item.chosen = -1;
        } else if (outcome.candidates.isEmpty()) {
            item.status = MatchItem.NO_MATCH;
            item.chosen = -1;
        } else {
            item.chosen = 0;
        }
    }

    /** Re-evaluates duplicates after every candidate change. */
    private void recomputeAll() {
        Set<String> seen = new HashSet<>();
        int ready = 0;
        for (MatchItem item : items) {
            if (item.status == MatchItem.ERROR) {
                continue;
            }
            if (item.chosen < 0 || item.chosenTrack() == null) {
                item.status = MatchItem.NO_MATCH;
                item.note = "没有候选，点一下可以重新搜索";
                continue;
            }
            Track track = item.chosenTrack();
            if (DuplicateUtil.sameSong(item.existingTitle, item.existingArtist, track)) {
                item.status = MatchItem.DUPLICATE_TAG;
                item.note = "已有相同标签，跳过";
            } else {
                String key = track.source + ":" + track.id;
                if (!seen.add(key)) {
                    item.status = MatchItem.DUPLICATE_BATCH;
                    item.note = "与列表内其他文件匹配到同一首";
                } else {
                    item.status = MatchItem.READY;
                    item.note = "";
                }
            }
            if (item.status == MatchItem.READY) {
                ready++;
            }
        }
        adapter.notifyDataSetChanged();
        updateCount(ready);
    }

    private void updateCount(int ready) {
        binding.count.setText(getString(R.string.ready_count, ready, items.size()));
        binding.btnWrite.setEnabled(!writing && ready > 0);
        binding.btnWrite.setText(getString(R.string.write_selected_n, ready));
    }

    private void onItemClick(MatchItem item) {
        if (writing) {
            return;
        }
        if (item.candidates == null || item.candidates.isEmpty()) {
            snack("这首没有候选，可以回到列表重新匹配");
            return;
        }
        CandidatePickerDialog.show(this, item.fileName, item.candidates, item.chosen, (index, track) -> {
            item.chosen = index;
            recomputeAll();
        });
    }

    private void writeAll() {
        final List<MatchItem> ready = new ArrayList<>();
        for (MatchItem item : items) {
            if (item.writable()) {
                ready.add(item);
            }
        }
        if (ready.isEmpty()) {
            snack("没有需要写入的歌曲");
            return;
        }
        writing = true;
        binding.btnWrite.setEnabled(false);
        binding.progress.setIndeterminate(false);
        binding.progress.setMax(ready.size());
        binding.progress.setProgress(0);
        binding.progress.setVisibility(View.VISIBLE);

        io.execute(() -> {
            int ok = 0;
            int fail = 0;
            final StringBuilder errors = new StringBuilder();
            for (int i = 0; i < ready.size() && !cancelled; i++) {
                final MatchItem item = ready.get(i);
                final int index = i + 1;
                main.post(() -> binding.summary.setText(
                        getString(R.string.writing_n, index, ready.size()) + " · " + item.fileName));
                AutoTagger.Result result = AutoTagger.write(this, BatchSession.treeUri(),
                        item.file, item.chosenTrack(), null);
                if (result.ok) {
                    ok++;
                    item.note = "已写入";
                } else {
                    fail++;
                    errors.append(item.fileName).append("：").append(result.message).append('\n');
                }
                final int done = index;
                main.post(() -> binding.progress.setProgress(done));
            }
            final int okFinal = ok;
            final int failFinal = fail;
            final String detail = errors.toString();
            main.post(() -> {
                writing = false;
                binding.progress.setVisibility(View.GONE);
                new MaterialAlertDialogBuilder(MatchReviewActivity.this)
                        .setTitle(R.string.write_done)
                        .setMessage(getString(R.string.write_summary, okFinal, failFinal)
                                + (detail.isEmpty() ? "" : "\n\n" + detail))
                        .setPositiveButton("完成", (d, w) -> {
                            BatchSession.clear();
                            finish();
                        })
                        .setCancelable(false)
                        .show();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelled = true;
    }

    private void snack(String msg) {
        Snackbar.make(binding.getRoot(), msg, Snackbar.LENGTH_SHORT).show();
    }
}
