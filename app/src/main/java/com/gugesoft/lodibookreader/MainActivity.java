package com.gugesoft.lodibookreader;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_BOOK_REQUEST = 1;
    private SettingsManager settings;
    private RecyclerView recyclerView;
    private SentenceAdapter adapter;
    private List<Sentence> sentences = new ArrayList<>();

    private TTSPlayer ttsPlayer;
    private LodiStepTimer timerManager;
    private ShakeDetector shakeDetector;

    private BookRepository bookRepo;
    private String currentBookUri;
    private boolean isBookLoaded = false;

    private FloatingActionButton playFab, pauseFab, rewindFab, closeFab;
    private MaterialButton timerToggleButton;
    private boolean isTimerEnabled = true;
    private MediaSessionCompat mediaSession;
    private LinearLayout topBar;
    private CardView bottomControls;
    private ContentObserver volumeObserver;
    private Handler hideHandler = new Handler(Looper.getMainLooper());
    private Runnable hideRunnable = () -> {
        topBar.setVisibility(View.GONE);
        bottomControls.setVisibility(View.GONE);
    };
    public void setBookLoaded(boolean loaded) {
        isBookLoaded = loaded;
    }
    public boolean getBookLoaded() {
        return isBookLoaded;
    }
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = new SettingsManager(this);
        bookRepo = new BookRepository(this);

        // Initialize views
        topBar = findViewById(R.id.topBar);
        bottomControls = findViewById(R.id.bottomControls);
        recyclerView = findViewById(R.id.recyclerView);
        timerToggleButton = findViewById(R.id.timerToggleButton);

        // Start hidden
        topBar.setVisibility(View.VISIBLE);
        bottomControls.setVisibility(View.VISIBLE);

        // RecyclerView setup
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new SentenceAdapter(sentences, new SentenceAdapter.OnSentenceClickListener() {
            @Override
            public void onSentenceClick(int position) {
                ttsPlayer.playFrom(position);
                startTimerWithCurrentSettings();
                updateService(true);
            }

            @Override
            public void onNavigateTo(int position) {
                recyclerView.scrollToPosition(position);
            }
        });
        recyclerView.setAdapter(adapter);

        // Timer setup
        VolumeController vc = new VolumeController(this);
        if (!vc.isAutoChanging()) {
            vc.captureBaselineVolume();
        }
        volumeObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                if (!vc.isAutoChanging()) {
                    vc.captureBaselineVolume();
                }
            }
        };
        getContentResolver().registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                volumeObserver
        );

        timerManager = new LodiStepTimer(vc, this::pauseBook);
        timerManager.setTimerListener(remainingMs -> runOnUiThread(() -> updateTimerButtonText(remainingMs)));

        // TTS setup
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

        // Shake detector
        shakeDetector = new ShakeDetector(this,
                settings.getShakeIntensity(),
                () -> {
                    if (isTimerEnabled) {
                        // Capture baseline volume before fading begins
                        timerManager.setResetTimeMs(settings.getTimerMs());
                        timerManager.start(settings.getTimerMs());
                        timerManager.handleShake();
                    }
                });


        // Top bar buttons
        findViewById(R.id.loadBookBtn).setOnClickListener(v -> pickBook());
        findViewById(R.id.openShelfBtn).setOnClickListener(v ->
                startActivity(new Intent(this, BookshelfActivity.class)));
        findViewById(R.id.openSettingsBtn).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // Timer toggle button logic
        timerToggleButton.setOnClickListener(v -> {
            isTimerEnabled = !isTimerEnabled;
            if (!isTimerEnabled) {
                timerManager.stop();
                timerToggleButton.setText("Off");
                timerToggleButton.setTextColor(0xFF333333);
            } else {
                if (ttsPlayer != null && ttsPlayer.isPlaying()) {
                    startTimerWithCurrentSettings();
                } else {
                    timerToggleButton.setText("On");
                    timerToggleButton.setTextColor(0xFF2196F3);
                }
            }
        });

        // Bottom controls
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

        // Media session setup
        mediaSession = new MediaSessionCompat(this, "LodiReaderSession");
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
                .build();
        mediaSession.setPlaybackState(state);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                if (!ttsPlayer.isPlaying()) {
                    ttsPlayer.play();
                }
                toggleControlsHide(topBar);
                toggleControlsHide(bottomControls);
            }

            @Override
            public void onPause() {
                if (ttsPlayer.isPlaying()) {
                    ttsPlayer.pause();
                }
                toggleControlsShow(topBar);
                toggleControlsShow(bottomControls);
            }
        });

        mediaSession.setActive(true);

    }

    /** Show/hide top and bottom controls with auto-hide */
    public void toggleControlsHide(View view) {
        view.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> view.setVisibility(View.GONE));
    }

    public void toggleControlsShow(View view) {
        view.setAlpha(0f); // start transparent
        view.setVisibility(View.VISIBLE); // make sure it's visible
        view.animate()
                .alpha(1f) // fade in to fully visible
                .setDuration(300);
    }



    private void togglePlayPause() {
        if (ttsPlayer.isPlaying()) {
            ttsPlayer.pause();
        } else {
            ttsPlayer.play();
        }
    }

    private void updateTimerButtonText(long remainingMs) {
        if (!isTimerEnabled) return;
        
        int minutes = (int) (remainingMs / 1000) / 60;
        int seconds = (int) (remainingMs / 1000) % 60;
        String time = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        timerToggleButton.setText(time);
        
        // Change color to blue if active
        timerToggleButton.setTextColor(0xFF2196F3);
    }

    private void startTimerWithCurrentSettings() {
        if (!isTimerEnabled) {
            timerManager.stop();
            return;
        }
        timerManager.setResetTimeMs(settings.getTimerMs());
        timerManager.setFadeStartMs(settings.getFadeMs());
        timerManager.start(settings.getTimerMs());
    }

    private void pickBook() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"text/plain", "application/epub+zip"});
        startActivityForResult(intent, PICK_BOOK_REQUEST);
    }

    private void loadBookFromUri(Uri uri) {
        cleanupPreviousBook();
        Toast.makeText(this, "Loading book...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                currentBookUri = uri.toString();
                BookLoader.BookMetadata meta = new BookLoader().loadBookWithMetadata(this, uri);

                runOnUiThread(() -> {
                    if (meta.sentences == null || meta.sentences.isEmpty()) {
                        Toast.makeText(this, "Empty or unreadable book", Toast.LENGTH_LONG).show();
                        return;
                    }

                    sentences.clear();
                    sentences.addAll(meta.sentences);
                    adapter.notifyDataSetChanged();
                    ttsPlayer.loadSentences(sentences);

                    BookItem existing = bookRepo.findBook(currentBookUri);
                    if (existing == null) {
                        existing = new BookItem(currentBookUri, meta.title, meta.author, meta.coverUri, 0);
                        bookRepo.saveOrUpdateBook(existing);
                    } else {
                        existing.title = meta.title;
                        existing.author = meta.author;
                        existing.coverUri = meta.coverUri;
                        bookRepo.saveOrUpdateBook(existing);
                    }

                    int startIndex = existing.lastSentenceIndex;
                    ttsPlayer.setCurrentIndex(startIndex);
                    adapter.setHighlighted(startIndex);
                    recyclerView.scrollToPosition(startIndex);
                    isBookLoaded = true;
                });

            } catch (Exception e) {
                Log.e("MainActivity", "Error loading", e);
                runOnUiThread(() -> Toast.makeText(this, "Load Failed", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_BOOK_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
                loadBookFromUri(uri);
            }
        }
    }

    private void playBook() {
        ttsPlayer.play();
        startTimerWithCurrentSettings();
        updateService(true);
    }

    private void pauseBook() {
        ttsPlayer.pause();
        timerManager.stop();
        updateService(false);
        if (isTimerEnabled) {
             timerToggleButton.setText("On");
             timerToggleButton.setTextColor(0xFF333333);
        }
    }

    private void rewindSentence() {
        int target = Math.max(0, ttsPlayer.getCurrentIndex() - 1);
        ttsPlayer.playFrom(target);
        updateService(true);
    }

    private void stopTtsOnly() {
        ttsPlayer.stop();
        timerManager.stop();
        updateService(false);
        if (isTimerEnabled) {
            timerToggleButton.setText("On");
            timerToggleButton.setTextColor(0xFF333333);
        }
    }

    private void updateService(boolean isPlaying) {
        Intent intent = new Intent(this, ReadingService.class);
        intent.putExtra("IS_PLAYING", isPlaying);
        startService(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        shakeDetector.setShakeThreshold(settings.getShakeIntensity());
        shakeDetector.start();
        // Apply paper color to the whole RecyclerView
        findViewById(R.id.rootLayout).setBackgroundColor(settings.getPaperColor());
        recyclerView.setBackgroundColor(settings.getPaperColor());

        // Refresh adapter so font size/color apply
        adapter.notifyDataSetChanged();
    }


    @Override
    protected void onPause() {
        super.onPause();
        //shakeDetector.stop();

        if (currentBookUri != null && ttsPlayer != null) {
            BookItem existing = bookRepo.findBook(currentBookUri);
            if (existing != null) {
                existing.lastSentenceIndex = ttsPlayer.getCurrentIndex();
                bookRepo.saveOrUpdateBook(existing);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String uriFromShelf = intent.getStringExtra("BOOK_URI");
        if (uriFromShelf != null) {
            loadBookFromUri(Uri.parse(uriFromShelf));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //unregisterReceiver(mediaReceiver);
        if (volumeObserver != null) {
            getContentResolver().unregisterContentObserver(volumeObserver);
        }
        ttsPlayer.release();
    }

    private void cleanupPreviousBook() {
        try {
            if (ttsPlayer != null) ttsPlayer.stop();
            if (timerManager != null) timerManager.stop();
            stopService(new Intent(this, ReadingService.class));
        } catch (Exception e) {
            Log.w("MainActivity", "Cleanup warning", e);
        }
    }
}