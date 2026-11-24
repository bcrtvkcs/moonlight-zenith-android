package com.limelight.utils;

import java.util.ArrayList;

import android.app.Activity;
import android.content.DialogInterface;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.limelight.R;
import com.limelight.ui.glass.GlassDialogHandle;
import com.limelight.ui.glass.GlassDialogPortal;

public class Dialog implements Runnable {
    private final String title;
    private final String message;
    private final Activity activity;
    private final Runnable runOnDismiss;

    private AlertDialog alert;
    private GlassDialogHandle glassHandle;

    private static final ArrayList<Dialog> rundownDialogs = new ArrayList<>();

    private Dialog(Activity activity, String title, String message, Runnable runOnDismiss)
    {
        this.activity = activity;
        this.title = title;
        this.message = message;
        this.runOnDismiss = runOnDismiss;
    }

    public static void closeDialogs()
    {
        synchronized (rundownDialogs) {
            for (Dialog d : rundownDialogs) {
                if (d.alert != null && d.alert.isShowing()) {
                    d.alert.dismiss();
                }
                if (d.glassHandle != null) {
                    d.glassHandle.dismiss();
                    d.glassHandle = null;
                }
            }

            rundownDialogs.clear();
        }
    }

    public static void displayDialog(final Activity activity, String title, String message, final boolean endAfterDismiss)
    {
        activity.runOnUiThread(new Dialog(activity, title, message, new Runnable() {
            @Override
            public void run() {
                if (endAfterDismiss) {
                    activity.finish();
                }
            }
        }));
    }

    public static void displayDialog(Activity activity, String title, String message, Runnable runOnDismiss)
    {
        activity.runOnUiThread(new Dialog(activity, title, message, runOnDismiss));
    }

    @Override
    public void run() {
        // If we're dying, don't bother creating a dialog
        if (activity.isFinishing())
            return;

        if (UiHelper.isLiquidGlassEnabled(activity)) {
            synchronized (rundownDialogs) {
                rundownDialogs.add(this);
                glassHandle = GlassDialogPortal.showAlert(
                        activity,
                        title,
                        message,
                        activity.getResources().getString(android.R.string.ok),
                        activity.getResources().getString(R.string.help),
                        new Runnable() {
                            @Override
                            public void run() {
                                synchronized (rundownDialogs) {
                                    rundownDialogs.remove(Dialog.this);
                                    glassHandle = null;
                                }
                                runOnDismiss.run();
                            }
                        },
                        new Runnable() {
                            @Override
                            public void run() {
                                synchronized (rundownDialogs) {
                                    rundownDialogs.remove(Dialog.this);
                                    glassHandle = null;
                                }
                                runOnDismiss.run();
                                HelpLauncher.launchTroubleshooting(activity);
                            }
                        }
                );
            }
            return;
        }

        alert = new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        synchronized (rundownDialogs) {
                            rundownDialogs.remove(Dialog.this);
                            alert.dismiss();
                        }

                        runOnDismiss.run();
                    }
                })
                .setNeutralButton(R.string.help, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        synchronized (rundownDialogs) {
                            rundownDialogs.remove(Dialog.this);
                            alert.dismiss();
                        }

                        runOnDismiss.run();

                        HelpLauncher.launchTroubleshooting(activity);
                    }
                })
                .create();

        alert.setCanceledOnTouchOutside(false);
        alert.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                // Set focus to the OK button by default
                Button button = alert.getButton(AlertDialog.BUTTON_POSITIVE);
                if (button != null) {
                    button.setFocusable(true);
                    button.setFocusableInTouchMode(true);
                    button.requestFocus();
                }
            }
        });

        synchronized (rundownDialogs) {
            rundownDialogs.add(this);
            UiHelper.applyLiquidGlassDialogStyle(activity, alert);
            alert.show();
        }
    }
}
