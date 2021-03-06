package com.salejung_android;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import android.os.Environment;
import android.support.v4.content.FileProvider;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;



/**
 * Created by tae seok ki on 2017-11-04.
 */


public class UploadActivity extends AppCompatActivity {

    final String TAG = "UploadActivity";

    final String IMAGE_INFO_UPLOAD_SUCCESS = "1";
    static final int REQUEST_TAKE_PHOTO = 1;
    String mCurrentPhotoPath = null;
    ImageView imgView = null;
    Uri photoURI = null;
    String userId = null;
    String storageFileField = "images";
    Locale systemLocale = null;
    String timeStamp = null;
    FirebaseUser user = null;
    Geocoder geocoder = null;
    String lat = null;
    String lng = null;
    List<Address> addresses = null;
    ArrayList<String> addressFragments = null;
    String fileName = null;
    String BASE_URL = "https://salejung-dev.herokuapp.com/photo/post/";
    RequestQueue queue = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        user = FirebaseAuth.getInstance().getCurrentUser();
        systemLocale = getApplicationContext().getResources().getConfiguration().locale;
        timeStamp = new SimpleDateFormat("yyyyMMddHHmmss", systemLocale).format(new Date());
        userId = user.getUid();
        geocoder= new Geocoder(this, Locale.getDefault());
        SharedPreferences sharedPreferences = getSharedPreferences("setting", 0);
        lat = sharedPreferences.getString("lat", "no lng");
        lng = sharedPreferences.getString("lng", "no lng");

        queue = Volley.newRequestQueue(UploadActivity.this);

        try {
            addresses = geocoder.getFromLocation(
                    Double.parseDouble(lat),
                    Double.parseDouble(lng),
                    // In this sample, get just a single address.
                    1);
        } catch (IOException ioException) {
            // Catch network or other I/O problems.
            ioException.printStackTrace();

        } catch (IllegalArgumentException illegalArgumentException) {
            // Catch invalid latitude or longitude values.
            illegalArgumentException.printStackTrace();
        }

        // Handle case where no address was found.
        if (addresses == null || addresses.size()  == 0) {
            Log.e(TAG,"addresses is null");
        } else {
            Address address = addresses.get(0);
            addressFragments = new ArrayList<>();

            // Fetch the address lines using getAddressLine,
            // join them, and send them to the thread.
            for(int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                addressFragments.add(address.getAddressLine(i));
            }

            if(addressFragments == null) {
                Log.e(TAG,"addressFragments is null");
            }
        }


        // if image clicked, take picture, get photo image and fill view with photo image.
        imgView = findViewById(R.id.imgView);
        imgView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Todo : add function that when upload photo, user can select photo in inner storage.
                // Now, User can upload image only by taking picture

