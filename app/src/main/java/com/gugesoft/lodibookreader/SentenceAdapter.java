package com.gugesoft.lodibookreader;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.util.Linkify;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SentenceAdapter extends RecyclerView.Adapter<SentenceAdapter.ViewHolder> {

    private List<Sentence> sentences;
    private int highlightedIndex = -1;
    private OnSentenceClickListener listener;

    public interface OnSentenceClickListener {
        void onSentenceClick(int position);
    }

    public SentenceAdapter(List<Sentence> sentences, OnSentenceClickListener listener) {
        this.sentences = sentences;
        this.listener = listener;
    }

    public void setHighlighted(int index) {
        highlightedIndex = index;
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        TextView tv = new TextView(parent.getContext());
        tv.setPadding(20, 20, 20, 20);
        tv.setTextSize(18);
        // Note: AutoLinkMask can sometimes interfere with custom click listeners.
        // If clicks feel "unresponsive," consider moving Linkify to manual handling.
        tv.setAutoLinkMask(Linkify.WEB_URLS);
        tv.setLinksClickable(true);
        return new ViewHolder(tv);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Sentence sentence = sentences.get(position);
        holder.textView.setText(sentence.getText());

        holder.textView.setBackgroundColor(
                position == highlightedIndex ? Color.parseColor("#BBDEFB") : Color.TRANSPARENT
        );

        // 🔹 Consolidated Listener: Handles both Single and Double clicks
        holder.textView.setOnClickListener(new DoubleClickListener() {
            @Override
            public void onClick(View v) {
                super.onClick(v); // Manages the double-click timing logic

                // SINGLE TAP → Open Link
                if (sentence.getLink() != null) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(sentence.getLink()));
                        v.getContext().startActivity(intent);
                    } catch (Exception ignored) {}
                }
            }

            @Override
            public void onDoubleClick(View v) {
                // DOUBLE TAP → Play Sentence
                if (listener != null) listener.onSentenceClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return sentences.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        ViewHolder(View itemView) {
            super(itemView);
            textView = (TextView) itemView;
        }
    }
}