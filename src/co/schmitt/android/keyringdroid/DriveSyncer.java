package co.schmitt.android.keyringdroid;

import android.accounts.Account;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.*;
import android.net.Uri;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

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
    // TODO Store in sharedPreferences ?
    private String mKeyringsFolderId;

    /**
     * Instantiate a new DriveSyncer.
     *
     * @param context Context to use on credential requests.
     *                // @param provider Provider to use for database requests.
     * @param account Account to perform sync for.
     */
    public DriveSyncer(Context context, ContentProviderClient provider, Account account) {
        mContext = context;
        mProvider = provider;
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
        String firstLaunchKey = mContext.getString(R.string.prefs_first_launch);
        boolean firstLaunch = prefs.contains(firstLaunchKey);
        if (firstLaunch)
            prefs.edit().putBoolean(firstLaunchKey, false).commit();
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
                ArrayList<String> scopes = new ArrayList<String>();
                scopes.add(DriveScopes.DRIVE);
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(mContext, scopes);
//                GoogleAccountCredential credential =
//                        GoogleAccountCredential.usingOAuth2(mContext, DriveScopes.DRIVE_FILE);
                credential.setSelectedAccountName(mAccount.name);
                // Trying to get a token right away to see if we are authorized
                //credential.getToken();
                mService = new Drive.Builder(AndroidHttp.newCompatibleTransport(),
                        new GsonFactory(), credential).build();
            } catch (Exception e) {
                Log.e(TAG, "Failed to get token");
                // If the Exception is User Recoverable, we display a notification that will trigger the
                // intent to fix the issue.
                // TODO catch UserRecoverableAuthException directly and do not show this buggy notification
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
            // TODO remove following debug line
            performFullSync();
        }
    }

    /**
     * Insert all new local files in Google Drive.
     */
    private void insertNewLocalFiles() {
        List<File> keyringFiles = new ArrayList<File>();
//        try {
        if (!parentFolderExists()) {
            createParentFolder();
        }
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

    /**
     * Create a folder named keyring in the user's root directory
     */
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
     * Retrieve a collection of files that have changed since the provided
     * {@code changeId}.
     *
     * @param changeId Change ID to retrieve changed files from.
     * @return Map of changed files key'ed by their file ID.
     */
    private Map<String, File> getChangedFiles(long changeId) {
        Map<String, File> result = new HashMap<String, File>();

        /*try {
            Drive.Changes.List request = mService.changes().list().setStartChangeId(changeId);
            do {
                ChangeList changes = request.execute();
                long largestChangeId = changes.getLargestChangeId().longValue();

                for (Change change : changes.getItems()) {
                    if (change.getDeleted()) {
                        result.put(change.getFileId(), null);
                    } else if (TEXT_PLAIN.equals(change.getFile().getMimeType())) {
                        result.put(change.getFileId(), change.getFile());
                    }
                }

                if (largestChangeId > mLargestChangeId) {
                    mLargestChangeId = largestChangeId;
                }
                request.setPageToken(changes.getNextPageToken());
            } while (request.getPageToken() != null && request.getPageToken().length() > 0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "Got changed Drive files: " + result.size() + " - " + mLargestChangeId);*/
        return result;
    }


    /**
     * Insert new Google Drive files in the local database.
     *
     * @param driveFiles Collection of Google Drive files to insert.
     */
    private void insertNewDriveFiles(Collection<File> driveFiles) {
        Log.d(TAG, "Inserting new Drive files: " + driveFiles.size());
        Uri uri = getKeyringsUri(mAccount.name);

        for (File driveFile : driveFiles) {
            if (driveFile != null) {
                ContentValues values = new ContentValues();
                values.put(Keyring.Keyrings.COLUMN_NAME_ACCOUNT, mAccount.name);
                values.put(Keyring.Keyrings.COLUMN_NAME_FILE_ID, driveFile.getId());
                values.put(Keyring.Keyrings.COLUMN_NAME_TITLE, driveFile.getTitle());
                values.put(Keyring.Keyrings.COLUMN_NAME_FILENAME, driveFile.getTitle());
                values.put(Keyring.Keyrings.COLUMN_NAME_CREATE_DATE, driveFile.getCreatedDate().getValue());
                values.put(Keyring.Keyrings.COLUMN_NAME_MODIFICATION_DATE, driveFile.getModifiedDate().getValue());
                try {
                    mProvider.insert(uri, values);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mContext.getContentResolver().notifyChange(uri, null, false);
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
                // Keyrings folder found
                createParentFolder();
//                insertNewLocalFiles();
            } else {
                mKeyringsFolderId = files.getItems().get(0).getId();
                Log.i(TAG, "Found matching folder : " + mKeyringsFolderId);
                request = mService.files().list().setQ("'" + mKeyringsFolderId + "' in parents and trashed=false");
                files = request.execute();
                if (files.getItems().size() > 0) {
                    for (File file : files.getItems()) {
                        if (file.getTitle().endsWith(".keyring")) {
                            Log.i(TAG, file.getMd5Checksum());
                            // Download file to internal storage
                            // Path: /data/data/co.schmitt.android.keyringdroid/files
                            Log.i(TAG, file.getDownloadUrl());
                            HttpResponse resp =
                                    mService.getRequestFactory().buildGetRequest(new GenericUrl(file.getDownloadUrl()))
                                            .execute();
                            InputStream downloadedFile = resp.getContent();
                            FileOutputStream outputStream = mContext.openFileOutput(file.getTitle(), Context.MODE_PRIVATE);
                            Log.i(TAG, "Content: " + downloadedFile);
                            byte buffer[] = new byte[1024];
                            int length;
                            while ((length = downloadedFile.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, length);
                            }
                            downloadedFile.close();
                            outputStream.close();
                        }
                    }
                    insertNewDriveFiles(files.getItems());
                }
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

    private static Uri getKeyringsUri(String accountName) {
        return Uri.parse("content://co.schmitt.android.provider.KeyringDroid/" + accountName + "/keyrings/");
    }

    private static Uri getKeyringUri(String accountName, String keyringId) {
        return Uri.parse("content://co.schmitt.android.provider.KeyringDroid/" + accountName + "/keyrings/" + keyringId);
    }

    private static Uri getFileUri(String accountName, String fileId) {
        return Uri.parse("content://co.schmitt.android.provider.KeyringDroid/" + accountName + "/keyrings/" + fileId);
    }

}