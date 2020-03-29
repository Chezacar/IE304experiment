package com.ambow.contact;

import com.ambow.contact.R;
import android.Manifest;
import android.annotation.TargetApi;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.file.spi.FileSystemProvider;

public class ContactEditor extends Activity {

    private static final String TAG = "ContactEditor";

    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    private static final int REVERT_ID = Menu.FIRST;
    private static final int DELETE_ID = Menu.FIRST + 1;
    private static final int DISCARD_ID = Menu.FIRST + 2;

    public static final int TAKE_PHOTO = 1;
    public static final int CHOOSE_PHOTO = 2;
    private ImageView picture;
    private Uri imageUri;

    private int mState;
    private Uri mUri;
    private Cursor mCursor;

    private EditText companyText;
    private EditText nameText;
    private EditText phoneText;
    private EditText emailText;
    private Button saveButton;
    private Button cancelButton;
    private Button takephoto;
    private Button chooseFromAlbum;


    private String originalCompanyText = "";
    private String originalNameText = "";
    private String originalPhoneText = "";
    private String originalEmailText = "";

    @SuppressLint("WrongViewCast")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        final String action = intent.getAction();
        Log.d(TAG + ":onCreate", action);

        if (Intent.ACTION_EDIT.equals(action)) {
            mState = STATE_EDIT;
            mUri = intent.getData();
        } else if (Intent.ACTION_INSERT.equals(action)) {
            mState = STATE_INSERT;
            mUri = getContentResolver().insert(intent.getData(), null);

            if (mUri == null) {
                Log.e(TAG + ":onCreate", "Failed to insert new Contact into " + getIntent().getData());
                finish();
                return;
            }
            setResult(RESULT_OK, new Intent().setAction(mUri.toString()));

        } else {
            Log.e(TAG + ":onCreate", " unknown action");
            finish();
            return;
        }

        setContentView(R.layout.contact_editor);

        companyText = (EditText) findViewById(R.id.EditText06);
        nameText = (EditText) findViewById(R.id.EditText01);
        phoneText = (EditText) findViewById(R.id.EditText02);
        emailText = (EditText) findViewById(R.id.EditText03);
        saveButton = (Button) findViewById(R.id.Button01);
        cancelButton = (Button) findViewById(R.id.Button02);
        takephoto = (Button) findViewById(R.id.take_photo);
        picture = (ImageView) findViewById(R.id.picture);
        chooseFromAlbum = (Button) findViewById(R.id.choose_from_album);

