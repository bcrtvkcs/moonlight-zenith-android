package com.limelight.utils;

import java.util.ArrayList;
import java.util.Iterator;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.limelight.R;

public class SpinnerDialog implements Runnable, OnCancelListener {
    private final String title;
    private String message;
    private final Activity activity;
    private AlertDialog progressDialog;
    private TextView messageTextView;
    private final boolean finish;

    private static final ArrayList<SpinnerDialog> rundownDialogs = new ArrayList<>();

    private SpinnerDialog(Activity activity, String title, String message, boolean finish)
    {
        this.activity = activity;
        this.title = title;
        this.message = message;
        this.progressDialog = null;
        this.finish = finish;
    }

    public static SpinnerDialog displayDialog(Activity activity, String title, String message, boolean finish)
    {
        SpinnerDialog spinner = new SpinnerDialog(activity, title, message, finish);
        activity.runOnUiThread(spinner);
        return spinner;
    }

    public static void closeDialogs(Activity activity)
    {
        synchronized (rundownDialogs) {
            Iterator<SpinnerDialog> i = rundownDialogs.iterator();
            while (i.hasNext()) {
                SpinnerDialog dialog = i.next();
                if (dialog.activity == activity) {
                    i.remove();
                    if (dialog.progressDialog != null && dialog.progressDialog.isShowing()) {
                        dialog.progressDialog.dismiss();
                    }
                }
            }
        }
    }

    public void dismiss()
    {
        // Running again with progressDialog != null will destroy it
        activity.runOnUiThread(this);
    }

    public void setMessage(final String message)
    {
        this.message = message;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (messageTextView != null) {
                    messageTextView.setText(message);
                }
            }
        });
    }

    @Override
    public void run() {
        // If we're dying, don't bother doing anything
        if (activity.isFinishing()) {
            return;
        }

        if (progressDialog == null)
        {
            View contentView = LayoutInflater.from(activity).inflate(R.layout.m3_dialog_spinner, null, false);
            messageTextView = contentView.findViewById(R.id.m3_spinner_message);
            if (messageTextView != null) {
                messageTextView.setText(message);
            }

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                    .setTitle(title)
                    .setView(contentView)
                    .setOnCancelListener(this);

            if (finish) {
                builder.setCancelable(true);
            } else {
                builder.setCancelable(false);
            }

            progressDialog = builder.create();
            progressDialog.setCanceledOnTouchOutside(false);

            synchronized (rundownDialogs) {
                rundownDialogs.add(this);
                progressDialog.show();
            }
        }
        else
        {
            synchronized (rundownDialogs) {
                if (rundownDialogs.remove(this) && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            }
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        synchronized (rundownDialogs) {
            rundownDialogs.remove(this);
        }

        // This will only be called if finish was true, so we don't need to check again
        activity.finish();
    }
}
