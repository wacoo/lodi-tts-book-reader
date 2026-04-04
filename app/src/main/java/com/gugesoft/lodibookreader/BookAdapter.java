package com.gugesoft.lodibookreader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class BookAdapter extends RecyclerView.Adapter<BookAdapter.ViewHolder> {

    private List<BookItem> books;
    private OnBookClickListener listener;

    public interface OnBookClickListener {
        void onClick(BookItem book);
    }

    public BookAdapter(List<BookItem> books, OnBookClickListener listener) {
        this.books = books;
        this.listener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // This line uses the new card layout
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.book_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        BookItem book = books.get(position);

        holder.tvTitle.setText(book.title);
        holder.tvAuthor.setText(book.author.isEmpty() ? "Unknown Author" : book.author);
        holder.tvProgress.setText("Stopped at sentence " + book.lastSentenceIndex);

        // Cover image or big initial
        if (book.coverUri != null && !book.coverUri.isEmpty()) {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(Uri.parse(book.coverUri).getPath());
                if (bitmap != null) {
                    holder.ivCover.setImageBitmap(bitmap);
                    holder.tvInitials.setVisibility(View.GONE);
                } else {
                    showInitials(holder, book);
                }
            } catch (Exception e) {
                showInitials(holder, book);
            }
        } else {
            showInitials(holder, book);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(book);
        });
    }

    private void showInitials(ViewHolder holder, BookItem book) {
        holder.ivCover.setImageDrawable(null);
        holder.tvInitials.setVisibility(View.VISIBLE);
        String initial = (book.title != null && book.title.length() > 0)
                ? String.valueOf(book.title.charAt(0)).toUpperCase()
                : "?";
        holder.tvInitials.setText(initial);
    }

    @Override
    public int getItemCount() {
        return books.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCover;
        TextView tvInitials;
        TextView tvTitle;
        TextView tvAuthor;
        TextView tvProgress;

        ViewHolder(View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.ivCover);
            tvInitials = itemView.findViewById(R.id.tvInitials);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvAuthor = itemView.findViewById(R.id.tvAuthor);
            tvProgress = itemView.findViewById(R.id.tvProgress);
        }
    }
}