package co.schmitt.android.keyringdroid.python;

import android.content.Context;
import com.android.python27.config.GlobalConstants;

import java.io.File;

/**
 * Created by pschmitt on 7/8/13.
 */
public class PythonInstaller {
    /**
     * Quick and dirty: only test a file
     *
     * @param context The context this object is running in
     * @return true if we need to extract the python archive
     */
    public static boolean isInstallNeeded(Context context) {
        File testedFile = new File(context.getFilesDir().getAbsolutePath() + "/" + GlobalConstants.PYTHON_MAIN_SCRIPT_NAME);
        return !testedFile.exists();
    }
}
