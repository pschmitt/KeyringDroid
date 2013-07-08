package co.schmitt.android.keyringdroid.python;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import co.schmitt.android.keyringdroid.R;
import com.android.python27.BackgroundScriptService;
import com.android.python27.ScriptService;
import com.android.python27.config.GlobalConstants;
import com.android.python27.support.Utils;
import com.googlecode.android_scripting.FileUtils;

import java.io.File;
import java.io.InputStream;

public class InstallAsyncTask extends AsyncTask<Void, Integer, Boolean> {

    public static final String TAG = "PythonInstallAsyncTask";

    private Context mContext;

    public InstallAsyncTask(Context context) {
        mContext = context;
    }

    @Override
    protected void onPreExecute() {
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        Log.i(TAG, "Installing...");

        // show progress dialog
        //        sendmsg("showProgressDialog", "");

        //        sendmsg("setMessageProgressDialog", "Please wait...");
        createOurExternalStorageRootDir();

        // Copy all resources
        copyResourcesToLocal();

        // TODO
        return true;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
    }

    @Override
    protected void onPostExecute(Boolean installStatus) {
        //        sendmsg("dismissProgressDialog", "");
        if (installStatus) {
            //            sendmsg("installSucceed", "");
        } else {
            //            sendmsg("installFailed", "");
        }
        runScriptService();
    }

    private void runScriptService() {
        if (GlobalConstants.IS_FOREGROUND_SERVICE) {
            mContext.startService(new Intent(mContext, ScriptService.class));
        } else {
            mContext.startService(new Intent(mContext, BackgroundScriptService.class));
        }
    }

    private void copyResourcesToLocal() {
        String name, sFileName;
        InputStream content;

        R.raw a = new R.raw();
        java.lang.reflect.Field[] t = R.raw.class.getFields();
        Resources resources = mContext.getResources();

        boolean succeed = true;

        for (int i = 0; i < t.length; i++) {
            try {
                name = resources.getText(t[i].getInt(a)).toString();
                sFileName = name.substring(name.lastIndexOf('/') + 1, name.length());
                content = mContext.getResources().openRawResource(t[i].getInt(a));
                content.reset();

                // python project
                if (sFileName.endsWith(GlobalConstants.PYTHON_PROJECT_ZIP_NAME)) {
                    succeed &= Utils.unzip(content, mContext.getFilesDir().getAbsolutePath() + "/", true);
                }
                // python -> /data/data/com.android.python27/files/python
                else if (sFileName.endsWith(GlobalConstants.PYTHON_ZIP_NAME)) {
                    succeed &= Utils.unzip(content, mContext.getFilesDir().getAbsolutePath() + "/", true);
                    FileUtils.chmod(new File(mContext.getFilesDir().getAbsolutePath() + "/python/bin/python"), 0755);
                }
                // python extras -> /sdcard/com.android.python27/extras/python
                else if (sFileName.endsWith(GlobalConstants.PYTHON_EXTRAS_ZIP_NAME)) {
                    Utils.createDirectoryOnExternalStorage(mContext.getPackageName() + "/" + "extras");
                    Utils.createDirectoryOnExternalStorage(mContext.getPackageName() + "/" + "extras" + "/" + "tmp");
                    succeed &= Utils.unzip(content, Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + mContext.getPackageName() + "/extras/", true);
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to copyResourcesToLocal", e);
                succeed = false;
            }
        } // end for all files in res/raw

    }

    private void createPythonStorageDir() {

    }

    private void createOurExternalStorageRootDir() {
        Utils.createDirectoryOnExternalStorage(mContext.getPackageName());
    }

}