        saveButton.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                String text = nameText.getText().toString();
                if (text.length() == 0) {
                    deleteContact();
                    setResult(RESULT_CANCELED);
                    finish();
                } else {
                    updateContact();
                }
            }
        });

        cancelButton.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                if (mState == STATE_INSERT) {
                    deleteContact();
                    setResult(RESULT_CANCELED);
                    finish();
                } else {
                    backupContact();
                }
            }

        });

        takephoto.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                File outputImage = new File(getExternalCacheDir(), "output_image.jpg");
                try {
                    if (outputImage.exists()) {
                        outputImage.delete();
                    }
                    outputImage.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                imageUri = FileProvider.getUriForFile(ContactEditor.this, "com.example.ambow.contact.fileprovider", outputImage);
                Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                startActivityForResult(intent, TAKE_PHOTO);
            }
        });
        chooseFromAlbum.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                openAlbum();
            }
        });

        Log.d(TAG + ":onCreate", mUri.toString());

        mCursor = managedQuery(mUri, ContactColumn.PROJECTION, null, null, null);
        mCursor.moveToFirst();

        originalCompanyText = mCursor.getString(4);
        originalNameText = mCursor.getString(ContactColumn.NAME_COLUMN);
        originalPhoneText = mCursor.getString(ContactColumn.MOBILE_COLUMN);
        originalEmailText = mCursor.getString(ContactColumn.EMAIL_COLUMN);

        companyText.setText(originalCompanyText);
        nameText.setText(originalNameText);
        phoneText.setText(originalPhoneText);
        emailText.setText(originalEmailText);

        if (mState == STATE_EDIT) {
            setTitle("个人通讯录 - " + getText(R.string.contact_edit));
        } else if (mState == STATE_INSERT) {
            setTitle("个人通讯录 - " + getText(R.string.contact_create));
        }
    }

        private void openAlbum() {
            Intent intent = new Intent("android.intent.action.GET_CONTENT");
            intent.setType("image/*");
            startActivityForResult(intent, CHOOSE_PHOTO); // 打开相册
        }

        protected void onActivityResult (int requestCode, int resultCode, Intent data){
            switch (requestCode) {
                case TAKE_PHOTO:
                    if (resultCode == RESULT_OK) {
                        try {
                            // 将拍摄的照片显示出来
                            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
                            picture.setImageBitmap(bitmap);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case CHOOSE_PHOTO:
                    if (resultCode == RESULT_OK) {
                        // 判断手机系统版本号
                        if (Build.VERSION.SDK_INT >= 19) {
                            // 4.4及以上系统使用这个方法处理图片
                            handleImageOnKitKat(data);
                        } else {
                            // 4.4以下系统使用这个方法处理图片
                            handleImageBeforeKitKat(data);
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        private void handleImageOnKitKat(Intent data) {
            String imagePath = null;
            Uri uri = data.getData();
            Log.d("TAG", "handleImageOnKitKat: uri is " + uri);
            if (DocumentsContract.isDocumentUri(this, uri)) {
                // 如果是document类型的Uri，则通过document id处理
                String docId = DocumentsContract.getDocumentId(uri);
                if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                    String id = docId.split(":")[1]; // 解析出数字格式的id
                    String selection = MediaStore.Images.Media._ID + "=" + id;
                    imagePath = getImagePath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection);
                } else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                    Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(docId));
                    imagePath = getImagePath(contentUri, null);
                }
            } else if ("content".equalsIgnoreCase(uri.getScheme())) {
                // 如果是content类型的Uri，则使用普通方式处理
                imagePath = getImagePath(uri, null);
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                // 如果是file类型的Uri，直接获取图片路径即可
                imagePath = uri.getPath();
            }
            displayImage(imagePath); // 根据图片路径显示图片
        }

        private void handleImageBeforeKitKat (Intent data){
            Uri uri = data.getData();
            String imagePath = getImagePath(uri, null);
            displayImage(imagePath);
        }

        private String getImagePath (Uri uri, String selection){
            String path = null;
            // 通过Uri和selection来获取真实的图片路径
            Cursor cursor = getContentResolver().query(uri, null, selection, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                }
                cursor.close();
            }
            return path;
        }

        private void displayImage (String imagePath){
            if (imagePath != null) {
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                picture.setImageBitmap(bitmap);
            } else {
                Toast.makeText(this, "failed to get image", Toast.LENGTH_SHORT).show();
            }
        }




    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        if (mState == STATE_EDIT) {
            menu.add(0, REVERT_ID, 0, R.string.menu_revert)
                    .setIcon(android.R.drawable.ic_menu_revert);
            menu.add(0, DELETE_ID, 0, R.string.menu_delete)
                    .setIcon(android.R.drawable.ic_menu_delete);
        } else {
            menu.add(0, DISCARD_ID, 0, R.string.menu_discard)
                    .setIcon(android.R.drawable.ic_menu_revert);
        }
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case DELETE_ID:
                deleteContact();
                finish();
                break;
            case DISCARD_ID:
                cancelContact();
                break;
            case REVERT_ID:
                backupContact();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void deleteContact() {
        if (mCursor != null) {
            mCursor.close();
            getContentResolver().delete(mUri, null, null);
        }
    }

    private void cancelContact() {
        deleteContact();
        setResult(RESULT_CANCELED);
        finish();
    }

    private void updateContact() {
        if (mCursor != null) {
            mCursor.close();
            ContentValues values = new ContentValues();
            values.put(ContactColumn.COMPANY, companyText.getText().toString());
            values.put(ContactColumn.NAME, nameText.getText().toString());
            values.put(ContactColumn.MOBILE, phoneText.getText().toString());
            values.put(ContactColumn.EMAIL, emailText.getText().toString());
            values.put()
            getContentResolver().update(mUri, values, null, null);
        }
        setResult(RESULT_OK);
        finish();
    }

    private void backupContact() {
        if (mCursor != null) {
            mCursor.close();
            ContentValues values = new ContentValues();
            values.put(ContactColumn.COMPANY, originalCompanyText);
            values.put(ContactColumn.NAME, originalNameText);
            values.put(ContactColumn.MOBILE, originalPhoneText);
            values.put(ContactColumn.EMAIL, originalEmailText);
            getContentResolver().update(mUri, values, null, null);
        }
        setResult(RESULT_CANCELED);
        finish();
    }

}
