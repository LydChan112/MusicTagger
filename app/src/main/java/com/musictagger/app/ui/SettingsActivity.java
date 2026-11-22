package com.musictagger.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;

import com.musictagger.app.R;
import com.musictagger.app.databinding.ActivitySettingsBinding;
import com.musictagger.app.provider.ProviderHub;
import com.musictagger.app.util.CoverLoader;
import com.musictagger.app.util.Settings;

public class SettingsActivity extends AppCompatActivity {

    public static final String REPO_URL = "https://github.com/LydChan112/MusicTagger";

    private ActivitySettingsBinding binding;

    private static final String[] DURATION_LABELS = {
            "关闭（不按时长过滤）", "±3 秒", "±5 秒", "±10 秒", "±20 秒", "±30 秒"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupDuration();
        setupSliders();

        binding.githubUrl.setText(REPO_URL.replace("https://", ""));
        binding.rowGithub.setOnClickListener(v -> openRepo());
        binding.version.setText(versionName());
    }

    private void setupDuration() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, DURATION_LABELS);
        binding.duration.setAdapter(adapter);
        int current = Settings.durationTolerance(this);
        int index = 0;
        for (int i = 0; i < Settings.DURATION_OPTIONS.length; i++) {
            if (Settings.DURATION_OPTIONS[i] == current) {
                index = i;
                break;
            }
        }
        binding.duration.setText(DURATION_LABELS[index], false);
        binding.duration.setOnItemClickListener((parent, view, position, id) ->
                Settings.setDurationTolerance(this, Settings.DURATION_OPTIONS[position]));
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
