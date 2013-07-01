// Copyright 2012 Google Inc. All Rights Reserved.

package co.schmitt.android.keyringdroid.drive;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;

/**
 * Created with IntelliJ IDEA.
 * User: pschmitt
 * Date: 7/1/13
 * Time: 7:53 PM
 * To change this template use File | Settings | File Templates.
 */
public class DriveSyncAdapter extends AbstractThreadedSyncAdapter {
    /** The context in which the Sync Adapter runs. */
    private Context mContext;

    /**
     * Constructs a new DriveSyncAdapter.
     * @see AbstractThreadedSyncAdapter
     */
    public DriveSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;
    }

    @Override
    public void onPerformSync(Account account, Bundle bundle, String authority,
                              ContentProviderClient provider, SyncResult syncResult) {
        DriveSyncer syncer = new DriveSyncer(mContext, provider, account);
        syncer.performSync();
    }
}
