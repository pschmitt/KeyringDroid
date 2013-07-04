package co.schmitt.android.keyringdroid;

import android.accounts.Account;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by pschmitt on 7/4/13.
 */
public class AccountAdapter extends ArrayAdapter<Account> {
    private ArrayList<Account> data = null;
    private int imageViewRessourceId;

    public AccountAdapter(Context context, int resource, int textViewResourceId, int imageViewRessourceId, Account[] objects) {
        super(context, resource, textViewResourceId, objects);
        data = new ArrayList<Account>(Arrays.asList(objects));
        this.imageViewRessourceId = imageViewRessourceId;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // assign the view we are converting to a local variable
        View v = convertView;

        // first check to see if the view is null. if so, we have to inflate it.
        // to inflate it basically means to render, or show, the view.
        if (v == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.account_spinner_item, null);
        }

		/*
         * Recall that the variable position is sent in as an argument to this method.
		 * The variable simply refers to the position of the current object in the list. (The ArrayAdapter
		 * iterates through the list we sent it)
		 *
		 * Therefore, i refers to the current Item object.
		 */
        Account account = data.get(position);

        if (account != null) {
            // This is how you obtain a reference to the TextViews.
            // These TextViews are created in the XML files we defined.

            TextView accountNameTextView = (TextView) v.findViewById(R.id.account_name);

            // check to see if each individual textview is null.
            // if not, assign some text!
            if (accountNameTextView != null) {
                accountNameTextView.setText(account.name);
            }
        }
        // the view must be returned to our activity
        return v;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        // This view starts when we click the spinner.
        View row = convertView;
        if (row == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            row = inflater.inflate(R.layout.account_spinner_item, parent, false);
        }

        Account account = data.get(position);

        if (account != null) {
            // Parse the data from each object and set it.
            ImageView accountPicture = (ImageView) row.findViewById(R.id.account_picture);
            TextView accountNameTextView = (TextView) row.findViewById(R.id.account_name);
            if (accountPicture != null) {
                // TODO retrieve the user's profile picture
//                accountPicture.setBackgroundDrawable(//getResources().getDrawable(account.));
            }
            if (accountNameTextView != null) {
                accountNameTextView.setText(account.name);
            }
        }

        return row;
    }
}