                dispatchTakePictureIntent();
            }
        });

        // if button clicked, upload image and related information.
        final Button btn = findViewById(R.id.btn_do_upload);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Todo : not only imagePath, need also detail(comment), lat and lng's null check.
                if (photoURI != null) {
                    try {
                        imageUpload();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    Toast.makeText(getApplicationContext(), "Image not uploaded!", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    // get photo image and fill the view with photo image.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_TAKE_PHOTO) {
                if (photoURI != null) {
                    imgView.setImageURI(photoURI);
                } else {
                    Toast.makeText(getApplicationContext(), "imageCaptureUri not saved!", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private File createImageFile() throws IOException {
        if (user != null) {
            // Create an image file name
            String imageFileName = timeStamp + '_' + userId;
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File image = File.createTempFile(imageFileName, ".jpg", storageDir);
            mCurrentPhotoPath = image.getAbsolutePath();

            if(storageDir == null) {
                Log.e(TAG,"storageDir is null");
            }

            if(mCurrentPhotoPath == null) {
                Log.e(TAG,"mCurrentPhotoPath is null");
            }

            return image;

        } else {
            Log.e(TAG,"user is null");
            return null;
        }
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                ex.printStackTrace();
                // Error occurred while creating the File
                Toast.makeText(getApplicationContext(), "User not signed in", Toast.LENGTH_LONG).show();
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                photoURI = FileProvider.getUriForFile(this,
                        "com.salejung_android.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
            else {
                Log.e(TAG,"photoFile is null");
            }
        }
    }

    private void imageUpload() throws IOException {

        FirebaseStorage storage = FirebaseStorage.getInstance();

        // Create a storage reference from our app
        StorageReference storageRef = storage.getReference();

        String[] splitedPath = mCurrentPhotoPath.split("/");
        fileName = splitedPath[splitedPath.length - 1];

        final String filePath = storageFileField + "/"
                + systemLocale.getCountry() + "/"
                + new SimpleDateFormat("yyyy/MM/dd/", systemLocale).format(new Date())
                + fileName;

        // Create a child reference
        // imagesRef now points to "images"
        StorageReference imagesRef = storageRef.child(filePath);

        // Get the image
        Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), photoURI);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] img = baos.toByteArray();

        UploadTask uploadTask = imagesRef.putBytes(img);
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                // Handle unsuccessful upload
                Log.d("IMAGE_UPLOAD_FAILURE", "image upload to firebase storage failure");
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                // Handle successful upload
                Log.d("IMAGE_UPLOAD_SUCCESS", "image upload to firebase storage success");

                // Upload image info data using volley POST.
                StringRequest postRequest = new StringRequest(Request.Method.POST, BASE_URL,
                        new Response.Listener<String>()
                        {
                            @Override
                            public void onResponse(String response) {
                                // response
                                if(response.equals(IMAGE_INFO_UPLOAD_SUCCESS)){
                                    Toast.makeText(getApplicationContext(), "Upload success", Toast.LENGTH_LONG).show();
                                }
                                else {
                                    Toast.makeText(getApplicationContext(), "Upload fail", Toast.LENGTH_LONG).show();
                                    // TODO : delete uploded photo.
                                }
                            }
                        },
                        new Response.ErrorListener()
                        {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                // error
                                Log.d("IMAGE_INFO_UPLOAD", "image info upload failure");
                            }
                        }
                ) {
                    @Override
                    protected Map<String, String> getParams()
                    {
                        Map<String, String>  params = new HashMap<>();
                        EditText detailText = findViewById(R.id.detail);

                        String detail = detailText.getText().toString();
                        String address = addressFragments.get(0);
                        String country = systemLocale.getCountry();

                        if(userId == null) {
                            Log.e(TAG, "userId is null");
                        }

                        if(detail.equals("")) {
                            Log.e(TAG, "detail is null");
                        }

                        if(lat == null) {
                            Log.e(TAG, "lat is null");
                        }

                        if(lng == null) {
                            Log.e(TAG, "lng is null");
                        }

                        if(fileName == null) {
                            Log.e(TAG, "fileName is null");
                        }

                        if(timeStamp == null) {
                            Log.e(TAG, "timeStamp is null");
                        }

                        if(address == null) {
                            Log.e(TAG, "address is null");
                        }

                        if(country == null) {
                            Log.e(TAG, "country is null");
                        }

                        params.put("userId", userId);
                        params.put("detail", detail);
                        params.put("lat", lat);
                        params.put("lng", lng);
                        params.put("photoFilePath", filePath);
                        params.put("date", timeStamp);
                        params.put("address", address);
                        params.put("country", country);

                        FCMTopicMaker topicMaker = new FCMTopicMaker(
                                Double.parseDouble(lng),
                                Double.parseDouble(lat));

                        String topic = topicMaker.makeTopic();

                        if (topic == null) {
                            Log.e(TAG, "topic is null");
                        }

                        params.put("topic", topic);


                        Task<GetTokenResult> getToken = null;
                        if (user != null) {
                            getToken = user.getIdToken(false);
                        } else {
                            Log.e(TAG, "user is null");
                        }

                        String userIdToken = null;
                        if (getToken != null) {
                            userIdToken = getToken.getResult().getToken();
                        } else {
                            Log.e(TAG, "getToken is null");
                        }

                        Log.d(TAG, "userIdToken : " + userIdToken);

                        params.put("userIdToken", userIdToken);

                        return params;
                    }
                };
                queue.add(postRequest);
            }
        });
    }
}


