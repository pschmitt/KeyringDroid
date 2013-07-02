package co.schmitt.android.keyringdroid;

/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Defines a contract between the Note Pad content provider and its clients. A
 * contract defines the information that a client needs to access the provider
 * as one or more data tables. A contract is a public, non-extendable (final)
 * class that contains constants defining column names and URIs. A well-written
 * client depends only on the constants in the contract.
 */
public final class Keyring {
    public static final String AUTHORITY = "co.schmitt.android.provider.KeyringDroid";

    // This class cannot be instantiated
    private Keyring() {
    }

    /**
     * Keyrings table contract
     */
    public static final class Keyrings implements BaseColumns {

        // This class cannot be instantiated
        private Keyrings() {
        }

        /**
         * The table name offered by this provider
         */
        public static final String TABLE_NAME = "keyrings";

    /*
     * URI definitions
     */

        /**
         * The scheme part for this provider's URI
         */
        private static final String SCHEME = "content://";

        /**
         * Path parts for the URIs
         */

        /**
         * Path part for the Keyrings URI
         */
        private static final String PATH_KEYRINGS = "/keyrings";

        /**
         * Path part for the Note ID URI
         */
        private static final String PATH_KEYRING_ID = "/keyrings/";

        /**
         * 0-relative position of a keyring ID segment in the path part of a keyring ID
         * URI
         */
        public static final int KEYRING_ID_PATH_POSITION = 1;

        /**
         * 0-relative position of a keyring account segment in the path part of a keyring
         * ID URI
         */
        public static final int KEYRING_ACCOUNT_PATH_POSITION = 0;

        /**
         * 0-relative position of a keyring file ID segment in the path part of a keyring
         * ID URI
         */
        public static final int NOTE_FILE_ID_PATH_POSITION = 2;

        /**
         * Path part for the Live Folder URI
         */
        private static final String PATH_LIVE_FOLDER = "/live_folders/keyrings";

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI = Uri.parse(SCHEME + AUTHORITY + PATH_KEYRINGS);

        /**
         * The content URI base for a single keyring. Callers must append a numeric
         * keyring id to this Uri to retrieve a keyring
         */
        public static final Uri CONTENT_ID_URI_BASE = Uri.parse(SCHEME + AUTHORITY + PATH_KEYRING_ID);

        /**
         * The content URI match pattern for a single keyring, specified by its ID. Use
         * this to match incoming URIs or to construct an Intent.
         */
        public static final Uri CONTENT_ID_URI_PATTERN = Uri.parse(SCHEME + AUTHORITY + PATH_KEYRING_ID
                + "/#");

        /**
         * The content Uri pattern for a keyrings listing for live folders
         */
        public static final Uri LIVE_FOLDER_URI = Uri.parse(SCHEME + AUTHORITY + PATH_LIVE_FOLDER);

    /*
     * MIME type definitions
     */

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of keyrings.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.keyring";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single keyring.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.keyring";

        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER = "modified DESC";

    /*
     * Column definitions
     */

        /**
         * Column name for the title of the keyring
         * <p/>
         * Type: TEXT
         * </P>
         */
        public static final String COLUMN_NAME_TITLE = "title";

        /**
         * Column name of the keyring content
         * <p/>
         * Type: TEXT
         * </P>
         */
        public static final String COLUMN_NAME_FILENAME = "keyring";

        /**
         * Column name for the creation timestamp
         * <p/>
         * Type: INTEGER (long from System.curentTimeMillis())
         * </P>
         */
        public static final String COLUMN_NAME_CREATE_DATE = "created";

        /**
         * Column name for the modification timestamp
         * <p/>
         * Type: INTEGER (long from System.curentTimeMillis())
         * </P>
         */
        public static final String COLUMN_NAME_MODIFICATION_DATE = "modified";

        /**
         * Column name for the Drive File ID
         * <p/>
         * Type: TEXT
         * </P>
         */
        public static final String COLUMN_NAME_FILE_ID = "fileId";

        /**
         * Column name for the account
         * <p/>
         * Type: TEXT
         * </P>
         */
        public static final String COLUMN_NAME_ACCOUNT = "account";

        /**
         * Column name for the trashed attribute
         * <p/>
         * Type: BOOL
         * </P>
         */
        public static final String COLUMN_NAME_DELETED = "deleted";
    }
}