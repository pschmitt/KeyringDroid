// Copyright 2012 Google Inc. All Rights Reserved.

package co.schmitt.android.keyringdroid;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import co.schmitt.android.keyringdroid.drive.DriveSyncAdapter;

/**
 * Created with IntelliJ IDEA.
 * User: pschmitt
 * Date: 7/1/13
 * Time: 7:55 PM
 * To change this template use File | Settings | File Templates.
 */
public class DriveSyncService extends Service {
    private static final Object sSyncAdapterLock = new Object();

    private static DriveSyncAdapter sSyncAdapter = null;

    @Override
    public void onCreate() {
        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new DriveSyncAdapter(getApplicationContext(), true);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return sSyncAdapter.getSyncAdapterBinder();
    }
}
