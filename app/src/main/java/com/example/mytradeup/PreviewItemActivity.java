package com.example.mytradeup;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PreviewItemActivity extends AppCompatActivity {

    private static final String TAG = "PreviewItemActivity";

    private TextView tvTitle, tvDescription, tvPrice, tvCategory, tvCondition, tvLocation, tvTags;
    private ViewPager2 viewPagerImages;
    private TabLayout tabLayoutImages;
    private Button btnEdit, btnPublish;

    private HashMap<String, Object> itemData;
    private ArrayList<Uri> imageUris;
    private UriImageSliderAdapter imageAdapter;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private FirebaseAuth auth;
    private StorageReference storageRef;

    private boolean isPublishing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview_item);

        initializeFirebase();
        initializeViews();
        getDataFromIntent();
        setupImageSlider();
        displayItemData();
        setupClickListeners();
    }

    private void initializeFirebase() {
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        auth = FirebaseAuth.getInstance();
        storageRef = storage.getReference();
    }

    private void initializeViews() {
        tvTitle = findViewById(R.id.tv_title);
        tvDescription = findViewById(R.id.tv_description);
        tvPrice = findViewById(R.id.tv_price);
        tvCategory = findViewById(R.id.tv_category);
        tvCondition = findViewById(R.id.tv_condition);
        tvLocation = findViewById(R.id.tv_location);
        tvTags = findViewById(R.id.tv_tags);

        viewPagerImages = findViewById(R.id.viewpager_images);
        tabLayoutImages = findViewById(R.id.tablayout_images);

        btnEdit = findViewById(R.id.btn_edit);
        btnPublish = findViewById(R.id.btn_publish);
    }

    @SuppressWarnings("unchecked")
    private void getDataFromIntent() {
        Intent intent = getIntent();
        if (intent != null) {
            itemData = (HashMap<String, Object>) intent.getSerializableExtra("item_data");
            imageUris = intent.getParcelableArrayListExtra("image_uris");

            if (itemData == null) itemData = new HashMap<>();
            if (imageUris == null) imageUris = new ArrayList<>();
        }
    }

    private void setupImageSlider() {
        if (imageUris != null && !imageUris.isEmpty()) {
            imageAdapter = new UriImageSliderAdapter(this, imageUris);
            viewPagerImages.setAdapter(imageAdapter);

            new TabLayoutMediator(tabLayoutImages, viewPagerImages,
                    (tab, position) -> tab.setText(String.valueOf(position + 1))
            ).attach();

            tabLayoutImages.setVisibility(imageUris.size() > 1 ? View.VISIBLE : View.GONE);
        } else {
            viewPagerImages.setVisibility(View.GONE);
            tabLayoutImages.setVisibility(View.GONE);
        }
    }

    private void displayItemData() {
        if (itemData == null) return;

        tvTitle.setText(getSafeString(itemData.get("title"), "No title"));
        tvDescription.setText(getSafeString(itemData.get("description"), "No description"));

        Object priceObj = itemData.get("price");
        if (priceObj != null) {
            try {
                double price = Double.parseDouble(priceObj.toString());
                NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
                tvPrice.setText(formatter.format(price));
            } catch (NumberFormatException e) {
                tvPrice.setText(priceObj.toString());
            }
        } else {
            tvPrice.setText("Giá liên hệ");
        }

        tvCategory.setText(getSafeString(itemData.get("category"), "Chưa phân loại"));
        tvCondition.setText(getSafeString(itemData.get("condition"), "Không xác định"));
        tvLocation.setText(getSafeString(itemData.get("location"), "Không xác định"));

        Object tagsObj = itemData.get("tags");
        if (tagsObj instanceof ArrayList) {
            ArrayList<String> tags = (ArrayList<String>) tagsObj;
            if (!tags.isEmpty()) {
                StringBuilder tagsBuilder = new StringBuilder();
                for (int i = 0; i < tags.size(); i++) {
                    tagsBuilder.append("#").append(tags.get(i));
                    if (i < tags.size() - 1) tagsBuilder.append(" ");
                }
                tvTags.setText(tagsBuilder.toString());
            } else {
                tvTags.setText("Không có tag");
            }
        } else {
            tvTags.setText("Không có tag");
        }
    }

    private String getSafeString(Object value, String defaultValue) {
        return value != null ? value.toString() : defaultValue;
    }

    private void setupClickListeners() {
        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.putExtra("item_data", itemData);
            intent.putParcelableArrayListExtra("image_uris", imageUris);
            setResult(RESULT_OK, intent);
            finish();
        });

        btnPublish.setOnClickListener(v -> {
            if (!isPublishing) {
                publishItem();
            }
        });
    }

    private void publishItem() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Bạn cần đăng nhập để đăng bài", Toast.LENGTH_SHORT).show();
            return;
        }

        if (itemData == null || itemData.get("title") == null ||
                ((String) itemData.get("title")).trim().isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tiêu đề sản phẩm", Toast.LENGTH_SHORT).show();
            return;
        }

        isPublishing = true;
        btnPublish.setText("Đang đăng...");
        btnPublish.setEnabled(false);

        if (imageUris != null && !imageUris.isEmpty()) {
            uploadImagesAndPublish();
        } else {
            publishItemToFirestore(new ArrayList<>());
        }
    }

    private void uploadImagesAndPublish() {
        ArrayList<String> imageUrls = new ArrayList<>();
        final int totalImages = imageUris.size();
        final int[] uploadedCount = {0};

        for (Uri imageUri : imageUris) {
            String imageId = UUID.randomUUID().toString();
            StorageReference imageRef = storageRef.child("items/" + imageId + ".jpg");

            imageRef.putFile(imageUri)
                    .addOnSuccessListener(taskSnapshot -> {
                        imageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                            imageUrls.add(downloadUri.toString());
                            uploadedCount[0]++;

                            if (uploadedCount[0] == totalImages) {
                                publishItemToFirestore(imageUrls);
                            }
                        }).addOnFailureListener(e -> {
                            Log.e(TAG, "Lỗi khi lấy URL tải về", e);
                            handlePublishError("Không thể lấy liên kết ảnh: " + e.getMessage());
                        });
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Lỗi khi tải ảnh lên", e);
                        handlePublishError("Tải ảnh thất bại: " + e.getMessage());
                    });
        }
    }

    private void publishItemToFirestore(ArrayList<String> imageUrls) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            handlePublishError("Người dùng không hợp lệ");
            return;
        }

        Map<String, Object> item = new HashMap<>(itemData);
        item.put("userId", currentUser.getUid());
        item.put("userEmail", currentUser.getEmail());
        item.put("imageUrls", imageUrls);
        item.put("createdAt", System.currentTimeMillis());
        item.put("updatedAt", System.currentTimeMillis());
        item.put("status", "active");
        item.put("views", 0);
        item.put("favorites", 0);

        String itemId = db.collection("items").document().getId();
        item.put("itemId", itemId);

        db.collection("items")
                .document(itemId)
                .set(item)
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Đăng sản phẩm thành công");
                    Toast.makeText(this, "Đăng sản phẩm thành công!", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent();
                    intent.putExtra("published", true);
                    intent.putExtra("item_id", itemId);
                    setResult(RESULT_OK, intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Lỗi khi đăng lên Firestore", e);
                    handlePublishError("Không thể đăng sản phẩm: " + e.getMessage());
                });
    }

    private void handlePublishError(String errorMessage) {
        isPublishing = false;
        btnPublish.setText("Đăng bài");
        btnPublish.setEnabled(true);
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onBackPressed() {
        if (isPublishing) {
            Toast.makeText(this, "Vui lòng đợi quá trình đăng bài hoàn tất", Toast.LENGTH_SHORT).show();
        } else {
            super.onBackPressed();
        }
    }
}