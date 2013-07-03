package co.schmitt.android.keyringdroid;

import android.accounts.Account;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: pschmitt
 * Date: 7/1/13
 * Time: 7:40 PM
 * To change this template use File | Settings | File Templates.
 */
public class DriveSyncer {
    /**
     * For logging and debugging purposes
     */
    private static final String TAG = "DriveSyncAdapter";

    private Context mContext;
    private ContentProviderClient mProvider;
    private Account mAccount;
    private Drive mService;
    private String mKeyringsFolderId;

    /**
     * Instantiate a new DriveSyncer.
     *
     * @param context  Context to use on credential requests.
     * // @param provider Provider to use for database requests.
     * @param account  Account to perform sync for.
     */
    public DriveSyncer(Context context, /* ContentProviderClient provider,*/ Account account) {
        mContext = context;
//        mProvider = provider;
        mAccount = account;
        mService = getDriveService();
    }

    /**
     * Check if this is the fist time this app was started.
     *
     * @return whether this is this app's first lauch
     */
    private boolean isFirstLauch() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean firstLaunch = prefs.contains("firstLaunch");
        if (firstLaunch)
            prefs.edit().putBoolean("fistLaunch", false);
        return firstLaunch;
    }

    /**
     * Retrieve a authorized service object to send requests to the Google Drive
     * API. On failure to retrieve an access token, a notification is sent to the
     * user requesting that authorization be granted for the
     * {@code https://www.googleapis.com/auth/drive.file} scope.
     *
     * @return An authorized service object.
     */
    private Drive getDriveService() {
        if (mService == null) {
            try {
                GoogleAccountCredential credential =
                        GoogleAccountCredential.usingOAuth2(mContext, DriveScopes.DRIVE_FILE);
                credential.setSelectedAccountName(mAccount.name);
                // Trying to get a token right away to see if we are authorized
                //credential.getToken();
                mService = new Drive.Builder(AndroidHttp.newCompatibleTransport(),
                        new GsonFactory(), credential).build();
            } catch (Exception e) {
                Log.e(TAG, "Failed to get token");
                // If the Exception is User Recoverable, we display a notification that will trigger the
                // intent to fix the issue.
                if (e instanceof UserRecoverableAuthException) {
                    UserRecoverableAuthException exception = (UserRecoverableAuthException) e;
                    NotificationManager notificationManager = (NotificationManager) mContext
                            .getSystemService(Context.NOTIFICATION_SERVICE);
                    Intent authorizationIntent = exception.getIntent();
                    authorizationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).addFlags(
                            Intent.FLAG_FROM_BACKGROUND);
                    PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0,
                            authorizationIntent, 0);
                    Notification notification = new Notification.Builder(mContext)
                            .setSmallIcon(android.R.drawable.ic_dialog_alert)
                            .setTicker("Permission requested")
                            .setContentTitle("Permission requested")
                            .setContentText("for account " + mAccount.name)
                            .setContentIntent(pendingIntent).setAutoCancel(true).build();
                    notificationManager.notify(0, notification);
                } else {
                    e.printStackTrace();
                }
            }
        }
        return mService;
    }

    /**
     * Perform a synchronization for the current account.
     */
    public void performSync() {
        if (mService == null) {
            return;
        }
        Log.d(TAG, "Performing sync for " + mAccount.name);
        if (isFirstLauch()) {
            performFullSync();
        } else {
            Log.d(TAG, "Performing selective sync for " + mAccount.name);
            insertNewLocalFiles();
        }
    }

    /**
     * Insert all new local files in Google Drive.
     */
    private void insertNewLocalFiles() {
        List<File> keyringFiles = new ArrayList<File>();
//        try {
//            if (!parentFolderExists()) {
//                createParentFolder();
//            }
//            keyringFiles = getKeyringFiles();    keyring
            Log.i(TAG, "FILES LIST UPDATED: " + keyringFiles.size());
//        } catch (UserRecoverableAuthIOException e) {
////            Activity.startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
//            Log.i(TAG, "FILES LIST UPDATED: " + keyringFiles.size());
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    private boolean parentFolderExists() {
        String keyringFolderName = mContext.getString(R.string.keyring_folder);
        boolean exists = false;
        try {
            About about = mService.about().get().execute();
            Log.i(TAG, "Root folder ID: " + about.getRootFolderId());
            Drive.Files.List request =
                    mService.files().list()
                            .setQ("'" + about.getRootFolderId() + "' in parents " +
                                    "and mimeType='application/vnd.google-apps.folder' " +
                                    "and trashed=false " +
                                    "and title='" + keyringFolderName + "'");
            FileList files = request.execute();
            if (files.getItems().size() == 0) {
                exists = false;
            } else {
                mKeyringsFolderId = files.getItems().get(0).getId();
                Log.i(TAG, "Found matching folder : " + mKeyringsFolderId);
                exists = true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return exists;
    }

    private void createParentFolder() {
        Log.i(TAG, "Found NO matching folder. Creating...");
        File body = new File();
        body.setTitle(mContext.getString(R.string.keyring_folder));
        body.setMimeType("application/vnd.google-apps.folder");
        try {
            File file = mService.files().insert(body).execute();
            if (file != null) {
                mKeyringsFolderId = file.getId();
                Log.i(TAG, "Keyrings folder ID: " + file.getId());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Insert new Google Drive files in the local database.
     *
     * @param driveFiles Collection of Google Drive files to insert.
     */
    private void insertNewDriveFiles(Collection<File> driveFiles) {

    }

    /**
     * Performs a full sync, usually occurs the first time a sync occurs for the
     * account.
     */
    private void performFullSync() {
        Log.d(TAG, "Performing FULL sync for " + mAccount.name);
        String keyringFolderName = mContext.getString(R.string.keyring_folder);
        About about;
        try {
            about = mService.about().get().execute();
            Drive.Files.List request =
                    mService.files().list()
                            .setQ("'" + about.getRootFolderId() + "' in parents " +
                                    "and mimeType='application/vnd.google-apps.folder' " +
                                    "and trashed=false " +
                                    "and title='" + keyringFolderName + "'");
            FileList files = request.execute();
            if (files.getItems().size() == 0) {
                Log.i(TAG, "Found NO matching folder. Creating...");
                File body = new File();
                body.setTitle(keyringFolderName);
                body.setMimeType("application/vnd.google-apps.folder");
                File file = mService.files().insert(body).execute();
                if (file != null) {
                    mKeyringsFolderId = file.getId();
                    Log.i(TAG, "Keyrings folder ID: " + file.getId());
                }
            } else {
                mKeyringsFolderId = files.getItems().get(0).getId();
                Log.i(TAG, "Found matching folder : " + mKeyringsFolderId);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getFileContent(File driveFile) {
        return null;
    }

    /**
     * Compute the MD5 checksum of the provided string data from <a
     * href="http://stackoverflow.com/a/304350/1106381"
     * >http://stackoverflow.com/a/304350/1106381</a>.
     *
     * @param string Data to compute the MD5 checksum from.
     * @return The MD5 checksum as string.
     */
    public static String md5(String string) {
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("MD5").digest(string.getBytes("UTF-8"));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Huh, MD5 should be supported?", e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Huh, UTF-8 should be supported?", e);
        }
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            if ((b & 0xFF) < 0x10) hex.append("0");
            hex.append(Integer.toHexString(b & 0xFF));
        }
        return hex.toString();
    }
}