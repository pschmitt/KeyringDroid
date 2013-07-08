package co.schmitt.android.keyringdroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Created by pschmitt on 7/8/13.
 */
public class KeyringActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.keyring_activity);
        // Get the keyring
        Intent intent = getIntent();
        String keyringName = intent.getStringExtra(MainActivity.EXTRA_KEYRING);
        Toast.makeText(this, keyringName, 3).show();
    }
}
