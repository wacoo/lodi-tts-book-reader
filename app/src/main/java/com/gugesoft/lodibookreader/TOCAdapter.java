package com.gugesoft.lodibookreader;

import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TOCAdapter extends RecyclerView.Adapter<TOCAdapter.ViewHolder> {
    private List<Sentence> sentences;
    private TableOfContentsFragment.OnTOCClickListener listener;

    public TOCAdapter(List<Sentence> sentences, TableOfContentsFragment.OnTOCClickListener listener) {
        this.sentences = sentences;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView tv = new TextView(parent.getContext());
        tv.setPadding(32, 24, 32, 24);
        return new ViewHolder(tv);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.textView.setText(sentences.get(position).getText());
        holder.textView.setOnClickListener(v -> listener.onSentenceSelected(position));
    }

    @Override
    public int getItemCount() {
        return sentences.size();
    }

    // 👇 Inner ViewHolder class
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        ViewHolder(@NonNull TextView itemView) {
            super(itemView);
            textView = itemView;
        }
    }
}