package co.schmitt.android.keyringdroid;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.accounts.GoogleAccountManager;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    static final int REQUEST_ACCOUNT_PICKER = 1;
    static final int REQUEST_AUTHORIZATION = 2;
    static final int CAPTURE_IMAGE = 3;
    private static Uri fileUri;
    private static Drive service;
    private static final String keyringFolderName = "keyrings";
    private String keyringsFolderId;
    private GoogleAccountCredential credential;
    private List<File> keyringFiles;
    private String[] mPlanetTitles;
    // UI Elements
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;


    private ListView mDrawerList;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        credential = GoogleAccountCredential.usingOAuth2(this, DriveScopes.DRIVE);
        credential.setSelectedAccountName("philipp@schmitt.co");
        service = getDriveService(credential);

        // Drawer initialization
        mPlanetTitles = getResources().getStringArray(R.array.planets_array);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
                                   mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.drawable.ic_drawer,  /* nav drawer icon to replace 'Up' caret */
                R.string.drawer_open,  /* "open drawer" description */
                R.string.drawer_close  /* "close drawer" description */
                ) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                getActionBar().setTitle("Drawer closed");
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                getActionBar().setTitle("Drawer open");
            }
        };
        mDrawerList = (ListView) findViewById(R.id.left_drawer);
        // Set the adapter for the list view
        mDrawerList.setAdapter(new ArrayAdapter<String>(this,
                R.layout.drawer_list_item, mPlanetTitles)
        );
        // Set the list's click listener
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);

