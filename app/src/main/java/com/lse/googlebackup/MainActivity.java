package com.lse.googlebackup;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private ActivityResultLauncher<Intent> signInResultLauncher;
    private DriveServiceHelper mDriveServiceHelper;
    private TextView userDetails, backupData;
    private LinearLayout accessBlock;
    private Button connect, signOut;
    private String fileId;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressDialog = ProgressDialog.show(this, "Processing", "Please wait...", true, false);
        progressDialog.hide();

        accessBlock = findViewById(R.id.access);
        connect = findViewById(R.id.connect);
        Button backup = findViewById(R.id.backup);
        Button restore = findViewById(R.id.restore);
        signOut = findViewById(R.id.sign_out);
        userDetails = findViewById(R.id.user_details);
        backupData = findViewById(R.id.data);
        userDetails.setMovementMethod(new ScrollingMovementMethod());

        connect.setOnClickListener(v -> requestSignIn());

        signInResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    progressDialog.hide();
                    if (result.getData() != null) {
                        handleSignInResult(result.getData());
                    }
                });

        backup.setOnClickListener(v -> {
            progressDialog.show();
            mDriveServiceHelper.searchFile(result -> {
                if (result != null) {
                    Log.d(TAG, String.valueOf(result.getFiles().size()));
                    for (File file : result.getFiles()) {
                        if (file.getName().contains(Constants.FILE_NAME)) {
                            fileId = file.getId();
                        }
                        Log.d(TAG, file.getName());
                    }
                    StringBuilder contentData = new StringBuilder();
                    for (int i = 0; i < Constants.RANDOM_DATA_SIZE; i++) {
                        contentData.append((int) (Math.random() * 100000));
                    }
                    if (fileId == null) {
                        mDriveServiceHelper.createFile(result1 -> {
                            progressDialog.hide();
                            if (result1 != null) {
                                Toast.makeText(getApplicationContext(), "Backup saved successfully", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getApplicationContext(), "Cannot save backup!", Toast.LENGTH_LONG).show();
                            }
                        }, Constants.FILE_NAME, contentData.toString());
                    } else {
                        mDriveServiceHelper.readFile(result1 -> {
                            String previousContent = "";
                            if (result1 != null) {
                                previousContent = result1;
                            }
                            mDriveServiceHelper.updateFile(result2 -> {
                                progressDialog.hide();
                                if (result2 != null) {
                                    Toast.makeText(getApplicationContext(), "Backup saved successfully", Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(getApplicationContext(), "Cannot save backup!", Toast.LENGTH_LONG).show();
                                }
                            }, fileId, previousContent + "::" + contentData.toString());
                        }, fileId);
                    }
                }
            });
        });

        restore.setOnClickListener(v -> {
            progressDialog.show();
            mDriveServiceHelper.searchFile(result -> {
                if (result != null) {
                    Log.d(TAG, String.valueOf(result.getFiles().size()));
                    for (File file : result.getFiles()) {
                        if (file.getName().contains(Constants.FILE_NAME)) {
                            fileId = file.getId();
                            Log.d(TAG, file.getName());
                        }
                    }
                    if (fileId != null) {
                        mDriveServiceHelper.readFile(result1 -> {
                            progressDialog.hide();
                            if (result1 != null) {
                                backupData.setText(result1);
                            } else {
                                Toast.makeText(getApplicationContext(), "Cannot find backup!", Toast.LENGTH_LONG).show();
                            }
                        }, fileId);
                    } else {
                        progressDialog.hide();
                        Toast.makeText(getApplicationContext(), "Cannot find backup!", Toast.LENGTH_LONG).show();
                    }
                }
            });
        });
    }

    private void requestSignIn() {
        Log.d(TAG, "Requesting sign-in");
        progressDialog.show();

        GoogleSignInOptions signInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestScopes(new Scope(DriveScopes.DRIVE), new Scope(DriveScopes.DRIVE_APPDATA))
                        .build();
        GoogleSignInClient client = GoogleSignIn.getClient(this, signInOptions);

        signInResultLauncher.launch(client.getSignInIntent());

        signOut.setOnClickListener(v -> {
            client.signOut();
            accessBlock.setVisibility(View.GONE);
            connect.setVisibility(View.VISIBLE);
            userDetails.setText("");
        });
    }

    private void handleSignInResult(Intent result) {
        GoogleSignIn.getSignedInAccountFromIntent(result)
                .addOnSuccessListener(googleAccount -> {
                    String temp = "Signed in as " + googleAccount.getEmail();
                    Log.d(TAG, temp);

                    accessBlock.setVisibility(View.VISIBLE);
                    connect.setVisibility(View.GONE);

                    userDetails.setText(temp);

                    ArrayList<String> scopeSet = new ArrayList<>();
                    scopeSet.add(DriveScopes.DRIVE);
                    scopeSet.add(DriveScopes.DRIVE_APPDATA);

                    // Use the authenticated account to sign in to the Drive service.
                    GoogleAccountCredential credential =
                            GoogleAccountCredential.usingOAuth2(
                                    this, scopeSet);
                    credential.setSelectedAccount(googleAccount.getAccount());
                    Drive googleDriveService =
                            new Drive.Builder(
                                    AndroidHttp.newCompatibleTransport(),
                                    new GsonFactory(),
                                    credential)
                                    .setApplicationName("Drive API Migration")
                                    .build();

                    // The DriveServiceHelper encapsulates all REST API and SAF functionality.
                    // Its instantiation is required before handling any onClick actions.
                    mDriveServiceHelper = new DriveServiceHelper(googleDriveService);
                })
                .addOnFailureListener(exception -> Log.e(TAG, "Unable to sign in.", exception));
    }
}