package co.schmitt.android.keyringdroid;

import android.accounts.Account;
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
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

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
    }

    /**
     * Check if this is the fist time this app was started.
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

        }
    }

    /**
     * Insert all new local files in Google Drive.
     */
    private void insertNewLocalFiles() {

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