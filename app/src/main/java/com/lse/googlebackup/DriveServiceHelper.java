package com.lse.googlebackup;

import android.os.Handler;
import android.os.Looper;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DriveServiceHelper {
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Drive mDriveService;

    public DriveServiceHelper(Drive driveService) {
        mDriveService = driveService;
    }

    public interface DriveCallback<String> {
        void onCreateComplete(String result);
    }

    public interface DriveListCallback<FileList> {
        void onSearchComplete(FileList result);
    }

    public void searchFile(DriveListCallback<FileList> callback) {
        executor.execute(() -> {
            FileList fileList;
            try {
                fileList = mDriveService.files().list()
                        .setSpaces("appDataFolder")
                        .setFields("nextPageToken, files(id, name)")
                        .setPageSize(10)
                        .execute();
            } catch (Exception e) {
                e.printStackTrace();
                fileList = null;
            }
            FileList finalResult = fileList;
            handler.post(() -> callback.onSearchComplete(finalResult));
        });
    }

    public void createFile(DriveCallback<String> callback, String fileName, String content) {
        executor.execute(() -> {
            File metadata = new File()
                    .setParents(Collections.singletonList("appDataFolder"))
                    .setMimeType("text/plain")
                    .setName(fileName);

            String result;
            try {
                ByteArrayContent contentStream = ByteArrayContent.fromString("text/plain", content);
                File googleFile = mDriveService.files().create(metadata, contentStream).execute();
                result = googleFile.getId();
            } catch (Exception e) {
                e.printStackTrace();
                result = null;
            }
            String finalResult = result;
            handler.post(() -> callback.onCreateComplete(finalResult));
        });
    }

    public void updateFile(DriveCallback<String> callback, String fileId, String content) {
        executor.execute(() -> {
            String result;
            try {
                ByteArrayContent contentStream = ByteArrayContent.fromString("text/plain", content);
                mDriveService.files().update(fileId, null, contentStream).execute();
                result = "done";
            } catch (Exception e) {
                e.printStackTrace();
                result = null;
            }
            String finalResult = result;
            handler.post(() -> callback.onCreateComplete(finalResult));
        });
    }

    public void readFile(DriveCallback<String> callback, String fileId) {
        executor.execute(() -> {
            String result;
            try {
                InputStream is = mDriveService.files().get(fileId).executeMediaAsInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder stringBuilder = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line);
                }
                result = stringBuilder.toString();
            } catch (Exception e) {
                e.printStackTrace();
                result = null;
            }
            String finalResult = result;
            handler.post(() -> callback.onCreateComplete(finalResult));
        });
    }
}