package com.chemfizlab.scioapp.activities;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;

import com.consumerphysics.android.sdk.callback.device.ScioDeviceCallback;
import com.consumerphysics.android.sdk.callback.device.ScioDeviceConnectHandler;
import com.consumerphysics.android.sdk.sciosdk.ScioCloud;
import com.consumerphysics.android.sdk.sciosdk.ScioDevice;

import com.chemfizlab.scioapp.config.Constants;
import com.chemfizlab.scioapp.interfaces.IScioDevice;
import com.chemfizlab.scioapp.utils.StringUtils;

/**
 * Created by nadavg on 19/07/2016.
 */
public class BaseScioActivity extends Activity implements IScioDevice {

    private ScioCloud scioCloud;
    private ScioDevice scioDevice;

    protected ScioCloud getScioCloud() {
        return scioCloud;
    }

    protected ScioDevice getScioDevice() {
        return scioDevice;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        scioCloud = new ScioCloud(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        final String deviceAddress = getSharedPreferences().getString(Constants.SCIO_ADDRESS, null);
        if (!StringUtils.isEmpty(deviceAddress)) {
            connect(deviceAddress);
        }
    }

    @Override
    protected void onStop() {
        // Make sure scio device is disconnected or leaks may occur
        if (scioDevice != null) {
            scioDevice.disconnect();
            scioDevice = null;
        }

        super.onStop();
    }

    protected boolean isDeviceConnected() {
        return scioDevice != null && scioDevice.isConnected();
    }

    protected boolean isLoggedIn() {
        return scioCloud != null && scioCloud.hasAccessToken();
    }

    protected SharedPreferences getSharedPreferences() {
        return getSharedPreferences(Constants.PREF_FILE, Context.MODE_PRIVATE);
    }

    protected void connect(final String deviceAddress) {
        scioDevice = new ScioDevice(this, deviceAddress);
        scioDevice.connect(new ScioDeviceConnectHandler() {
            @Override
            public void onConnected() {
                onScioConnected();
            }

            @Override
            public void onConnectFailed() {
                onScioConnectionFailed();
            }

            @Override
            public void onTimeout() {
                onScioConnectionFailed();
            }
        });

        scioDevice.setScioDisconnectCallback(new ScioDeviceCallback() {
            @Override
            public void execute() {
                onScioDisconnected();
            }
        });

        scioDevice.setButtonPressedCallback(new ScioDeviceCallback() {
            @Override
            public void execute() {
                onScioButtonClicked();
            }
        });
    }

    @Override
    public void onScioButtonClicked() {
    }

    @Override
    public void onScioConnected() {
    }

    @Override
    public void onScioConnectionFailed() {
    }

    @Override
    public void onScioDisconnected() {
    }
}
