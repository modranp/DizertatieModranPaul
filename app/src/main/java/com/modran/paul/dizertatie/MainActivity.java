
package com.modran.paul.dizertatie;

import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.content.Intent;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {






    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int MSG_LENGTH_LIMIT = 1000;
    public static final String MSG_LENGTH_KEY = "msg_length";
    //flag for returning activity (sign in)
    public static final int RC_SIGN_IN = 1;

    //flag for photo picker
    private static final int RC_PHOTO_PICKER =  2;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private String mUsername;

    //Firebase Storage
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mPhotoStorageReference;

    //Firebase Database
    private FirebaseDatabase mFDatabase;
    private DatabaseReference mMessagesDatabaseReference;
    private ChildEventListener mChildEventListener;
    //auth
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;

    //remote config
    private FirebaseRemoteConfig mfirebaseRemoteConfig;

    @Override
     protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.modran.paul.dizertatie.R.layout.activity_main);

        mUsername = ANONYMOUS;
        //Initialize Firebase storage for photos
        mFirebaseStorage = FirebaseStorage.getInstance();

        //Initialize Firebase Database
        mFDatabase = FirebaseDatabase.getInstance();
        //intitialize auth and listener
        mFirebaseAuth = FirebaseAuth.getInstance();
        //initialize the remote config
        mfirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        //Reffrences for the firebase db/Storage
        mMessagesDatabaseReference = mFDatabase.getReference().child("messages");
        mPhotoStorageReference = mFirebaseStorage.getReference().child("chat_photos");


        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(com.modran.paul.dizertatie.R.id.progressBar);
        mMessageListView = (ListView) findViewById(com.modran.paul.dizertatie.R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(com.modran.paul.dizertatie.R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(com.modran.paul.dizertatie.R.id.messageEditText);
        mSendButton = (Button) findViewById(com.modran.paul.dizertatie.R.id.sendButton);

        // Initialize message ListView and its adapter
        List<Message> messages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, com.modran.paul.dizertatie.R.layout.item_message, messages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Fire an intent to show an image picker
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Message message = new Message(mMessageEditText.getText().toString(), mUsername, null);
                mMessagesDatabaseReference.push().setValue(message);
                // Clear input box
                mMessageEditText.setText("");
            }
        });




        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null ){
                    //signed in
                  //  Toast.makeText(MainActivity.this, "Bine ai revenit!!! Esti logat", Toast.LENGTH_SHORT).show();
                    onSignedInInit(user.getDisplayName());
                }
                else {
                    
                    onSignedOutClean();
                    //not signed in
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setProviders(
                                            AuthUI.EMAIL_PROVIDER,
                                            AuthUI.GOOGLE_PROVIDER)
                                    .build(),
                            RC_SIGN_IN);



                }

            }
        };

        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
            }
        });

        //setting up remote config
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build();
        mfirebaseRemoteConfig.setConfigSettings(configSettings);

        Map<String, Object> defaultConfigMap = new HashMap<>();
        defaultConfigMap.put(MSG_LENGTH_KEY, MSG_LENGTH_LIMIT);
        mfirebaseRemoteConfig.setDefaults(defaultConfigMap);
        fetchConfig();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(MainActivity.this,"SIGNED IN", Toast.LENGTH_SHORT).show();


            }
            else if (resultCode == RESULT_CANCELED){
                Toast.makeText(MainActivity.this,"CANCELED", Toast.LENGTH_SHORT).show();
                finish();
            }

        }
        else if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK){

            //selecting locally
            Uri selectedImageUri = data.getData();

            //getting the path of the last photo selected
            StorageReference photoRef = mPhotoStorageReference.child(selectedImageUri.getLastPathSegment());
            //uploading to firebase storage
            photoRef.putFile(selectedImageUri)
                    .addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            Uri downloadUrl = taskSnapshot.getDownloadUrl();
                            Message message =
                                    new Message(null, mUsername, downloadUrl.toString());
                            mMessagesDatabaseReference.push().setValue(message);
                        }
                    });
        }

    }

    private void onSignedInInit(String displayName) {
        mUsername = displayName;
        attachDatabaseReadListnere();

    }

    private void onSignedOutClean() {
        mUsername = ANONYMOUS;
        mMessageAdapter.clear();
        detachDatabaseReadListnere();

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(com.modran.paul.dizertatie.R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()){
            case R.id.sign_out_menu:
                //sign out
                AuthUI.getInstance().signOut(this);
                return true;

            default:
        return super.onOptionsItemSelected(item);
    }
    }



    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAuthStateListener !=null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
        detachDatabaseReadListnere();
        mMessageAdapter.clear();
    }

    private void attachDatabaseReadListnere() {
        if (mChildEventListener == null){
        mChildEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                Message message = dataSnapshot.getValue(Message.class);
                mMessageAdapter.add(message);
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        };
        mMessagesDatabaseReference.addChildEventListener(mChildEventListener);

    }
    }

    private void detachDatabaseReadListnere(){
            if (mChildEventListener != null){
                mMessagesDatabaseReference.removeEventListener(mChildEventListener);
                mChildEventListener = null;
            }
    }
//for getting the config
    public void fetchConfig(){
        long cacheExpiration = 3600;

        if(mfirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()){
            cacheExpiration = 0;
        }
        mfirebaseRemoteConfig.fetch(cacheExpiration)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        mfirebaseRemoteConfig.activateFetched();

                        applyRetrievedLengthLimit();

                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error fetching the config :(", e);
                        applyRetrievedLengthLimit();
                    }
                });
    }
//for returning the value of the length
    private void applyRetrievedLengthLimit() {
                Long msg_length = mfirebaseRemoteConfig.getLong(MSG_LENGTH_KEY);
                mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(msg_length.intValue())});
                Log.d(TAG, MSG_LENGTH_KEY + " = " + msg_length);
            }
}

