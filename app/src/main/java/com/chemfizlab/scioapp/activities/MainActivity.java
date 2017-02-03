package com.chemfizlab.scioapp.activities;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.consumerphysics.android.sdk.callback.cloud.ScioCloudAnalyzeManyCallback;
import com.consumerphysics.android.sdk.callback.cloud.ScioCloudSCiOVersionCallback;
import com.consumerphysics.android.sdk.callback.cloud.ScioCloudUserCallback;
import com.consumerphysics.android.sdk.callback.device.ScioDeviceBatteryHandler;
import com.consumerphysics.android.sdk.callback.device.ScioDeviceCalibrateHandler;
import com.consumerphysics.android.sdk.callback.device.ScioDeviceCallbackHandler;
import com.consumerphysics.android.sdk.callback.device.ScioDeviceScanHandler;
import com.consumerphysics.android.sdk.model.ScioBattery;
import com.consumerphysics.android.sdk.model.ScioModel;
import com.consumerphysics.android.sdk.model.ScioReading;
import com.consumerphysics.android.sdk.model.ScioUser;
import com.consumerphysics.android.sdk.sciosdk.ScioLoginActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.chemfizlab.scioapp.R;
import com.chemfizlab.scioapp.adapter.ScioModelAdapter;
import com.chemfizlab.scioapp.config.Constants;
import com.chemfizlab.scioapp.utils.StringUtils;

