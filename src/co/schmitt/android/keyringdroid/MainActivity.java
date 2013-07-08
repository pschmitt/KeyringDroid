package co.schmitt.android.keyringdroid;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.*;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.*;
import android.widget.*;
import co.schmitt.android.keyringdroid.python.InstallAsyncTask;
import co.schmitt.android.keyringdroid.python.PythonInstaller;
import com.android.python27.BackgroundScriptService;
import com.android.python27.ScriptService;
import com.android.python27.config.GlobalConstants;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.services.drive.DriveScopes;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;

public class MainActivity extends Activity implements Observer {

    // For logging and debugging
    private static final String TAG = "MainActivity";

    // Intent extras
    public final static String EXTRA_KEYRING = "co.schmitt.android.keyringdroid.KEYRING";

    // Provider URI
    private String AUTHORITY = "co.schmitt.android.provider.KeyringDroid";

    // Keys for local broadcasts
    public static final String LB_REQUEST_ACCOUNT = "REQUEST_ACCOUNT";
    public static final String LB_AUTH_APP = "AUTH_APP";
    public static final String EXTRA_AUTH_APP_INTENT = "AUTH_APP_INTENT";

    // Request codes
    private static final int AUTH_APP = 1;
    private static final int REQUEST_ACCOUNT_PICKER = 2;
    private static final int REQUEST_AUTHORIZATION = 3;

    // UI Elements
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private Spinner mAccountSpinner;
    private ListView mDrawerList;
    private ListView mKeyringList;

    // Model
    private ArrayList<String> keyringList = new ArrayList<String>();
    private GoogleAccountCredential mCredential;
    private String[] mPlanetTitles;

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (LB_AUTH_APP.equals(action)) {
                Intent authAppIntent = intent.getParcelableExtra(EXTRA_AUTH_APP_INTENT);
                startActivityForResult(authAppIntent, AUTH_APP);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        // Initialize the UI
        setupNavigationDrawer();
        populateAccountSpinner();
        populateKeyringList();
        registerObservers();

        // install needed ?
        if (PythonInstaller.isInstallNeeded(this)) {
            setContentView(R.layout.install);
            new InstallAsyncTask(this).execute();
        } else {
            //            runScriptService();
        }
    }

