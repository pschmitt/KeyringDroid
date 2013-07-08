// Copyright 2012 Google Inc. All Rights Reserved.

package co.schmitt.android.keyringdroid.drive;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.preference.PreferenceManager;
import co.schmitt.android.keyringdroid.DriveSyncer;
import co.schmitt.android.keyringdroid.R;

/**
 * Created with IntelliJ IDEA.
 * User: pschmitt
 * Date: 7/1/13
 * Time: 7:53 PM
 */
public class DriveSyncAdapter extends AbstractThreadedSyncAdapter {

    /**
     * Constructs a new DriveSyncAdapter.
     *
     * @see AbstractThreadedSyncAdapter
     */
    public DriveSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        // Allow parallel sync
        //        super(context, autoInitialize, true);
    }

    @Override
    public void onPerformSync(Account account, Bundle bundle, String authority, ContentProviderClient provider, SyncResult syncResult) {
        DriveSyncer syncer = new DriveSyncer(getContext(), provider, account);
        // TODO set sync interval and result with SyncResult -> Pass it to syncer ?
        syncer.performSync();
        String syncIntervalKey = getContext().getString(R.string.prefs_sync_interval) + account.name;
        syncResult.delayUntil = PreferenceManager.getDefaultSharedPreferences(getContext()).getLong(syncIntervalKey, 5 * 60);
    }
}
