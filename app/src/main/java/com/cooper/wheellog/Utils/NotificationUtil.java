package com.cooper.wheellog.Utils;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.support.v7.app.NotificationCompat;
import android.widget.RemoteViews;

import com.cooper.wheellog.BluetoothLeService;
import com.cooper.wheellog.LoggingService;
import com.cooper.wheellog.MainActivity;
import com.cooper.wheellog.PebbleService;
import com.cooper.wheellog.R;
import com.cooper.wheellog.WheelLog;

import java.util.Locale;

public class NotificationUtil {

    private Context mContext;
    private int mConnectionState = BluetoothLeService.STATE_DISCONNECTED;
    private int notificationMessageId = R.string.wheel_disconnected;
    private int mBatteryLevel = 0;

    public NotificationUtil(Context context) {
        mContext = context;
        context.registerReceiver(messageReceiver, makeIntentFilter());
    }

    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Constants.ACTION_BLUETOOTH_CONNECTED.equals(action)) {
                mConnectionState = BluetoothLeService.STATE_CONNECTED;
                notificationMessageId = R.string.wheel_connected;
                updateNotification();
            } else if (Constants.ACTION_BLUETOOTH_DISCONNECTED.equals(action)) {
                mConnectionState = BluetoothLeService.STATE_DISCONNECTED;
                notificationMessageId = R.string.wheel_disconnected;
                updateNotification();
            } else if (Constants.ACTION_BLUETOOTH_CONNECTING.equals(action)) {
                mConnectionState = BluetoothLeService.STATE_CONNECTING;
                if (intent.hasExtra(Constants.INTENT_EXTRA_BLE_AUTO_CONNECT))
                    notificationMessageId = R.string.wheel_searching;
                else
                    notificationMessageId = R.string.wheel_connecting;
                updateNotification();
            } else if (Constants.ACTION_WHEEL_DATA_AVAILABLE.equals(action)) {
                int batteryLevel = ((WheelLog) mContext.getApplicationContext()).getBatteryLevel();
                if (mBatteryLevel != batteryLevel) {
                    mBatteryLevel = batteryLevel;
                    updateNotification();
                }
            } else if (Constants.ACTION_PEBBLE_SERVICE_TOGGLED.equals(action) ||
                    Constants.ACTION_LOGGING_SERVICE_TOGGLED.equals(action)) {
                updateNotification();
            }
        }
    };

    public Notification buildNotification() {
        Intent notificationIntent = new Intent(mContext, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, notificationIntent, 0);


        RemoteViews notificationView = new RemoteViews(mContext.getPackageName(), R.layout.notification);
        notificationView.setTextViewText(R.id.text_title, mContext.getString(R.string.app_name));

        PendingIntent pendingConnectionIntent = PendingIntent.getBroadcast(mContext, 0,
                new Intent(Constants.NOTIFICATION_BUTTON_CONNECTION), 0);
        PendingIntent pendingWatchIntent = PendingIntent.getBroadcast(mContext, 0,
                new Intent(Constants.NOTIFICATION_BUTTON_WATCH), 0);
        PendingIntent pendingLoggingIntent = PendingIntent.getBroadcast(mContext, 0,
                new Intent(Constants.NOTIFICATION_BUTTON_LOGGING), 0);

        notificationView.setOnClickPendingIntent(R.id.ib_connection,
                pendingConnectionIntent);
        notificationView.setOnClickPendingIntent(R.id.ib_watch,
                pendingWatchIntent);
        notificationView.setOnClickPendingIntent(R.id.ib_logging,
                pendingLoggingIntent);

        switch (mConnectionState) {
            case BluetoothLeService.STATE_CONNECTING:
                notificationView.setImageViewResource(R.id.ib_connection, R.drawable.ic_action_wheel_light_orange);
                break;
            case BluetoothLeService.STATE_CONNECTED:
                notificationView.setImageViewResource(R.id.ib_connection, R.drawable.ic_action_wheel_orange);
                break;
            case BluetoothLeService.STATE_DISCONNECTED:
                notificationView.setImageViewResource(R.id.ib_connection, R.drawable.ic_action_wheel_grey);
                break;
        }

        String message;

        if (mConnectionState == BluetoothLeService.STATE_CONNECTED)
            message = String.format(Locale.US, "%s - %d%%", mContext.getString(notificationMessageId), mBatteryLevel);
        else
            message = mContext.getString(notificationMessageId);

        notificationView.setTextViewText(R.id.text_message, message);

        if (PebbleService.isInstanceCreated())
            notificationView.setImageViewResource(R.id.ib_watch, R.drawable.ic_action_watch_orange);
        else
            notificationView.setImageViewResource(R.id.ib_watch, R.drawable.ic_action_watch_grey);

        if (LoggingService.isInstanceCreated())
            notificationView.setImageViewResource(R.id.ib_logging, R.drawable.ic_action_logging_orange);
        else
            notificationView.setImageViewResource(R.id.ib_logging, R.drawable.ic_action_logging_grey);



        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return new Notification.Builder(mContext)
                    .setSmallIcon(R.drawable.ic_stat_wheel)
                    .setContentIntent(pendingIntent)
                    .setCustomContentView(notificationView)
                    .setStyle(new Notification.DecoratedCustomViewStyle())
                    .build();
        } else {
            return new NotificationCompat.Builder(mContext)
                    .setSmallIcon(R.drawable.ic_stat_wheel)
                    .setContentIntent(pendingIntent)
                    .setTicker("")
                    .setContent(notificationView)
                    .setCustomBigContentView(null)
                    .build();
        }
    }

    private void updateNotification() {
        Notification notification = buildNotification();
        android.app.NotificationManager mNotificationManager = (android.app.NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(Constants.NOTIFICATION_ID, notification);
    }

    public void unregisterReceiver() {
        mContext.unregisterReceiver(messageReceiver);
    }

    private IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.ACTION_BLUETOOTH_CONNECTING);
        intentFilter.addAction(Constants.ACTION_BLUETOOTH_CONNECTED);
        intentFilter.addAction(Constants.ACTION_BLUETOOTH_DISCONNECTED);
        intentFilter.addAction(Constants.ACTION_WHEEL_DATA_AVAILABLE);
        intentFilter.addAction(Constants.ACTION_LOGGING_SERVICE_TOGGLED);
        intentFilter.addAction(Constants.ACTION_PEBBLE_SERVICE_TOGGLED);
        return intentFilter;
    }
}
