package com.gugesoft.lodibookreader;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
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

    private BookRepository bookRepo;
    private String currentBookUri;

    private FloatingActionButton playFab, pauseFab, rewindFab, closeFab;

    private final BroadcastReceiver mediaReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ReadingService.ACTION_PLAY.equals(action)) playBook();
            else if (ReadingService.ACTION_PAUSE.equals(action)) pauseBook();
            else if (ReadingService.ACTION_REWIND.equals(action)) rewindSentence();
            else if (ReadingService.ACTION_CLOSE.equals(action)) {
                stopTtsOnly();
                finish();
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //cleanupPreviousBook();
        setContentView(R.layout.activity_main);

        bookRepo = new BookRepository(this);
        // 🔐 Notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        // 📚 Recycler
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new SentenceAdapter(sentences, new SentenceAdapter.OnSentenceClickListener() {
            @Override
            public void onSentenceClick(int position) {
                ttsPlayer.playFrom(position);
                timerManager.start(AppConfig.TIMER_RESET_MS);
                updateService(true);
            }

            @Override
            public void onNavigateTo(int position) {
                recyclerView.scrollToPosition(position);
            }
        });

        recyclerView.setAdapter(adapter);

        // 🔊 Systems
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

        // 🎮 Buttons
        findViewById(R.id.loadBookBtn).setOnClickListener(v -> pickBook());

        findViewById(R.id.openShelfBtn).setOnClickListener(v ->
                startActivity(new Intent(this, BookshelfActivity.class))
        );

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

        // 🔔 Receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(ReadingService.ACTION_PLAY);
        filter.addAction(ReadingService.ACTION_PAUSE);
        filter.addAction(ReadingService.ACTION_REWIND);
        filter.addAction(ReadingService.ACTION_CLOSE);

        registerReceiver(mediaReceiver, filter);

        // 📖 OPEN FROM SHELF
        String uriFromShelf = getIntent().getStringExtra("BOOK_URI");
        if (uriFromShelf != null) {
            loadBookFromUri(Uri.parse(uriFromShelf));
        }
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

                // ✅ Ensure we still have permission
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                } catch (Exception ignored) {}

                BookLoader.BookMetadata meta = new BookLoader().loadBookWithMetadata(this, uri);

                runOnUiThread(() -> {
                    if (meta.sentences == null || meta.sentences.isEmpty()) {
                        Toast.makeText(this, "Empty or unreadable book", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // ✅ Always reload sentences
                    sentences.clear();
                    sentences.addAll(meta.sentences);
                    adapter.notifyDataSetChanged();
                    ttsPlayer.loadSentences(sentences);

                    // ✅ Save metadata intact
                    BookItem newBook = new BookItem(currentBookUri, meta.title, meta.author, meta.coverUri, 0);
                    bookRepo.saveOrUpdateBook(newBook);

                    // ✅ Restore position only
                    BookItem saved = bookRepo.findBook(currentBookUri);
                    int startIndex = (saved != null) ? saved.lastSentenceIndex : 0;

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
                // 🔐 Take Persistable permissions for both Read and Write
                final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
                loadBookFromUri(uri);
            }
        }
    }

    private void playBook() {
        ttsPlayer.play();
        timerManager.start(AppConfig.TIMER_RESET_MS);
        updateService(true);
    }

    private void pauseBook() {
        ttsPlayer.pause();
        timerManager.stop();
        updateService(false);
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
    }

    private void updateService(boolean isPlaying) {
        Intent intent = new Intent(this, ReadingService.class);
        intent.putExtra("IS_PLAYING", isPlaying);
        startService(intent);
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

        if (currentBookUri != null && ttsPlayer != null) {
            BookItem existing = bookRepo.findBook(currentBookUri);
            if (existing != null) {
                // ✅ Only update progress, don’t overwrite title/cover
                existing.lastSentenceIndex = ttsPlayer.getCurrentIndex();
                bookRepo.saveOrUpdateBook(existing);
            }
        }
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // Important: This updates the Activity's intent
        String uriFromShelf = intent.getStringExtra("BOOK_URI");
        if (uriFromShelf != null) {
            loadBookFromUri(Uri.parse(uriFromShelf));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mediaReceiver);
        ttsPlayer.release();
    }
    // Add this method anywhere in MainActivity class
    private void cleanupPreviousBook() {
        try {
            if (ttsPlayer != null) {
                ttsPlayer.stop();
            }
            if (timerManager != null) {
                timerManager.stop();
            }
            stopService(new Intent(this, ReadingService.class));
        } catch (Exception e) {
            Log.w("MainActivity", "Cleanup warning", e);
        }
    }
}