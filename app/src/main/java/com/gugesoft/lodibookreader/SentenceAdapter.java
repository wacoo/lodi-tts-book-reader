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
        void onNavigateTo(int position); // 🔥 for local links
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
        tv.setAutoLinkMask(Linkify.WEB_URLS);
        tv.setLinksClickable(true);
        return new ViewHolder(tv);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Sentence sentence = sentences.get(position);
        holder.textView.setText(sentence.getText());

        // Apply user settings
        SettingsManager settings = new SettingsManager(holder.itemView.getContext());
        holder.textView.setTextColor(settings.getFontColor());
        holder.textView.setTextSize(settings.getFontSize());

        // Highlight logic stays:
        if (position == highlightedIndex) {
            holder.textView.setBackgroundColor(Color.parseColor("#BBDEFB")); // highlight
        } else {
            holder.textView.setBackgroundColor(Color.TRANSPARENT);
        }

        // 🔥 SINGLE + DOUBLE TAP HANDLER
        holder.textView.setOnClickListener(new DoubleClickListener() {
            @Override
            public void onDoubleClick(View v) {
                if (listener != null) listener.onSentenceClick(position);
            }

            @Override
            public void onClick(View v) {
                super.onClick(v);

                if (sentence.getLink() != null) {
                    if (sentence.getLink().startsWith("http")) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(sentence.getLink()));
                            v.getContext().startActivity(intent);
                        } catch (Exception ignored) {}
                        return;
                    }
                    int target = findSentenceIndexByLink(sentence.getLink());
                    if (target != -1 && listener != null) {
                        listener.onNavigateTo(target);
                    }
                }
            }
        });

    }



    private int findSentenceIndexByLink(String link) {
        for (int i = 0; i < sentences.size(); i++) {
            if (link.equals(sentences.get(i).getLink())) {
                return i;
            }
        }
        return -1;
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