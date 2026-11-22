package com.musictagger.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.musictagger.app.R;
import com.musictagger.app.databinding.ActivitySettingsBinding;
import com.musictagger.app.provider.ProviderHub;
import com.musictagger.app.util.CoverLoader;
import com.musictagger.app.util.Settings;

public class SettingsActivity extends AppCompatActivity {

    public static final String REPO_URL = "https://github.com/LydChan112/MusicTagger";

    private ActivitySettingsBinding binding;

    private static final String[] SEPARATORS = {"/", "、", "; ", " & ", ", ", " feat. "};
    private static final String CUSTOM = "自定义…";

    private static final String[] COVER_SIZES = {"原图（最大）", "1200 px", "1000 px", "800 px", "500 px", "300 px"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupSliders();
        setupArtistSeparator();
        setupCoverSize();

        binding.githubUrl.setText(REPO_URL.replace("https://", ""));
        binding.rowGithub.setOnClickListener(v -> openRepo());
        binding.version.setText(versionName());
    }

    private void setupCoverSize() {
        binding.coverSize.setAdapter(new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, COVER_SIZES));
        int current = Settings.coverSize(this);
        int index = 0;
        for (int i = 0; i < Settings.COVER_SIZE_OPTIONS.length; i++) {
            if (Settings.COVER_SIZE_OPTIONS[i] == current) {
                index = i;
                break;
            }
        }
        binding.coverSize.setText(COVER_SIZES[index], false);
        binding.coverSize.setOnItemClickListener((parent, view, position, id) -> {
            int pixels = Settings.COVER_SIZE_OPTIONS[position];
            Settings.setCoverSize(this, pixels);
            com.musictagger.app.util.CoverOptions.setSize(pixels);
        });
    }

    private void setupArtistSeparator() {
        String[] labels = new String[SEPARATORS.length + 1];
        System.arraycopy(SEPARATORS, 0, labels, 0, SEPARATORS.length);
        labels[SEPARATORS.length] = CUSTOM;
        binding.artistSeparator.setAdapter(new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, labels));

        String current = Settings.artistSeparator(this);
        int index = -1;
        for (int i = 0; i < SEPARATORS.length; i++) {
            if (SEPARATORS[i].equals(current)) {
                index = i;
            }
        }
        binding.artistSeparator.setText(
                index >= 0 ? SEPARATORS[index] : (current.isEmpty() ? "（空）" : current), false);

        binding.artistSeparator.setOnItemClickListener((parent, view, position, id) -> {
            if (position == SEPARATORS.length) {
                showCustomSeparator();
            } else {
                Settings.setArtistSeparator(this, SEPARATORS[position]);
                binding.artistSeparator.setText(SEPARATORS[position], false);
            }
        });
    }

    private void showCustomSeparator() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setText(Settings.artistSeparator(this));
        input.setSelectAllOnFocus(true);

        android.widget.FrameLayout wrapper = new android.widget.FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        wrapper.setPadding(pad, pad / 2, pad, 0);
        wrapper.addView(input, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_artist_separator_title)
                .setView(wrapper)
                .setPositiveButton("确定", (dialog, which) -> {
                    String value = input.getText().toString();
                    Settings.setArtistSeparator(this, value);
                    binding.artistSeparator.setText(value.isEmpty() ? "（空）" : value, false);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void setupSliders() {
        binding.searchThreads.setValue(Settings.searchThreads(this));
        binding.searchThreads.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                Settings.setSearchThreads(this, Math.round(value));
            }
        });

        binding.downloadThreads.setValue(Settings.downloadThreads(this));
        binding.downloadThreads.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int threads = Math.round(value);
                Settings.setDownloadThreads(this, threads);
                ProviderHub.setDownloadThreads(threads);
                CoverLoader.setThreads(threads);
            }
        });
    }

    private void openRepo() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)));
        } catch (Exception ignored) {
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