public final class MainActivity extends BaseScioActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private final static int LOGIN_ACTIVITY_RESULT = 1000;

    // TODO: Put your redirect url here!
    private static final String REDIRECT_URL = "https://www.consumerphysics.com";

    // TODO: Put your app key here!
    private static final String APPLICATION_KEY = "a25e3dc8-224d-4cf8-a6e8-206a103e0923";

    // UI
    private TextView nameTextView;
    private TextView addressTextView;
    private TextView statusTextView;
    private TextView usernameTextView;
    private TextView modelTextView;
    private TextView version;
    private ProgressDialog progressDialog;

    // Members
    private String deviceName;
    private String deviceAddress;
    private String username;
    private String modelId;
    private String modelName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        initUI();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (getScioCloud().hasAccessToken() && username == null) {
            getScioUser();
        }

        updateDisplay();
    }

    @Override
    protected void onStop() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
            progressDialog = null;
        }

        super.onStop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case LOGIN_ACTIVITY_RESULT:
                if (resultCode == RESULT_OK) {
                    Log.d(TAG, "Zalogowano.");
                }
                else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            final String description = data.getStringExtra(ScioLoginActivity.ERROR_DESCRIPTION);
                            final int errorCode = data.getIntExtra(ScioLoginActivity.ERROR_CODE, -1);

                            Toast.makeText(MainActivity.this, "Wystąpił bład.\nNumer: " + errorCode + "\nOpis: " + description, Toast.LENGTH_LONG).show();
                        }
                    });
                }

                break;
        }
    }

    @Override
    public void onScioButtonClicked() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateDisplay();
                Toast.makeText(getApplicationContext(), "Trwa pomiar...", Toast.LENGTH_SHORT).show();

                doScan(null);
            }
        });
    }

    @Override
    public void onScioConnectionFailed() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateDisplay();
                Toast.makeText(getApplicationContext(), "Połączenie ze SCiO nieudane", Toast.LENGTH_SHORT).show();

                dismissingProgress();
            }
        });
    }

    private void dismissingProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    @Override
    public void onScioConnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateDisplay();
                Log.d(TAG, "Połączono ze SCiO");
                dismissingProgress();
            }
        });
    }

    @Override
    public void onScioDisconnected() {
        super.onScioDisconnected();

        Log.d(TAG, "Rozłączono ze SCiO");

        updateDisplay();

        dismissingProgress();
    }

    // Button Actions
    public void doLogout(final View view) {
        if (getScioCloud() != null) {
            getScioCloud().deleteAccessToken();

            storeUsername(null);
            updateDisplay();
        }
    }

    public void doLogin(final View view) {
        if (!isLoggedIn()) {
            final Intent intent = new Intent(this, ScioLoginActivity.class);
            intent.putExtra(ScioLoginActivity.INTENT_REDIRECT_URI, REDIRECT_URL);
            intent.putExtra(ScioLoginActivity.INTENT_APPLICATION_ID, APPLICATION_KEY);

            startActivityForResult(intent, LOGIN_ACTIVITY_RESULT);
        }
        else {
            Log.d(TAG, "Posiadasz już Token");

            getScioUser();
        }
    }

    public void startCachedScanning(View view) {
        if (!isDeviceConnected() || !isLoggedIn()) {
            Toast.makeText(getApplicationContext(), "Połącz się ze SCiO i zaloguj.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (StringUtils.isEmpty(modelId)) {
            Toast.makeText(getApplicationContext(), "Wybierz model", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, MultiScanningActivity.class);
        ArrayList<String> models = new ArrayList<>(Arrays.asList(modelId.split(",")));
        intent.putStringArrayListExtra("models", models);
        startActivity(intent);
    }

    public void doDiscover(View view) {
        Intent intent = new Intent(this, DiscoverActivity.class);
        startActivity(intent);
    }

    public void getScioVersion(final View view) {
        if (!isDeviceConnected()) {
            Toast.makeText(getApplicationContext(), "Urządzenie niepodłączone. Nieprawidłowe ID", Toast.LENGTH_SHORT).show();

            return;
        }

        progressDialog = ProgressDialog.show(this, "Proszę czekać", "Pobieram wersję SCiO...", false);

        getScioCloud().getScioVersion(getScioDevice().getId(), new ScioCloudSCiOVersionCallback() {
            @Override
            public void onSuccess(final String scioVersion) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Wersja SCiO")
                                .setMessage(scioVersion)
                                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                    }
                                })
                                .setIcon(android.R.drawable.ic_dialog_info)
                                .show();

                        dismissingProgress();
                    }
                });
            }

            @Override
            public void onError(int i, String s) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Błąd przy pobieraniu wersji SCiO", Toast.LENGTH_SHORT).show();
                        dismissingProgress();
                    }
                });
            }
        });
    }

    public void doModels(final View view) {
        if (!isLoggedIn()) {
            Toast.makeText(getApplicationContext(), "Nie można pobrać kolekcji. Zaloguj się...", Toast.LENGTH_SHORT).show();
            return;
        }

        final Intent intent = new Intent(this, ModelActivity.class);
        startActivity(intent);
    }

    public void doCPModels(final View view) {
        if (!isLoggedIn()) {
            Toast.makeText(getApplicationContext(), "Nie można pobrać modeli Consumer Physics. Zaloguj się...", Toast.LENGTH_SHORT).show();
            return;
        }

        final Intent intent = new Intent(this, CPModelActivity.class);
        startActivity(intent);
    }

    public void doConnect(final View view) {
        if (deviceAddress == null) {
            Toast.makeText(getApplicationContext(), "Wybierz urządzenie SCiO", Toast.LENGTH_SHORT).show();
            return;
        }

        if (getScioDevice() != null) {
            if (isDeviceConnected()) {
                Toast.makeText(getApplicationContext(), "Jesteś już połączony ze SCiO", Toast.LENGTH_SHORT).show();
            }
            else {
                Toast.makeText(getApplicationContext(), "Autołączenie, proszę czekać...", Toast.LENGTH_SHORT).show();
            }

            return;
        }

        progressDialog = ProgressDialog.show(this, "Proszę czekać", "Łączenie...", false);

        connect(deviceAddress);
    }

    public void doDisconnect(final View view) {
        if (!isDeviceConnected()) {
            Toast.makeText(getApplicationContext(), "SCiO niepodłączone", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog = ProgressDialog.show(this, "Proszę czekać", "Rozłączanie...", false);

        getScioDevice().disconnect();
    }

    public void doCalibrate(final View view) {
        if (!isDeviceConnected()) {
            Toast.makeText(getApplicationContext(), "Nie można skalibrować. SCiO niepodłączone", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog = ProgressDialog.show(this, "Proszę czekać", "Kalibracja...", false);

        getScioDevice().calibrate(new ScioDeviceCalibrateHandler() {
            @Override
            public void onSuccess() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "SCiO skalibrowane poprawnie", Toast.LENGTH_SHORT).show();
                        dismissingProgress();
                    }
                });
            }

            @Override
            public void onError() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Błąd podczas kalibracji", Toast.LENGTH_SHORT).show();
                        dismissingProgress();
                    }
                });
            }

            @Override
            public void onTimeout() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Błąd, upłynął czas kalibracji", Toast.LENGTH_SHORT).show();
                        dismissingProgress();
                    }
                });
            }
        });
    }

    public void readBattery(final View view) {
        if (!isDeviceConnected()) {
            Toast.makeText(getApplicationContext(), "Nie można pobrać stanu baterii. SCiO niepodłączone", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog = ProgressDialog.show(this, "Proszę czekać", "Pobieranie stanu baterii...", false);

        getScioDevice().readBattery(new ScioDeviceBatteryHandler() {
            @Override
            public void onSuccess(final ScioBattery scioBattery) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Procent naładowania: " + scioBattery.getChargePercentage() + ", Trwa ładowanie? " + scioBattery.isCharging(), Toast.LENGTH_SHORT).show();
                        dismissingProgress();
                    }
                });
            }

            @Override
            public void onError() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Błąd podczas pobierania stanu baterii", Toast.LENGTH_SHORT).show();
                        dismissingProgress();
                    }
                });
            }

            @Override
            public void onTimeout() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Upłynął czas pobierania stanu baterii", Toast.LENGTH_SHORT).show();
                        dismissingProgress();
                    }
                });
            }
        });
    }

    public void checkCalibration(final View view) {
        if (!isDeviceConnected()) {
            Toast.makeText(getApplicationContext(), "Nie można sprawdzić statusu kalibracji. SCiO niepodłączone", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(getApplicationContext(), "SCiO potrzebuje kalibracji? " + getScioDevice().isCalibrationNeeded(), Toast.LENGTH_SHORT).show();
    }

    public void doRename(final View view) {
        if (!isDeviceConnected()) {
            Toast.makeText(getApplicationContext(), "Nie można zmienić nazwy. SCiO niepodłączone", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
        alertDialog.setTitle("Zmienia nazwę SCiO");
        alertDialog.setMessage("Podaj nazwę");

        final EditText input = new EditText(MainActivity.this);
        input.setHint(nameTextView.getText().toString());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        input.setLayoutParams(lp);
        alertDialog.setView(input);

        alertDialog.setPositiveButton("Zmień", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                getScioDevice().renameDevice(input.getText().toString(), new ScioDeviceCallbackHandler() {
                    @Override
                    public void onSuccess() {
                        storeDeviceName(input);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Nazwa zmieniona.", Toast.LENGTH_SHORT).show();
                                nameTextView.setText(input.getText().toString());
                            }
                        });
                    }

                    @Override
                    public void onError() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Niepowodzenia.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onTimeout() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Niepowodzenie, czas minął.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }
        });

        alertDialog.setNegativeButton("Anuluj", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        alertDialog.show();
    }

    private void storeDeviceName(final EditText input) {
        getSharedPreferences().edit().putString(Constants.SCIO_NAME, input.getText().toString()).commit();
    }

    public void doScan(final View view) {

        if (!isDeviceConnected()) {
            Toast.makeText(getApplicationContext(), "Nie można skanować. SCiO niepodłączone", Toast.LENGTH_SHORT).show();
            return;
        }

        if (modelId == null) {
            Toast.makeText(getApplicationContext(), "Nie można skanować. Model nie wybrany.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isLoggedIn()) {
            Toast.makeText(getApplicationContext(), "Nie można skanować. Zaloguj się.", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog = ProgressDialog.show(this, "Proszę czekać", "Analizuję...", false);

        getScioDevice().scan(new ScioDeviceScanHandler() {
            @Override
            public void onSuccess(final ScioReading reading) {
                // ScioReading object is Serializable and can be saved to be used later for analyzing.
                List<String> modelsToAnalyze = new ArrayList<>();
                modelsToAnalyze.addAll(Arrays.asList(modelId.split(",")));

                getScioCloud().analyze(reading, modelsToAnalyze, new ScioCloudAnalyzeManyCallback() {
                    @Override
                    public void onSuccess(final List<ScioModel> models) {
                        Log.d(TAG, "analyze onSuccess");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showAnalyzeResults(models);
                                dismissingProgress();
                            }
                        });
                    }

                    @Override
                    public void onError(final int code, final String msg) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), "Błąd podczas analizowania: " + msg, Toast.LENGTH_SHORT).show();
                                dismissingProgress();
                            }
                        });

                    }
                });
            }

            @Override
            public void onNeedCalibrate() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Nie można skanować. Potrzebna kalibracja", Toast.LENGTH_SHORT).show();
                        dismissingProgress();
                    }
                });
            }

            @Override
            public void onError() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Błąd podczas skanowania", Toast.LENGTH_SHORT).show();
                        dismissingProgress();
                    }
                });

            }

            @Override
            public void onTimeout() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Niepowodzenie, czas minął", Toast.LENGTH_SHORT).show();
                        dismissingProgress();
                    }
                });
            }
        });
    }

    // Private
    private void getScioUser() {
        progressDialog = ProgressDialog.show(this, "Proszę czekać", "Pobieram dane...", true);

        getScioCloud().getScioUser(new ScioCloudUserCallback() {
            @Override
            public void onSuccess(final ScioUser user) {
                storeUsername(user.getUsername());

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Witaj " + user.getFirstName() + " " + user.getLastName(), Toast.LENGTH_SHORT).show();
                        updateDisplay();
                        dismissingProgress();
                    }
                });
            }

            @Override
            public void onError(final int code, final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Błąd podczas pobierania danych użytkownika.", Toast.LENGTH_SHORT).show();
                        dismissingProgress();
                    }
                });
            }
        });
    }

    private void showAnalyzeResults(final List<ScioModel> models) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("Wyniki");

        final LayoutInflater inflater = getLayoutInflater();
        final View convertView = inflater.inflate(R.layout.results_view, null);
        builder.setView(convertView);

        final ArrayList<ScioModel> arrayOfModels = new ArrayList<>();
        final ScioModelAdapter scioModelAdapter = new ScioModelAdapter(this, arrayOfModels);

        final ListView listView = (ListView) convertView.findViewById(R.id.results);
        listView.setAdapter(scioModelAdapter);

        scioModelAdapter.addAll(models);

        builder.setPositiveButton("OK", null);
        builder.setCancelable(true);

        builder.create().show();
    }

    private void initUI() {
        nameTextView = (TextView) findViewById(R.id.tv_scio_name);
        addressTextView = (TextView) findViewById(R.id.tv_scio_address);
        statusTextView = (TextView) findViewById(R.id.tv_scio_status);
        usernameTextView = (TextView) findViewById(R.id.tv_username);
        modelTextView = (TextView) findViewById(R.id.tv_model);
        version = (TextView) findViewById(R.id.version);

        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            version.setText("v" + pInfo.versionName);
        }
        catch (PackageManager.NameNotFoundException e) {
        }
    }

    private void storeUsername(final String username) {
        this.username = username;
        getSharedPreferences().edit().putString(Constants.USER_NAME, username).commit();
    }

    private void updateDisplay() {
        final SharedPreferences pref = getSharedPreferences();

        deviceName = pref.getString(Constants.SCIO_NAME, null);
        deviceAddress = pref.getString(Constants.SCIO_ADDRESS, null);
        username = pref.getString(Constants.USER_NAME, null);
        modelName = pref.getString(Constants.MODEL_NAME, null);
        modelId = pref.getString(Constants.MODEL_ID, null);

        nameTextView.setText(deviceName);
        addressTextView.setText(deviceAddress);
        usernameTextView.setText(username);
        modelTextView.setText(modelName);

        if (!isDeviceConnected()) {
            statusTextView.setText("Rozłączono");
        }
        else {
            statusTextView.setText("Połączono");
        }
    }
}