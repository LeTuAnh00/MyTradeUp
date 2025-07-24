package com.example.mytradeup;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {

    private ArrayList<Uri> imageList;
    private Context context;
    private OnImageRemovedListener listener;

    // Interface để thông báo khi xóa ảnh
    public interface OnImageRemovedListener {
        void onImageRemoved(int position);
    }

    public ImageAdapter(ArrayList<Uri> imageList, Context context) {
        this.imageList = imageList;
        this.context = context;
    }

    public void setOnImageRemovedListener(OnImageRemovedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_image_preview, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        Uri imageUri = imageList.get(position);

        // Load image using Glide
        Glide.with(context)
                .load(imageUri)
                .centerCrop()
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.placeholder_image)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(holder.imageView);

        // Set remove button click listener
        holder.btnRemove.setOnClickListener(v -> {
            int adapterPosition = holder.getAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                // Remove item from list
                imageList.remove(adapterPosition);
                notifyItemRemoved(adapterPosition);
                notifyItemRangeChanged(adapterPosition, imageList.size());

                // Notify listener
                if (listener != null) {
                    listener.onImageRemoved(adapterPosition);
                }

                // Update parent activity if needed (fallback method)
                if (context instanceof AddItemActivity) {
                    ((AddItemActivity) context).updateImageCountDisplay();
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return imageList != null ? imageList.size() : 0;
    }

    // Method to add new image
    public void addImage(Uri imageUri) {
        if (imageList != null && imageUri != null) {
            imageList.add(imageUri);
            notifyItemInserted(imageList.size() - 1);
        }
    }

    // Method to clear all images
    public void clearImages() {
        if (imageList != null) {
            int size = imageList.size();
            imageList.clear();
            notifyItemRangeRemoved(0, size);
        }
    }

    // Get current image list
    public ArrayList<Uri> getImageList() {
        return imageList;
    }

    public static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ImageButton btnRemove;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.iv_image);
            btnRemove = itemView.findViewById(R.id.btn_remove);
        }
    }
}