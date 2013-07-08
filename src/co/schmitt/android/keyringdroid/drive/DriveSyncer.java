package co.schmitt.android.keyringdroid.drive;

import android.accounts.Account;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import co.schmitt.android.keyringdroid.KeyringUri;
import co.schmitt.android.keyringdroid.KeyringVault;
import co.schmitt.android.keyringdroid.MainActivity;
import co.schmitt.android.keyringdroid.R;
import co.schmitt.android.keyringdroid.utils.MD5;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    private static final String MIME_BINARY = "application/octet-stream";
    private static final String KEYRING_EXTENSION = "keyring";

    /**
     * Projection used for querying the database.
     */
    private static final String[] PROJECTION = new String[]{KeyringVault.Keyrings._ID, KeyringVault.Keyrings.COLUMN_NAME_TITLE, KeyringVault.Keyrings.COLUMN_NAME_FILENAME, KeyringVault.Keyrings.COLUMN_NAME_MODIFICATION_DATE, KeyringVault.Keyrings.COLUMN_NAME_FILE_ID, KeyringVault.Keyrings.COLUMN_NAME_DELETED};

    /**
     * The index of the projection columns
     */
    private static final int COLUMN_INDEX_ID = 0;
    private static final int COLUMN_INDEX_TITLE = 1;
    private static final int COLUMN_INDEX_FILENAME = 2;
    private static final int COLUMN_INDEX_MODIFICATION_DATE = 3;
    private static final int COLUMN_INDEX_FILE_ID = 4;
    private static final int COLUMN_INDEX_DELETED = 5;

    private Context mContext;
    private ContentProviderClient mProvider;
    private Account mAccount;
    private Drive mService;
    private String mKeyringsFolderId;
    private long mLargestChangeId;
    private String mToken;

    /**
     * Instantiate a new DriveSyncer.
     *
     * @param context  Context to use on credential requests.
     * @param provider Provider to use for database requests.
     * @param account  Account to perform sync for.
     */
    public DriveSyncer(Context context, ContentProviderClient provider, Account account) {
        mContext = context;
        mProvider = provider;
        mAccount = account;
        mService = getDriveService();
        mKeyringsFolderId = getKeyringsFolderId();
        mLargestChangeId = getLargestChangeId();
    }

    /**
     * Retrieve the largest change ID for the current user if available.
     *
     * @return The largest change ID, {@code -1} if not available.
     */
    private long getLargestChangeId() {
        return PreferenceManager.getDefaultSharedPreferences(mContext).getLong("largest_change_" + mAccount.name, -1);
    }

    /**
     * Store the largest change ID for the current user.
     *
     * @param changeId The largest change ID to store.
     */
    private void setLargestChangeId(long changeId) {
        String largestChangeKey = mContext.getString(R.string.prefs_largest_change);
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        editor.putLong(largestChangeKey + mAccount.name, changeId);
        editor.commit();
        mLargestChangeId = changeId;
    }

    /**
     * Retrieve the file ID of our keyrings/ folder
     *
     * @return The Drive file of keyrings/
     */
    private String getKeyringsFolderId() {
        String folderIdKey = mContext.getString(R.string.prefs_keyrings_folder_id) + mAccount.name;
        return PreferenceManager.getDefaultSharedPreferences(mContext).getString(folderIdKey, null);
    }

    /**
     * Store the file ID of out keyrings/ folder
     *
     * @param keyringsFolderId The folderID of keyrings/
     */
    private void setKeyringsFolderId(String keyringsFolderId) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        editor.putString(mContext.getString(R.string.prefs_keyrings_folder_id) + mAccount.name, keyringsFolderId);
        editor.commit();
        mKeyringsFolderId = keyringsFolderId;
    }

    /**
     * Retrieve the local path where keyring files are stored
     * Example: /data/data/co.schmitt.android.keyringdroid/files/$EMAIL/
     *
     * @return The local keyrings path
     */
    private String getLocalParentFolderPath() {
        return mContext.getFilesDir() + java.io.File.separator + mAccount.name + java.io.File.separator;
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
            ArrayList<String> scopes = new ArrayList<String>();
            scopes.add(DriveScopes.DRIVE);
            GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(mContext, scopes);
            credential.setSelectedAccountName(mAccount.name);
            // Trying to get a token right away to see if we are authorized
            try {
                mToken = credential.getToken();//getAccessToken();
            } catch (UserRecoverableAuthException e) {
                // Ask for authorization
                Intent authRequiredIntent = new Intent(MainActivity.LB_AUTH_APP);
                authRequiredIntent.putExtra(MainActivity.EXTRA_AUTH_APP_INTENT, e.getIntent());
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(authRequiredIntent);
            } catch (GoogleAuthException e) {
                //                Log.e(getClass().getSimpleName(), "Fatal authorization exception", e);
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mService = new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential).build();
        }
        return mService;
    }

    /**
     * Perform a synchronization for the current account.
     * <p/>
     * TODO Import new Keyrings from Drive
     */
    public void performSync() {
        if (mService == null) {
            return;
        }

        Log.d(TAG, "Performing sync for " + mAccount.name);

        if (mLargestChangeId == -1) {
            // First sync
            performFullSync();
        } else {
            Map<String, File> files = getChangedFiles(mLargestChangeId);
            Uri uri = KeyringUri.getKeyringsUri(mAccount.name);

            try {
                Cursor cursor = mProvider.query(uri, PROJECTION, KeyringVault.Keyrings.COLUMN_NAME_FILE_ID + " IS NOT NULL", null, null);
                Log.d(TAG, "Got local files: " + cursor.getCount());
                for (boolean more = cursor.moveToFirst(); more; more = cursor.moveToNext()) {
                    // Merge.
                    String fileId = cursor.getString(COLUMN_INDEX_FILE_ID);
                    Uri localFileUri = KeyringUri.getFileUri(mAccount.name, fileId);

                    Log.d(TAG, "Processing local file with drive ID: " + fileId);
                    if (files.containsKey(fileId)) {
                        File driveFile = files.get(fileId);
                        if (driveFile != null) {
                            // Merge the files.
                            mergeFiles(localFileUri, cursor, driveFile);
                        } else {
                            Log.d(TAG, "  > Deleting local file: " + fileId);
                            // The file does not exist in Drive anymore, delete it.
                            mProvider.delete(localFileUri, null, null);
                        }
                        files.remove(fileId);
                    } else {
                        // The file has not been updated on Drive, eventually update the Drive file.
                        File driveFile = mService.files().get(fileId).execute();
                        mergeFiles(localFileUri, cursor, driveFile);
                    }
                    mContext.getContentResolver().notifyChange(localFileUri, null, false);
                }

                // Any remaining files in the map are files that do not exist in the local database.
                insertNewDriveFiles(files.values());
                setLargestChangeId(mLargestChangeId + 1);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Performs a full sync, usually occurs the first time a sync occurs for the
     * account.
     */
    private void performFullSync() {
        Log.d(TAG, "Performing FULL sync for " + mAccount.name);
        String keyringFolderName = mContext.getString(R.string.keyring_folder);
        try {
            // Get the largest change Id first to avoid race conditions.
            About about = mService.about().get().execute();
            Drive.Files.List request = mService.files().list().setQ("'" + about.getRootFolderId() + "' in parents " +
                    "and mimeType='application/vnd.google-apps.folder' " +
                    "and trashed=false " +
                    "and title='" + keyringFolderName + "'");
            FileList files = request.execute();
            if (files.getItems().size() == 0) {
                // Keyrings folder NOT found
                createParentFolder();
                insertNewLocalFiles();
            } else {
                // Keyrings folder found
                setKeyringsFolderId(files.getItems().get(0).getId());
                Log.i(TAG, "Found matching folder : " + mKeyringsFolderId);
                // TODO filter by fileExtension='keyring' and mimetype='application/octet-stream' instead of searching for matching files afterwards
                request = mService.files().list().setQ("'" + mKeyringsFolderId + "' in parents and trashed=false"); //and fileExtension='keyring'");
                Log.d(TAG, "QUERY: " + mService.files().list().getQ());
                files = request.execute();
                if (files.getItems().size() > 0) {
                    List<File> keyringFiles = new ArrayList<File>();
                    for (File file : files.getItems()) {
                        if (file.getTitle().endsWith(".keyring")) {
                            keyringFiles.add(file);
                        }
                    }
                    insertNewDriveFiles(keyringFiles);
                    setLargestChangeId(about.getLargestChangeId());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Merge a local file with a Google Drive File.
     * <p/>
     * The last modification is used to check which file to sync from. Then, the
     * md5 checksum of the file is used to check whether or not the file's content
     * should be sync'ed.
     * <p/>
     * TODO Delete keyrings that were deleted from drive + Search for duplicates
     *
     * @param localFileUri    Local file URI to save local changes against.
     * @param localFileCursor Local file cursor to retrieve data from.
     * @param driveFile       Google Drive file.
     */
    private void mergeFiles(Uri localFileUri, Cursor localFileCursor, File driveFile) {
        long localFileModificationDate = localFileCursor.getLong(COLUMN_INDEX_MODIFICATION_DATE);
        String localFilename = localFileCursor.getString(COLUMN_INDEX_FILENAME);
        java.io.File localFile = new java.io.File(getLocalParentFolderPath(), localFilename);
        String localMd5 = MD5.calculateMD5(localFile);

        Log.d(TAG, "Modification dates: " + localFileModificationDate + " - " + driveFile.getModifiedDate().getValue());
        Log.d(TAG, "Processing drive file " + driveFile.getTitle());
        if (localFileModificationDate > driveFile.getModifiedDate().getValue()) {
            // Update remote file (Drive)
            try {
                if (localFileCursor.getShort(COLUMN_INDEX_DELETED) != 0) {
                    Log.d(TAG, "  > Deleting Drive file.");
                    mService.files().delete(driveFile.getId()).execute();
                    mProvider.delete(localFileUri, null, null);
                } else {
                    File updatedFile = null;

                    // Update drive file.
                    Log.d(TAG, "  > Updating Drive file.");
                    driveFile.setTitle(localFileCursor.getString(COLUMN_INDEX_TITLE));

                    if (localMd5 != driveFile.getMd5Checksum()) {
                        // TODO actual merge ! Also: upload/download ?
                        // Update both content and metadata.
                        //                        ByteArrayContent content = ByteArrayContent.fromString(TEXT_PLAIN, localNote);
                        //                        updatedFile = mService.files().update(driveFile.getId(), driveFile, content).execute();
                    } else {
                        // Only update the metadata.
                        updatedFile = mService.files().update(driveFile.getId(), driveFile).execute();
                    }

                    ContentValues values = new ContentValues();
                    values.put(KeyringVault.Keyrings.COLUMN_NAME_MODIFICATION_DATE, updatedFile.getModifiedDate().getValue());
                    mProvider.update(localFileUri, values, null, null);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else if (localFileModificationDate < driveFile.getModifiedDate().getValue()) {
            // Update local file.
            Log.d(TAG, "  > Updating local file.");

            try {
                if (driveFile.getLabels().getTrashed()) {
                    //if (driveFile.getExplicitlyTrashed()) {
                    // Remove local entry
                    //                    mProvider.delete(localFileUri, null, null);
                    // Remove local file
                    if (localFile.delete()) {
                        Log.d(TAG, "Removed file " + localFile.getName());
                    }
                    // Remove Drive file permanently (skip trash)
                    mService.files().delete(driveFile.getId()).execute();
                } else {
                    // Only download the content if it has changed.
                    if (!localMd5.equals(driveFile.getMd5Checksum())) {
                        downloadDriveFile(driveFile);
                    }
                    ContentValues values = new ContentValues();
                    values.put(KeyringVault.Keyrings.COLUMN_NAME_TITLE, driveFile.getTitle());
                    values.put(KeyringVault.Keyrings.COLUMN_NAME_MODIFICATION_DATE, driveFile.getModifiedDate().getValue());
                    mProvider.update(localFileUri, values, null, null);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Insert (upload) all new local files in Google Drive.
     */
    private void insertNewLocalFiles() {
        Uri uri = KeyringUri.getKeyringsUri(mAccount.name);

        if (!parentFolderExists()) {
            createParentFolder();
        }

        try {
            Cursor cursor = mProvider.query(uri, PROJECTION, KeyringVault.Keyrings.COLUMN_NAME_FILE_ID + " is NULL", null, null);

            Log.d(TAG, "Inserting new local files: " + cursor.getCount());

            if (cursor.moveToFirst()) {
                do {
                    Uri localFileUri = KeyringUri.getKeyringUri(mAccount.name, cursor.getString(COLUMN_INDEX_ID));

                    if (cursor.getShort(COLUMN_INDEX_DELETED) != 0) {
                        mProvider.delete(localFileUri, null, null);
                    } else {

                        File newFile = new File();
                        File insertedFile = null;
                        String fileName = cursor.getString(COLUMN_INDEX_FILENAME);

                        Log.i(TAG, "Uploading " + fileName);

                        java.io.File localFile = new java.io.File(getLocalParentFolderPath(), fileName);
                        FileContent mediaContent = new FileContent(null, localFile);
                        newFile.setTitle(fileName + "_UPLOADEDBYKRD");


                        // Set the parent folder.
                        if (mKeyringsFolderId != null && mKeyringsFolderId.length() > 0) {
                            newFile.setParents(Arrays.asList(new ParentReference().setId(mKeyringsFolderId)));
                        }

                        try {
                            insertedFile = mService.files().insert(newFile, mediaContent).execute();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        // Update the local file to add the file ID.
                        ContentValues values = new ContentValues();
                        values.put(KeyringVault.Keyrings.COLUMN_NAME_MODIFICATION_DATE, insertedFile.getModifiedDate().getValue());
                        values.put(KeyringVault.Keyrings.COLUMN_NAME_CREATE_DATE, insertedFile.getCreatedDate().getValue());
                        values.put(KeyringVault.Keyrings.COLUMN_NAME_FILE_ID, insertedFile.getId());

                        mProvider.update(localFileUri, values, null, null);
                    }
                } while (cursor.moveToNext());
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Insert new Google Drive files in the local database.
     *
     * @param driveFiles Collection of Google Drive files to insert.
     */
    private void insertNewDriveFiles(Collection<File> driveFiles) {
        Log.d(TAG, "Inserting new Drive files: " + driveFiles.size());
        Uri uri = KeyringUri.getKeyringsUri(mAccount.name);

        for (File driveFile : driveFiles) {
            String fileName = driveFile.getTitle();
            if (driveFile != null && fileName != null && fileName.endsWith(KEYRING_EXTENSION)) {
                ContentValues values = new ContentValues();
                values.put(KeyringVault.Keyrings.COLUMN_NAME_ACCOUNT, mAccount.name);
                values.put(KeyringVault.Keyrings.COLUMN_NAME_FILE_ID, driveFile.getId());
                values.put(KeyringVault.Keyrings.COLUMN_NAME_TITLE, driveFile.getTitle());
                values.put(KeyringVault.Keyrings.COLUMN_NAME_FILENAME, driveFile.getTitle());
                values.put(KeyringVault.Keyrings.COLUMN_NAME_CREATE_DATE, driveFile.getCreatedDate().getValue());
                values.put(KeyringVault.Keyrings.COLUMN_NAME_MODIFICATION_DATE, driveFile.getModifiedDate().getValue());

                try {
                    downloadDriveFile(driveFile);
                    mProvider.insert(uri, values);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mContext.getContentResolver().notifyChange(uri, null, false);
    }

    /**
     * Check if the keyrings/ folder already exists.
     *
     * @return True, if /My Files/keyrings/ exists
     */
    private boolean parentFolderExists() {
        String keyringFolderName = mContext.getString(R.string.keyring_folder);
        boolean exists = false;
        try {
            // Search by name
            About about = mService.about().get().execute();
            Log.i(TAG, "Root folder ID: " + about.getRootFolderId());
            Drive.Files.List request = mService.files().list().setQ("'" + about.getRootFolderId() + "' in parents " +
                    "and mimeType='application/vnd.google-apps.folder' " +
                    "and trashed=false " +
                    "and title='" + keyringFolderName + "'");
            FileList files = request.execute();
            if (files.getItems().size() == 0) {
                exists = false;
            } else {
                setKeyringsFolderId(files.getItems().get(0).getId());
                Log.i(TAG, "Found matching folder : " + mKeyringsFolderId);
                exists = true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return exists;
    }

    /**
     * Create a folder named keyrings in the user's root directory
     *
     * @return The fileID of the newly created folder, null if its creation failed
     */
    private String createParentFolder() {
        Log.i(TAG, "Found NO matching folder. Creating...");
        File body = new File();
        body.setTitle(mContext.getString(R.string.keyring_folder));
        body.setMimeType("application/vnd.google-apps.folder");
        try {
            File file = mService.files().insert(body).execute();
            if (file != null) {
                setKeyringsFolderId(file.getId());
                return file.getId();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
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
        // TODO only get through changes in keyrings/ folder ?
        try {
            Drive.Changes.List request = mService.changes().list().setStartChangeId(changeId);
            do {
                ChangeList changes = request.execute();
                long largestChangeId = changes.getLargestChangeId();

                for (Change change : changes.getItems()) {
                    // TODO proper handling of deleted files
                    if (change.getDeleted()) {
                        result.put(change.getFileId(), null);
                    }
                    // TODO ?
                    else if (MIME_BINARY.equals(change.getFile().getMimeType())) {
                        String fileName = change.getFile().getTitle();
                        if (fileName != null && fileName.endsWith(KEYRING_EXTENSION)) {
                            result.put(change.getFileId(), change.getFile());
                        }
                    }
                }
                if (largestChangeId > mLargestChangeId) {
                    setLargestChangeId(largestChangeId);
                }
                request.setPageToken(changes.getNextPageToken());
            } while (request.getPageToken() != null && request.getPageToken().length() > 0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "Got changed Drive files: " + result.size());
        return result;
    }


    /**
     * Upload a file to Google Drive
     *
     * @param localFile The file to upload
     * @return The uploaded Drive File, null if upload failed
     * @throws IOException
     */
    private File uploadFileToDrive(java.io.File localFile) throws IOException {
        Log.i(TAG, "Uploading " + localFile.getName());
        // File's metadata.
        File body = new File();
        body.setTitle(localFile.getName() + "_ADDED BY KEYRINGDROID");

        // Set the parent folder.
        if (mKeyringsFolderId != null && mKeyringsFolderId.length() > 0) {
            body.setParents(Arrays.asList(new ParentReference().setId(mKeyringsFolderId)));
        }

        // File's content.
        FileContent mediaContent = new FileContent(null, localFile);//new FileContent(mimeType, localFile);
        try {
            return mService.files().insert(body, mediaContent).execute();
        } catch (IOException e) {
            System.out.println("An error occured: " + e);
            return null;
        }
    }

    /**
     * Download a file from Google Drive to internal storage
     *
     * @param driveFile The file to download
     * @throws IOException
     */
    private void downloadDriveFile(File driveFile) throws IOException {
        Log.d(TAG, "Downloading " + driveFile.getDownloadUrl());
        HttpResponse resp = mService.getRequestFactory().buildGetRequest(new GenericUrl(driveFile.getDownloadUrl())).execute();
        InputStream downloadedFile = resp.getContent();
        java.io.File parentFolder = new java.io.File(getLocalParentFolderPath());
        parentFolder.mkdirs();
        Log.d(TAG, "Create folder " + parentFolder.getAbsolutePath());
        FileOutputStream outputStream = new FileOutputStream(parentFolder + java.io.File.separator + driveFile.getTitle(), false);//(driveFile.getTitle(), Context.MODE_PRIVATE);//mContext.openFileOutput(driveFile.getTitle(), Context.MODE_PRIVATE);
        Log.d(TAG, "Content: " + downloadedFile);
        Log.d(TAG, "MIME-Type:" + driveFile.getMimeType());

        byte buffer[] = new byte[1024];
        int length;
        while ((length = downloadedFile.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
        }
        downloadedFile.close();
        outputStream.close();
    }
}