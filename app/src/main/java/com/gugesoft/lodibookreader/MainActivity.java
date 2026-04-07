package com.gugesoft.lodibookreader;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
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
    private final List<Sentence> sentences = new ArrayList<>();

    private TTSPlayer ttsPlayer;
    private LodiStepTimer timerManager;
    private ShakeDetector shakeDetector;

    private BookRepository bookRepo;
    private String currentBookUri;

    private MaterialButton timerToggleButton;
    private CardView bottomControls;
    private LinearLayout topBar;
    private FloatingActionButton playPauseFab;
    private boolean isTimerEnabled = true;

    private boolean wasPlayingBeforeCall = false;

    private final BroadcastReceiver mediaReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ReadingService.ACTION_PLAY.equals(action)) playBook();
            else if (ReadingService.ACTION_PAUSE.equals(action)) pauseBook();
            else if (ReadingService.ACTION_REWIND.equals(action)) rewindSentence();
            else if (ReadingService.ACTION_FORWARD.equals(action)) forwardSentence();
            else if (ReadingService.ACTION_CLOSE.equals(action)) {
                stopTtsOnly();
            }
        }
    };

    private PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String phoneNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    if (ttsPlayer != null && ttsPlayer.isPlaying()) {
                        wasPlayingBeforeCall = true;
                        pauseBook();
                    }
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    if (wasPlayingBeforeCall) {
                        wasPlayingBeforeCall = false;
                        playBook();
                    }
                    break;
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        settings = new SettingsManager(this);
        bookRepo = new BookRepository(this);

        bottomControls = findViewById(R.id.bottomControls);
        topBar = findViewById(R.id.topBar);
        timerToggleButton = findViewById(R.id.timerToggleButton);
        recyclerView = findViewById(R.id.recyclerView);

        showControls();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

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

            @Override
            public void onSingleTap() {
                if (topBar.getVisibility() == View.VISIBLE) {
                    hideControls();
                } else {
                    showControls();
                }
            }
        }, settings);

        recyclerView.setAdapter(adapter);

        VolumeController vc = new VolumeController(this);
        vc.captureBaselineVolume();
        
        getContentResolver().registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        super.onChange(selfChange);
                        if (!vc.isAutoChanging()) {
                            vc.captureBaselineVolume();
                        }
                    }
                }
        );

        timerManager = new LodiStepTimer(vc, () -> {
            stopTtsOnly();
            stopService(new Intent(this, ReadingService.class));
            finish();
        });
        timerManager.setTimerListener(remainingMs -> runOnUiThread(() -> updateTimerButtonText(remainingMs)));

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

        shakeDetector = new ShakeDetector(this,
                settings.getShakeIntensity(),
                () -> {
                    if (isTimerEnabled && ttsPlayer.isPlaying()) {
                        timerManager.handleShake();
                    }
                });

        findViewById(R.id.loadBookBtn).setOnClickListener(v -> pickBook());
        findViewById(R.id.openShelfBtn).setOnClickListener(v ->
                startActivity(new Intent(this, BookshelfActivity.class))
        );
        findViewById(R.id.openSettingsBtn).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class))
        );

        timerToggleButton.setOnClickListener(v -> {
            isTimerEnabled = !isTimerEnabled;
            if (!isTimerEnabled) {
                timerManager.stop();
                timerToggleButton.setText("Off");
                timerToggleButton.setTextColor(0xFF333333);
            } else {
                if (ttsPlayer.isPlaying()) {
                    startTimerWithCurrentSettings();
                } else {
                    timerToggleButton.setText("On");
                    timerToggleButton.setTextColor(0xFF2196F3);
                }
            }
        });

        playPauseFab = findViewById(R.id.playPauseFab);
        FloatingActionButton rewindFab = findViewById(R.id.rewindFab);
        FloatingActionButton forwardFab = findViewById(R.id.forwardFab);
        FloatingActionButton closeFab = findViewById(R.id.closeFab);

        playPauseFab.setOnClickListener(v -> {
            if (ttsPlayer.isPlaying()) pauseBook();
            else playBook();
        });
        rewindFab.setOnClickListener(v -> rewindSentence());
        forwardFab.setOnClickListener(v -> forwardSentence());
        closeFab.setOnClickListener(v -> {
            stopTtsOnly();
            stopService(new Intent(this, ReadingService.class));
            finish();
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(ReadingService.ACTION_PLAY);
        filter.addAction(ReadingService.ACTION_PAUSE);
        filter.addAction(ReadingService.ACTION_REWIND);
        filter.addAction(ReadingService.ACTION_FORWARD);
        filter.addAction(ReadingService.ACTION_CLOSE);
        registerReceiver(mediaReceiver, filter);

        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            tm.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }

        /*String uriFromShelf = getIntent().getStringExtra("BOOK_URI");
        if (uriFromShelf != null) {
            loadBookFromUri(Uri.parse(uriFromShelf));
        }*/
        String uriFromShelf = getIntent().getStringExtra("BOOK_URI");

        String bookUriToLoad = (uriFromShelf != null) ? uriFromShelf : settings.getLastOpenedBookUri();

        if (bookUriToLoad != null) {
            Uri bookUri = Uri.parse(bookUriToLoad);

            // Load book normally
            loadBookFromUri(bookUri);

            // After loading, restore last read position
            int lastIndex = settings.getLastReadSentenceIndex(bookUriToLoad); // <-- replace with your method
            if (lastIndex > 0) {
                // Scroll or highlight in your RecyclerView / TextView
                recyclerView.scrollToPosition(lastIndex); // or whatever your list is
                // If you have TTS, set its index
                ttsPlayer.setCurrentIndex(lastIndex); // only if you use TTS
            }
        }

        applyAppearance();
    }

    private void showControls() {
        if (topBar != null) topBar.setVisibility(View.VISIBLE);
        if (bottomControls != null) bottomControls.setVisibility(View.VISIBLE);
        if (timerToggleButton != null) timerToggleButton.setVisibility(View.VISIBLE);
    }

    private void hideControls() {
        if (topBar != null) topBar.setVisibility(View.GONE);
        if (bottomControls != null) bottomControls.setVisibility(View.GONE);
        if (timerToggleButton != null) timerToggleButton.setVisibility(View.GONE);
    }

    private void applyAppearance() {
        if (recyclerView != null) {
            recyclerView.setBackgroundColor(settings.getPaperColor());
        }
        View root = findViewById(R.id.rootLayout);
        if (root != null) {
            root.setBackgroundColor(settings.getPaperColor());
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void updateTimerButtonText(long remainingMs) {
        if (!isTimerEnabled || timerToggleButton == null) return;
        
        int minutes = (int) (remainingMs / 1000) / 60;
        int seconds = (int) (remainingMs / 1000) % 60;
        String time = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        timerToggleButton.setText(time);
        
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
        settings.setLastOpenedBookUri(uri.toString());
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
        if (playPauseFab != null) {
            playPauseFab.setImageResource(android.R.drawable.ic_media_pause);
        }
    }

    private void pauseBook() {
        ttsPlayer.pause();
        timerManager.stop();
        updateService(false);
        if (playPauseFab != null) {
            playPauseFab.setImageResource(android.R.drawable.ic_media_play);
        }
        if (isTimerEnabled && timerToggleButton != null) {
             timerToggleButton.setText("On");
             timerToggleButton.setTextColor(0xFF333333);
        }
    }

    private void rewindSentence() {
        int target = Math.max(0, ttsPlayer.getCurrentIndex() - 1);
        ttsPlayer.playFrom(target);
        updateService(ttsPlayer.isPlaying());
    }

    private void forwardSentence() {
        int target = Math.min(sentences.size() - 1, ttsPlayer.getCurrentIndex() + 1);
        ttsPlayer.playFrom(target);
        updateService(ttsPlayer.isPlaying());
    }

    private void stopTtsOnly() {
        ttsPlayer.stop();
        timerManager.stop();
        updateService(false);
        if (playPauseFab != null) {
            playPauseFab.setImageResource(android.R.drawable.ic_media_play);
        }
        if (isTimerEnabled && timerToggleButton != null) {
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
        applyAppearance();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //shakeDetector.stop();
        if (currentBookUri != null && ttsPlayer != null) {
            settings.setLastReadSentenceIndex(currentBookUri.toString(), ttsPlayer.getCurrentIndex());
        }
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
        if (currentBookUri != null && ttsPlayer != null) {
            // Save last read sentence
            settings.setLastReadSentenceIndex(currentBookUri, ttsPlayer.getCurrentIndex());
        }
        unregisterReceiver(mediaReceiver);
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            tm.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
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