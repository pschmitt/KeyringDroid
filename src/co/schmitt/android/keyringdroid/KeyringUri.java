package co.schmitt.android.keyringdroid;

import android.net.Uri;

/**
 * Created by pschmitt on 7/8/13.
 */
public class KeyringUri {

    private KeyringUri() {}

    /**
     * Retrieve the URI of Keyrings for a given account
     *
     * @param accountName The owner's account name
     * @return The URI of Keyrings
     */
    public static Uri getKeyringsUri(String accountName) {
        return Uri.parse("content://co.schmitt.android.provider.KeyringDroid/" + accountName + "/keyrings/");
    }

    /**
     * Retrieve the URI of KeyringVault for a given account
     *
     * @param accountName The owner's account name
     * @param keyringId   The ID of the keyring item
     * @return The URI of the KeyringVault
     */
    public static Uri getKeyringUri(String accountName, String keyringId) {
        return Uri.parse("content://co.schmitt.android.provider.KeyringDroid/" + accountName + "/keyring/" + keyringId);
    }

    /**
     * Retrieve the URI of a (local ?) file
     *
     * @param accountName The owner's account name
     * @param fileId      The ID of the file
     * @return The URI of the file
     */
    public static Uri getFileUri(String accountName, String fileId) {
        return Uri.parse("content://co.schmitt.android.provider.KeyringDroid/" + accountName + "/files/" + fileId);
    }
}
