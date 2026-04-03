package com.gugesoft.lodibookreader;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_BOOK_REQUEST = 1;

    private RecyclerView recyclerView;
    private SentenceAdapter adapter;
    private List<Sentence> sentences = new ArrayList<>();

    private TTSPlayer ttsPlayer;
    private LodiStepTimer timerManager;
    private ShakeDetector shakeDetector;

    private FloatingActionButton playFab, pauseFab, rewindFab, closeFab;

    private final BroadcastReceiver mediaReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ReadingService.ACTION_PLAY.equals(action)) {
                playBook();
            } else if (ReadingService.ACTION_PAUSE.equals(action)) {
                pauseBook();
            } else if (ReadingService.ACTION_REWIND.equals(action)) {
                rewindSentence();
            } else if (ReadingService.ACTION_CLOSE.equals(action)) {
                stopTtsOnly();
                finish(); // CLOSE APP
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        // RecyclerView setup
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new SentenceAdapter(sentences, position -> {
            ttsPlayer.playFrom(position);
            updateService(true);
        });

        recyclerView.setAdapter(adapter);

        // Core systems
        VolumeController vc = new VolumeController(this);

        timerManager = new LodiStepTimer(vc, this::pauseBook);

        ttsPlayer = new TTSPlayer(this, new TTSPlayer.OnTTSListener() {
            @Override
            public void onSentenceChanged(int index) {
                adapter.setHighlighted(index);
                recyclerView.scrollToPosition(index);
            }

            @Override
            public void onFinished() {
                pauseBook();
            }
        });

        shakeDetector = new ShakeDetector(this, () -> timerManager.handleShake());

        // Buttons
        findViewById(R.id.loadBookBtn).setOnClickListener(v -> pickBook());

        playFab = findViewById(R.id.playFab);
        pauseFab = findViewById(R.id.pauseFab);
        rewindFab = findViewById(R.id.rewindFab);
        closeFab = findViewById(R.id.closeFab);

        playFab.setOnClickListener(v -> playBook());
        pauseFab.setOnClickListener(v -> pauseBook());
        rewindFab.setOnClickListener(v -> rewindSentence());

        closeFab.setOnClickListener(v -> {
            stopTtsOnly();
            stopService(new Intent(this, ReadingService.class));
            finish();
        });

        // Receiver for notification actions
        IntentFilter filter = new IntentFilter();
        filter.addAction(ReadingService.ACTION_PLAY);
        filter.addAction(ReadingService.ACTION_PAUSE);
        filter.addAction(ReadingService.ACTION_REWIND);
        filter.addAction(ReadingService.ACTION_CLOSE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(mediaReceiver, filter);
        }
    }

    private void pickBook() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        String[] mimeTypes = {"text/plain", "application/epub+zip"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        startActivityForResult(intent, PICK_BOOK_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_BOOK_REQUEST && resultCode == RESULT_OK && data != null) {

            Uri uri = data.getData();

            if (uri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );

                    List<Sentence> loaded = new BookLoader().load(this, uri);

                    if (loaded != null && !loaded.isEmpty()) {
                        sentences.clear();
                        sentences.addAll(loaded);
                        adapter.notifyDataSetChanged();

                        ttsPlayer.loadSentences(sentences);

                        Toast.makeText(this, "Book Loaded!", Toast.LENGTH_SHORT).show();
                    }

                } catch (Exception e) {
                    Toast.makeText(this, "Error loading file", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void playBook() {
        ttsPlayer.play();
        timerManager.start(15 * 60 * 1000);
        updateService(true);
    }

    private void pauseBook() {
        ttsPlayer.pause();
        timerManager.stop();
        updateService(false);
    }

    private void rewindSentence() {
        int current = ttsPlayer.getCurrentIndex();
        int target = Math.max(0, current - 1);
        ttsPlayer.playFrom(target);
        updateService(true);
    }

    private void stopTtsOnly() {
        ttsPlayer.stop();
        timerManager.stop();
        updateService(false);
    }

    private void updateService(boolean isPlaying) {
        Intent intent = new Intent(this, ReadingService.class);
        intent.putExtra("IS_PLAYING", isPlaying);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        shakeDetector.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        shakeDetector.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(mediaReceiver);
        } catch (Exception ignored) {}

        ttsPlayer.release();
    }
}