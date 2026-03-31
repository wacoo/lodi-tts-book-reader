package com.gugesoft.lodibookreader;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private VolumeController volumeController;
    private TimerManager timerManager;
    private TTSPlayer ttsPlayer;

    private Button btnPlay, btnPause, btnRewind, btnStartTimer, btnLoadBook;
    private TextView tvBookText;

    private ShakeDetector shakeDetector;

    private static final int PICK_FILE = 2001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ---------- Views ----------
        tvBookText = findViewById(R.id.tvBookText);
        btnPlay = findViewById(R.id.btnPlay);
        btnPause = findViewById(R.id.btnPause);
        btnRewind = findViewById(R.id.btnRewind);
        btnStartTimer = findViewById(R.id.btnStartTimer);
        btnLoadBook = findViewById(R.id.btnLoadBook);

        // ---------- Controllers ----------
        volumeController = new VolumeController(this);
        timerManager = new TimerManager(volumeController, () -> {});

        ttsPlayer = new TTSPlayer(this, new ArrayList<>(), tvBookText);

        // ---------- Button Listeners ----------
        btnPlay.setOnClickListener(v -> ttsPlayer.play());
        btnPause.setOnClickListener(v -> ttsPlayer.pause());
        btnRewind.setOnClickListener(v -> ttsPlayer.rewind());
        btnStartTimer.setOnClickListener(v -> timerManager.start(20_000));

        btnLoadBook.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            String[] mimeTypes = {"application/epub+zip", "application/pdf", "text/plain"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            startActivityForResult(intent, PICK_FILE);
        });

        // ---------- ShakeDetector ----------
        shakeDetector = new ShakeDetector(this, () -> {
            timerManager.reset();
            ttsPlayer.play();

            MediaPlayer bell = MediaPlayer.create(this, R.raw.bell);
            if (bell != null) {
                bell.setOnCompletionListener(mp -> mp.release());
                bell.start();
            }
        });
    }

    // ---------- File Selection ----------
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri fileUri = data.getData();
            try {
                InputStream inputStream = getContentResolver().openInputStream(fileUri);
                String type = getContentResolver().getType(fileUri);
                BookLoader loader = new BookLoader(this);
                List<String> sentences = new ArrayList<>();

                if ("application/epub+zip".equals(type)) {
                    sentences = loader.loadEpub(inputStream);
                } else if ("text/plain".equals(type)) {
                    sentences = loader.loadTxt(inputStream);
                }

                ttsPlayer.loadSentences(sentences);
                ttsPlayer.play();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ---------- Lifecycle ----------
    @Override
    protected void onResume() {
        super.onResume();
        if (shakeDetector != null) shakeDetector.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (shakeDetector != null) shakeDetector.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ttsPlayer.stop();
        timerManager.stop();
        if (shakeDetector != null) shakeDetector.stop();
    }
}