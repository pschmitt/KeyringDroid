package co.schmitt.android.keyringdroid;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

/**
 * Created by pschmitt on 7/8/13.
 */
public class KeyringActivity extends Activity {

    private static final String TAG = "KeyringActivity";

    private String mKeyringName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.keyring_activity);
        // Get the keyring
        Intent intent = getIntent();
        mKeyringName = intent.getStringExtra(MainActivity.EXTRA_KEYRING);
        Toast.makeText(this, mKeyringName, 3).show();
        File keyringFile = getKeyringFile();
        if (keyringFile != null) {
            Log.d(TAG, "Loaded keyring file " + keyringFile);
            // TODO Do something
        }
    }

    private File getKeyringFile() {
        String accountNameKey = getString(R.string.prefs_account_name);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String accountName = preferences.getString(accountNameKey, null);
        //        String[] PROJECTION = new String[]{KeyringVault.Keyrings._ID, KeyringVault.Keyrings.COLUMN_NAME_TITLE, KeyringVault.Keyrings.COLUMN_NAME_FILENAME, KeyringVault.Keyrings.COLUMN_NAME_MODIFICATION_DATE, KeyringVault.Keyrings.COLUMN_NAME_FILE_ID, KeyringVault.Keyrings.COLUMN_NAME_DELETED};
        //        Uri uri = KeyringUri.getKeyringUri(accountName, keyringName);
        //        Cursor c = getContentResolver().query(uri, PROJECTION, KeyringVault.Keyrings.COLUMN_NAME_FILE_ID + " IS NOT NULL", null, null);
        return new File(getFilesDir() + java.io.File.separator + accountName + java.io.File.separator + mKeyringName);
    }
}