    private void runScriptService() {
        if (GlobalConstants.IS_FOREGROUND_SERVICE) {
            startService(new Intent(this, ScriptService.class));
        } else {
            startService(new Intent(this, BackgroundScriptService.class));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter(LB_REQUEST_ACCOUNT));
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter(LB_AUTH_APP));
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity, menu);
        return true;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        if (item.getItemId() == R.id.menu_refresh) {
            requestSync();
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Make this activity react on model changes
     */
    private void registerObservers() {

    }

    /**
     * Setup the navigation Drawer
     */
    private void setupNavigationDrawer() {
        // Drawer initialization
        mAccountSpinner = (Spinner) findViewById(R.id.account_spinner);
        mPlanetTitles = getResources().getStringArray(R.array.planets_array);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.drawable.ic_drawer,  /* nav drawer icon to replace 'Up' caret */
                R.string.drawer_open,  /* "open drawer" description */
                R.string.drawer_close  /* "close drawer" description */) {
            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                getActionBar().setTitle(getString(R.string.app_name));
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                getActionBar().setTitle("Drawer opened");
            }
        };
        mDrawerList = (ListView) findViewById(R.id.left_drawer);
        // Set the adapter for the list view
        mDrawerList.setAdapter(new ArrayAdapter<String>(this, R.layout.drawer_list_item, mPlanetTitles));
        // Set the list's click listener
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);

        ArrayList<String> scopes = new ArrayList<String>();
        scopes.add(DriveScopes.DRIVE);
        mCredential = GoogleAccountCredential.usingOAuth2(this, scopes);
        String accountName = getSelectedAccountName();
        if (accountName != null) {
            mCredential.setSelectedAccountName(accountName);
        } else {
            startActivityForResult(mCredential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
        }
    }

    /**
     * The click listner for ListView in the navigation drawer - calls selectedItem
     */
    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            selectItem(position);
        }
    }

    /**
     * Get called when a settings item in NavigationDrawer is selected
     *
     * @param position The position in the NavigationDrawer ListView
     */
    private void selectItem(int position) {
        // update the main_activity content by replacing fragments
        Fragment fragment = new SettingsFragment();
        Bundle args = new Bundle();
        args.putInt(SettingsFragment.ARG_SETTING_NUMBER, position);
        fragment.setArguments(args);

        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit();

        // update selected item and title, then close the drawer
        mDrawerList.setItemChecked(position, true);
        setTitle(mPlanetTitles[position]);
        mDrawerLayout.closeDrawer(mDrawerList);
    }

    /**
     * Setup the account spinner in the NavigationDrawer
     */
    private void populateAccountSpinner() {
        // Populate account spinner
        AccountManager accMgr = AccountManager.get(this);
        Account[] accountList = accMgr.getAccountsByType("com.google");
        AccountAdapter accountAdapter = new AccountAdapter(this, R.layout.account_spinner_item, R.id.account_name, R.id.account_picture, accountList);
        mAccountSpinner.setAdapter(accountAdapter);
        mAccountSpinner.setSelection(accountAdapter.getPosition(mCredential.getSelectedAccount()));
        mAccountSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setSelectedAccountName(((Account) parent.getItemAtPosition(position)).name);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        // TODO Display the account pictures (as in the Drive App)
        //        ImageView profilePic = (ImageView) findViewById(R.id.profilePic);
        //        Cursor c = getContentResolver().query(ContactsContract.Profile.CONTENT_URI, null, null, null, null);
        //        int count = c.getCount();
        //        String[] columnNames = c.getColumnNames();
        //        String photoUri = c.getString(c.getColumnIndex("photo_thumb_uri"));
        //        Log.d(TAG, "Photo URI: " + photoUri);
        //        boolean b = c.moveToFirst();
        //        int position = c.getPosition();
        //        if (count == 1 && position == 0) {
        //            for (int j = 0; j < columnNames.length; j++) {
        //                String columnName = columnNames[j];
        //                String columnValue = c.getString(c.getColumnIndex(columnName));
        //                Log.d(TAG, "Namme: " + columnName);
        //                Log.d(TAG, "Value: " + columnValue);
        ////                ...
        //                // consume the values here
        //            }
        //        }
        //        c.close();
    }

    /**
     * Show the synced keyring in ListView
     * <p/>
     * TODO Add observer that repopulated the list after syncing
     */
    private void populateKeyringList() {
        // Populate keyring list
        String[] PROJECTION = new String[]{KeyringVault.Keyrings._ID, KeyringVault.Keyrings.COLUMN_NAME_TITLE, KeyringVault.Keyrings.COLUMN_NAME_FILENAME, KeyringVault.Keyrings.COLUMN_NAME_MODIFICATION_DATE, KeyringVault.Keyrings.COLUMN_NAME_FILE_ID, KeyringVault.Keyrings.COLUMN_NAME_DELETED};
        mKeyringList = (ListView) findViewById(R.id.keyring_list);
        Uri uri = KeyringUri.getKeyringsUri(getSelectedAccountName());
        Cursor cursor = getContentResolver().query(uri, PROJECTION, KeyringVault.Keyrings.COLUMN_NAME_FILE_ID + " IS NOT NULL", null, null);
        Log.d(TAG, "Got local files: " + cursor.getCount());
        keyringList.clear();
        for (boolean more = cursor.moveToFirst(); more; more = cursor.moveToNext()) {
            keyringList.add(cursor.getString(2));
        }
        ArrayAdapter<String> keyrings = new ArrayAdapter<String>(this, R.layout.keyring_list_item, keyringList);
        mKeyringList.setAdapter(keyrings);
        mKeyringList.setOnItemClickListener(new KeyringItemClickListener());
    }

    private class KeyringItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Intent intent = new Intent(getApplicationContext(), KeyringActivity.class);
            intent.putExtra(EXTRA_KEYRING, keyringList.get(position));
            startActivity(intent);
        }
    }


    /**
     * Retrieve the currently selected account name from SharedPreferences
     *
     * @return The currently selected account
     */
    private String getSelectedAccountName() {
        String accountNameKey = getString(R.string.prefs_account_name);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        return preferences.getString(accountNameKey, null);
    }

    /**
     * Set the currently selected account name and save it in SharedPreferences
     *
     * @param accountName
     */
    private void setSelectedAccountName(String accountName) {
        String accountNameKey = getString(R.string.prefs_account_name);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.edit().putString(accountNameKey, accountName).commit();
        mCredential.setSelectedAccountName(accountName);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        mCredential.setSelectedAccountName(accountName);
                        // Store account name in shared preferences
                        setSelectedAccountName(accountName);
                        enableAutoSync();
                        showToast("Selected account: " + mCredential.getSelectedAccountName());
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == Activity.RESULT_OK) {
                    requestSync();
                } else {
                    startActivityForResult(mCredential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
                }
                break;
        }
    }

    @Override
    public void update(Observable observable, Object data) {

    }

    /**
     * Enable sync
     */
    private void enableAutoSync() {
        ContentResolver.setIsSyncable(mCredential.getSelectedAccount(), AUTHORITY, 1);
        ContentResolver.setSyncAutomatically(mCredential.getSelectedAccount(), AUTHORITY, true);
    }

    /**
     * Request a synchronisation
     */
    private void requestSync() {
        Account account = mCredential.getSelectedAccount();
        if (account != null) {
            //            getContentResolver().notifyChange(rowUri, null, true);
            Bundle options = new Bundle();
            options.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
            ContentResolver.requestSync(account, AUTHORITY, options);
            populateKeyringList();
        }
    }

    /**
     * Simple wrapper that displays a toast message
     *
     * @param toast The message to display
     */
    public void showToast(final String toast) {
        Toast.makeText(getApplicationContext(), toast, Toast.LENGTH_SHORT).show();
    }

    /**
     * Fragment that appears in the "content_frame", shows a planet
     */
    public static class SettingsFragment extends Fragment {
        public static final String ARG_SETTING_NUMBER = "settings_number";

        public SettingsFragment() {
            // Empty constructor required for fragment subclasses
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_settings, container, false);
            int i = getArguments().getInt(ARG_SETTING_NUMBER);
            String planet = getResources().getStringArray(R.array.planets_array)[i];

            int imageId = getResources().getIdentifier(planet.toLowerCase(Locale.getDefault()), "drawable", getActivity().getPackageName());
            ((ImageView) rootView.findViewById(R.id.image)).setImageResource(imageId);
            getActivity().setTitle(planet);
            return rootView;
        }
    }
}