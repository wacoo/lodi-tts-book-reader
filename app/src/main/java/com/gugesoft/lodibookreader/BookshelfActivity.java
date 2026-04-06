package com.gugesoft.lodibookreader;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class BookshelfActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private BookAdapter adapter;
    private BookRepository bookRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookshelf);

        recyclerView = findViewById(R.id.recyclerViewBooks);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        bookRepo = new BookRepository(this);
        List<BookItem> books = bookRepo.getBooks();

        adapter = new BookAdapter(books, book -> {
            stopCurrentReadingSession();

            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("BOOK_URI", book.uri);

            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            startActivity(intent);
        });

        recyclerView.setAdapter(adapter);
    }

    private void stopCurrentReadingSession() {
        try {
            // ✅ ONLY stop service — do NOT broadcast CLOSE
            Intent serviceIntent = new Intent(this, ReadingService.class);
            stopService(serviceIntent);

        } catch (Exception e) {
            Log.e("Bookshelf", "Error stopping previous session", e);
        }
    }
}