package com.example.mytradeup;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;


public class AddItemActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 100;
    private static final int PICK_IMAGE_REQUEST = 101;
    private static final int MAX_IMAGES = 10;

    // UI Components
    private EditText etTitle, etDescription, etPrice, etLocation, etTags;
    private Spinner spinnerCategory, spinnerCondition;
    private Button btnAddImages, btnGetLocation, btnPreview, btnSubmit;
    private RecyclerView recyclerImages;
    private LinearLayout layoutImagePreview;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private StorageReference storageRef;

    // Location
    private FusedLocationProviderClient fusedLocationClient;

    // Data
    private ArrayList<Uri> selectedImages;
    private ImageAdapter imageAdapter;
    private String currentLocation = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_item);

        initializeFirebase();
        initializeViews();
        setupSpinners();
        setupImageRecyclerView();
        setupClickListeners();
        initializeLocation();
    }

    private void initializeFirebase() {
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();
    }

    private void initializeViews() {
        etTitle = findViewById(R.id.et_title);
        etDescription = findViewById(R.id.et_description);
        etPrice = findViewById(R.id.et_price);
        etLocation = findViewById(R.id.et_location);
        etTags = findViewById(R.id.et_tags);

        spinnerCategory = findViewById(R.id.spinner_category);
        spinnerCondition = findViewById(R.id.spinner_condition);

        btnAddImages = findViewById(R.id.btn_add_images);
        btnGetLocation = findViewById(R.id.btn_get_location);
        btnPreview = findViewById(R.id.btn_preview);
        btnSubmit = findViewById(R.id.btn_submit);

        recyclerImages = findViewById(R.id.recycler_images);
        layoutImagePreview = findViewById(R.id.layout_image_preview);

        selectedImages = new ArrayList<>();
    }

    private void setupSpinners() {
        // Category spinner
        String[] categories = {"Electronics", "Clothing", "Home & Garden", "Sports",
                "Books", "Automotive", "Toys", "Others"};
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categories);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);

        // Condition spinner
        String[] conditions = {"New", "Like New", "Good", "Fair", "Poor"};
        ArrayAdapter<String> conditionAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, conditions);
        conditionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCondition.setAdapter(conditionAdapter);
    }

    private void setupImageRecyclerView() {
        imageAdapter = new ImageAdapter(selectedImages, this);
        recyclerImages.setLayoutManager(new LinearLayoutManager(this,
                LinearLayoutManager.HORIZONTAL, false));
        recyclerImages.setAdapter(imageAdapter);
    }

    private void setupClickListeners() {
        btnAddImages.setOnClickListener(v -> openImagePicker());
        btnGetLocation.setOnClickListener(v -> getCurrentLocation());
        btnPreview.setOnClickListener(v -> previewListing());
        btnSubmit.setOnClickListener(v -> submitListing());
    }

    private void initializeLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    private void openImagePicker() {
        if (selectedImages.size() >= MAX_IMAGES) {
            Toast.makeText(this, "Maximum " + MAX_IMAGES + " images allowed",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    private void getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            getAddressFromLocation(location.getLatitude(), location.getLongitude());
                        } else {
                            Toast.makeText(AddItemActivity.this,
                                    "Unable to get current location", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void getAddressFromLocation(double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder addressText = new StringBuilder();

                if (address.getSubLocality() != null) {
                    addressText.append(address.getSubLocality()).append(", ");
                }
                if (address.getLocality() != null) {
                    addressText.append(address.getLocality()).append(", ");
                }
                if (address.getAdminArea() != null) {
                    addressText.append(address.getAdminArea());
                }

                currentLocation = addressText.toString();
                etLocation.setText(currentLocation);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error getting address", Toast.LENGTH_SHORT).show();
        }
    }

    private void previewListing() {
        if (!validateRequiredFields()) {
            return;
        }

        // Create item data for preview
        Map<String, Object> itemData = createItemData();

        // Start preview activity
        Intent intent = new Intent(this, PreviewItemActivity.class);
        intent.putExtra("item_data", (HashMap<String, Object>) itemData);
        intent.putParcelableArrayListExtra("selected_images", selectedImages);
        startActivity(intent);
    }

    private void submitListing() {
        if (!validateRequiredFields()) {
            return;
        }

        if (selectedImages.isEmpty()) {
            Toast.makeText(this, "Please add at least one image", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmit.setEnabled(false);
        btnSubmit.setText("Uploading...");

        uploadImagesAndSaveListing();
    }

    private boolean validateRequiredFields() {
        boolean isValid = true;

        if (TextUtils.isEmpty(etTitle.getText().toString().trim())) {
            etTitle.setError("Title is required");
            isValid = false;
        }

        if (TextUtils.isEmpty(etDescription.getText().toString().trim())) {
            etDescription.setError("Description is required");
            isValid = false;
        }

        if (TextUtils.isEmpty(etPrice.getText().toString().trim())) {
            etPrice.setError("Price is required");
            isValid = false;
        }

        if (TextUtils.isEmpty(etLocation.getText().toString().trim())) {
            etLocation.setError("Location is required");
            isValid = false;
        }

        return isValid;
    }

    private Map<String, Object> createItemData() {
        Map<String, Object> itemData = new HashMap<>();
        itemData.put("title", etTitle.getText().toString().trim());
        itemData.put("description", etDescription.getText().toString().trim());
        itemData.put("price", Double.parseDouble(etPrice.getText().toString().trim()));
        itemData.put("category", spinnerCategory.getSelectedItem().toString());
        itemData.put("condition", spinnerCondition.getSelectedItem().toString());
        itemData.put("location", etLocation.getText().toString().trim());
        itemData.put("tags", etTags.getText().toString().trim());
        itemData.put("timestamp", System.currentTimeMillis());
        itemData.put("userId", getCurrentUserId()); // Implement user authentication

        return itemData;
    }

    private void uploadImagesAndSaveListing() {
        List<String> imageUrls = new ArrayList<>();
        final int totalImages = selectedImages.size();
        final int[] uploadCount = {0};

        for (Uri imageUri : selectedImages) {
            String imageName = "items/" + UUID.randomUUID().toString() + ".jpg";
            StorageReference imageRef = storageRef.child(imageName);

            imageRef.putFile(imageUri)
                    .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            imageRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                @Override
                                public void onSuccess(Uri downloadUri) {
                                    imageUrls.add(downloadUri.toString());
                                    uploadCount[0]++;

                                    if (uploadCount[0] == totalImages) {
                                        // All images uploaded, save listing
                                        saveListingToFirestore(imageUrls);
                                    }
                                }
                            });
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(AddItemActivity.this,
                                "Error uploading image: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("Submit Listing");
                    });
        }
    }

    private void saveListingToFirestore(List<String> imageUrls) {
        Map<String, Object> itemData = createItemData();
        itemData.put("imageUrls", imageUrls);

        db.collection("items")
                .add(itemData)
                .addOnCompleteListener(new OnCompleteListener<DocumentReference>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentReference> task) {
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("Submit Listing");

                        if (task.isSuccessful()) {
                            Toast.makeText(AddItemActivity.this,
                                    "Item listed successfully!", Toast.LENGTH_SHORT).show();
                            finish(); // Close activity and return to previous screen
                        } else {
                            Toast.makeText(AddItemActivity.this,
                                    "Error saving listing: " + task.getException().getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private String getCurrentUserId() {
        // Implement Firebase Authentication to get current user ID
        // For now, return a placeholder
        return "user_123"; // Replace with actual user authentication
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                // Multiple images selected
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count && selectedImages.size() < MAX_IMAGES; i++) {
                    Uri imageUri = data.getClipData().getItemAt(i).getUri();
                    selectedImages.add(imageUri);
                }
            } else if (data.getData() != null) {
                // Single image selected
                if (selectedImages.size() < MAX_IMAGES) {
                    selectedImages.add(data.getData());
                }
            }

            imageAdapter.notifyDataSetChanged();
            updateImageCountDisplay();
        }
    }

    public void updateImageCountDisplay() {
        // Cập nhật số lượng ảnh hiển thị
        if (imageAdapter != null) {
            int imageCount = imageAdapter.getItemCount();

            // Cập nhật text của button với số lượng ảnh hiện tại
            btnAddImages.setText("Add Images (" + imageCount + "/" + MAX_IMAGES + ")");

            // Cập nhật UI - ví dụ:
            // textViewImageCount.setText(imageCount + "/10 ảnh");

            // Hiển thị/ẩn nút thêm ảnh nếu đã đạt giới hạn
            if (imageCount >= MAX_IMAGES) {
                btnAddImages.setVisibility(View.GONE);
                // Hoặc disable button thay vì ẩn
                // btnAddImages.setEnabled(false);
            } else {
                btnAddImages.setVisibility(View.VISIBLE);
                // btnAddImages.setEnabled(true);
            }

            // Log để debug
            android.util.Log.d("ImageCount", "Current images: " + imageCount);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}