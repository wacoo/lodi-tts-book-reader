package com.gugesoft.lodibookreader;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.media.session.MediaSessionCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.media.session.MediaButtonReceiver;
import android.support.v4.media.session.PlaybackStateCompat;

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
    private final List<BookLoader.TOCItem> currentToc = new ArrayList<>();

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

    private final AudioManager.OnAudioFocusChangeListener focusChangeListener = focusChange -> {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                // Another app (like Spotify) started. Pause permanently.
                if (ttsPlayer != null && ttsPlayer.isPlaying()) pauseBook();
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Short interruption (like a GPS notification). Pause temporarily.
                if (ttsPlayer != null && ttsPlayer.isPlaying()) pauseBook();
                break;

            case AudioManager.AUDIOFOCUS_GAIN:
                // Interruption is over. Resume if we were playing before.
                // (You can add logic here to auto-resume if desired)
                break;
        }
    };

    private final BroadcastReceiver serviceUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case ReadingService.ACTION_PLAY:
                    playBook();
                    break;
                case ReadingService.ACTION_PAUSE:
                    pauseBook();
                    break;
                case ReadingService.ACTION_REWIND:
                    rewindSentence();
                    break;
                case ReadingService.ACTION_FORWARD:
                    forwardSentence();
                    break;
                case ReadingService.ACTION_CLOSE:
                    stopTtsOnly();
                    finish();
                    break;
            }
        }
    };


    private void sendServiceCommand(String action) {
        Intent intent = new Intent(this, ReadingService.class);
        intent.setAction(action);
        startService(intent);
    }

    private final BroadcastReceiver headsetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // 1. Handle Unplugging/Disconnecting (Noisy)
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)) {
                // Pause immediately when earphones are removed
                if (ttsPlayer != null && ttsPlayer.isPlaying()) {
                    pauseBook();
                }
            }
            // 2. Handle Reconnecting (Wired or Bluetooth)
            else if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
                int state = intent.getIntExtra("state", -1);
                if (state == 1) { // Plugged in
                    // Logic: Resume only if it was playing before or as per user preference
                    if (ttsPlayer != null && !ttsPlayer.isPlaying()) {
                        playBook();
                    }
                }
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

        // Register serviceUpdateReceiver with all relevant actions
        IntentFilter filter = new IntentFilter();
        filter.addAction(ReadingService.ACTION_PLAY);
        filter.addAction(ReadingService.ACTION_PAUSE);
        filter.addAction(ReadingService.ACTION_REWIND);
        filter.addAction(ReadingService.ACTION_FORWARD);
        filter.addAction(ReadingService.ACTION_CLOSE);
        registerReceiver(serviceUpdateReceiver, filter);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            String packageName = getPackageName();
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 102);
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_PHONE_STATE}, 103);
        }

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
                ttsPlayer.setCurrentIndex(position);
                playBook();

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

        playPauseFab.setOnClickListener(v -> sendServiceCommand(
                ttsPlayer.isPlaying() ? ReadingService.ACTION_PAUSE : ReadingService.ACTION_PLAY
        ));
        rewindFab.setOnClickListener(v -> sendServiceCommand(ReadingService.ACTION_REWIND));
        forwardFab.setOnClickListener(v -> sendServiceCommand(ReadingService.ACTION_FORWARD));
        closeFab.setOnClickListener(v -> sendServiceCommand(ReadingService.ACTION_CLOSE));

        MaterialButton tocBtn = findViewById(R.id.openTocBtn);
        tocBtn.setOnClickListener(v -> {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.rootLayout, new TableOfContentsFragment(currentToc, sentences, index -> {
                        // Jump playback
                        ttsPlayer.setCurrentIndex(index);
                        playBook();

                        // Highlight in SentenceAdapter
                        if (adapter != null) {
                            adapter.setHighlighted(index);
                            recyclerView.scrollToPosition(index);
                        }

                        // Close TOC and return to reader
                        getSupportFragmentManager().popBackStack();
                    }))
                    .addToBackStack(null)
                    .commit();
        });

        // 2. Filter for Hardware/System events (Headsets)
        IntentFilter hardwareFilter = new IntentFilter();
        hardwareFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        hardwareFilter.addAction(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(headsetReceiver, hardwareFilter);

        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            tm.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }

        String uriFromShelf = getIntent().getStringExtra("BOOK_URI");
        String bookUriToLoad = (uriFromShelf != null) ? uriFromShelf : settings.getLastOpenedBookUri();

        if (bookUriToLoad != null) {
            Uri bookUri = Uri.parse(bookUriToLoad);
            loadBookFromUri(bookUri);

            int lastIndex = settings.getLastReadSentenceIndex(bookUriToLoad);
            if (lastIndex > 0) {
                recyclerView.scrollToPosition(lastIndex);
                ttsPlayer.setCurrentIndex(lastIndex);
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

                    // Load sentences into adapter and TTS
                    sentences.clear();
                    sentences.addAll(meta.sentences);
                    currentToc.clear();
                    currentToc.addAll(meta.toc);

                    adapter.notifyDataSetChanged();
                    ttsPlayer.loadSentences(sentences);

                    // Update or insert book record in repo
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

                    // 🔑 Restore last read position
                    int startIndex = settings.getLastReadSentenceIndex(currentBookUri);
                    if (startIndex == 0 && existing != null) {
                        startIndex = existing.lastSentenceIndex;
                    }

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
        if (ttsPlayer == null || ttsPlayer.isPlaying()) return;

        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = am.requestAudioFocus(focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            ttsPlayer.play();
            startTimerWithCurrentSettings();
            updateService(true);
            if (playPauseFab != null) {
                playPauseFab.setImageResource(android.R.drawable.ic_media_pause);
            }
        } else {
            Toast.makeText(this, "Cannot play: another app is using audio", Toast.LENGTH_SHORT).show();
        }
    }

    private void pauseBook() {
        if (ttsPlayer == null || !ttsPlayer.isPlaying()) return;

        ttsPlayer.pause();

        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.abandonAudioFocus(focusChangeListener);
        }

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
        if (ttsPlayer == null) return;
        int target = Math.max(0, ttsPlayer.getCurrentIndex() - 1);
        ttsPlayer.setCurrentIndex(target);
        if (ttsPlayer.isPlaying()) {
            ttsPlayer.play(); // Restart current sentence
        } else {
            adapter.setHighlighted(target);
            recyclerView.scrollToPosition(target);
        }
    }

    private void forwardSentence() {
        if (ttsPlayer == null) return;
        int target = Math.min(sentences.size() - 1, ttsPlayer.getCurrentIndex() + 1);
        ttsPlayer.setCurrentIndex(target);
        if (ttsPlayer.isPlaying()) {
            ttsPlayer.play(); // Restart current sentence
        } else {
            adapter.setHighlighted(target);
            recyclerView.scrollToPosition(target);
        }
    }

    private void stopTtsOnly() {
        if (currentBookUri != null && ttsPlayer != null) {
            settings.setLastReadSentenceIndex(currentBookUri, ttsPlayer.getCurrentIndex());
        }
        if (ttsPlayer != null) ttsPlayer.stop();
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
        if (currentBookUri != null && ttsPlayer != null) {
            settings.setLastReadSentenceIndex(currentBookUri, ttsPlayer.getCurrentIndex());
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
            settings.setLastReadSentenceIndex(currentBookUri, ttsPlayer.getCurrentIndex());
        }
        try {
            unregisterReceiver(headsetReceiver);
            unregisterReceiver(serviceUpdateReceiver);
        } catch (Exception e) {
            Log.e("MainActivity", "Receiver error", e);
        }
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            tm.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
        if (ttsPlayer != null) ttsPlayer.release();
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