//        startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
        // startCameraIntent();
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
//            sync();
            requestSync();
        }
        return super.onOptionsItemSelected(item);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        credential.setSelectedAccountName(accountName);
                        service = getDriveService(credential);
                        startCameraIntent();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == Activity.RESULT_OK) {
//                    sync();
                } else {
                    startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
                }
                break;
            case CAPTURE_IMAGE:
                if (resultCode == Activity.RESULT_OK) {
//                    sync();
                }
        }
    }

    /* The click listner for ListView in the navigation drawer */
    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            selectItem(position);
        }
    }

    private void selectItem(int position) {
        // update the main content by replacing fragments
        Fragment fragment = new PlanetFragment();
        Bundle args = new Bundle();
        args.putInt(PlanetFragment.ARG_PLANET_NUMBER, position);
        fragment.setArguments(args);

        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit();

        // update selected item and title, then close the drawer
        mDrawerList.setItemChecked(position, true);
        setTitle(mPlanetTitles[position]);
        mDrawerLayout.closeDrawer(mDrawerList);
    }


    private void startCameraIntent() {
        String mediaStorageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES).getPath();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        fileUri = Uri.fromFile(new java.io.File(mediaStorageDir + java.io.File.separator + "IMG_"
                + timeStamp + ".jpg"));

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
        startActivityForResult(cameraIntent, CAPTURE_IMAGE);
    }

    private void checkIfFolderExists() throws IOException {
        About about = service.about().get().execute();
        Log.i("krg", "Root folder ID: " + about.getRootFolderId());
        Drive.Files.List request =
                service.files().list()
                        .setQ("'" + about.getRootFolderId() + "' in parents " +
                                "and mimeType='application/vnd.google-apps.folder' " +
                                "and trashed=false " +
                                "and title='" + keyringFolderName + "'");
        FileList files = request.execute();
        if (files.getItems().size() == 0) {
            Log.i("krg", "Found NO matching folder. Creating...");
            File body = new File();
            body.setTitle(keyringFolderName);
            body.setMimeType("application/vnd.google-apps.folder");
            File file = service.files().insert(body).execute();
            if (file != null) {
                keyringsFolderId = file.getId();
                Log.i("krg", "Keyrings folder ID: " + file.getId());
            }
        } else {
            keyringsFolderId = files.getItems().get(0).getId();
            Log.i("krg", "Found matching folder : " + keyringsFolderId);
        }
    }

    private List<File> getKeyringFiles() throws IOException {
        Log.i("krg", keyringsFolderId);
        if (keyringsFolderId == null) {
            return null;
        }
        Drive.Files.List request =
                service.files().list()
                        .setQ("'" + keyringsFolderId + "' in parents and trashed=false");
        FileList files = request.execute();
        List<File> keyrings = new ArrayList<File>();
        if (files.getItems().size() > 0) {
            for (File file : files.getItems()) {
                if (file.getTitle().endsWith(".keyring")) {
                    Log.i("krg", file.getMd5Checksum());
                    // Download file to internal storage
                    Log.i("krg", file.getDownloadUrl());
                    HttpResponse resp =
                            service.getRequestFactory().buildGetRequest(new GenericUrl(file.getDownloadUrl()))
                                    .execute();
                    InputStream downloadedFile = resp.getContent();
                    FileOutputStream outputStream = openFileOutput(file.getTitle(), Context.MODE_PRIVATE);
                    Log.i("krg", "Content: " + downloadedFile);
                    byte buffer[] = new byte[1024];
                    int length;
                    while ((length = downloadedFile.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                    downloadedFile.close();
                    outputStream.close();
                    // Add filename to list
                    keyrings.add(file);
                }
            }
        }
        return keyrings;
    }

    private void requestSync() {
        final GoogleAccountManager accountManager = new GoogleAccountManager(this);
        Account account = accountManager.getAccountByName("philipp@schmitt.co");
        DriveSyncer syncer = new DriveSyncer(this, account); //(this, new KeyringProvider(), account);
        syncer.performSync();
//        String accountName = credential.getSelectedAccountName();
//        if (accountName != null && accountName.length() > 0) {
//            final GoogleAccountManager accountManager = new GoogleAccountManager(this);
//            Account account = accountManager.getAccountByName(accountName);
//
//            if (account != null) {
//                Bundle options = new Bundle();
//                options.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
//                ContentResolver.requestSync(account, "co.schmitt.android.provider.KeyringDroid", options);
//            }
//        }
    }

/*    private void sync() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    checkIfFolderExists();
                    keyringFiles = getKeyringFiles();
                    Log.i("krg", "FILES LIST UPDATED: " + keyringFiles.size());
                    updateList();
                    // File's binary content
                    *//*java.io.File fileContent = new java.io.File(fileUri.getPath());
                    FileContent mediaContent = new FileContent("image/jpeg", fileContent);

                    // File's metadata.
                    File body = new File();
                    body.setTitle(fileContent.getName());
                    body.setMimeType("image/jpeg");
                    body.setParents(Arrays.asList(new ParentReference().setId(keyringsFolderId)));
                    File file = service.files().insert(body, mediaContent).execute();
                    if (file != null) {
                        showToast("Photo uploaded: " + file.getTitle());
                        startCameraIntent();
                    }*//*
                } catch (UserRecoverableAuthIOException e) {
                    startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();
    }*/

    private Drive getDriveService(GoogleAccountCredential credential) {
        return new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential)
                .build();
    }

    private void updateList() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
               /* final ListView listview= (ListView) findViewById(R.id.keyringListView);
                final ArrayList<String> list = new ArrayList<String>();
                for (File kr : keyringFiles) {
                    list.add(kr.getTitle());
                }
                ArrayAdapter arrayAdapter = new ArrayAdapter(getApplicationContext(), android.R.layout.simple_list_item_1, list);
                listview.setAdapter(arrayAdapter);*/
            }
        });
    }

    public void showToast(final String toast) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toast, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Fragment that appears in the "content_frame", shows a planet
     */
    public static class PlanetFragment extends Fragment {
        public static final String ARG_PLANET_NUMBER = "planet_number";

        public PlanetFragment() {
            // Empty constructor required for fragment subclasses
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_planet, container, false);
            int i = getArguments().getInt(ARG_PLANET_NUMBER);
            String planet = getResources().getStringArray(R.array.planets_array)[i];

            int imageId = getResources().getIdentifier(planet.toLowerCase(Locale.getDefault()),
                    "drawable", getActivity().getPackageName());
            ((ImageView) rootView.findViewById(R.id.image)).setImageResource(imageId);
            getActivity().setTitle(planet);
            return rootView;
        }
    }
}