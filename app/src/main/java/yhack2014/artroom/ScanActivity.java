package yhack2014.artroom;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import android.widget.TextView;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.estimote.sdk.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import com.moxtra.sdk.MXAccountManager;
import com.moxtra.sdk.MXChatManager;
import com.moxtra.sdk.MXException;
import com.moxtra.sdk.MXSDKConfig;
import com.moxtra.sdk.MXSDKException;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;

import java.util.UUID;

public class ScanActivity extends Activity {
    public static final String TAG = "ScanActivity";
    private SharedPreferences sharedPrefs;

    // Estimote stuff
    private BeaconManager beaconManager;
    private static final String ESTIMOTE_PROXIMITY_UUID = "B9407F30-F5F8-466E-AFF9-25556B57FE6D";
    private static final Region ALL_ESTIMOTE_BEACONS = new Region("regionId", ESTIMOTE_PROXIMITY_UUID, null, null);
    private TextView textView;

    // Moxtra stuff
    private MXAccountManager accountManager;
    private String userId, firstName, lastName;
    private static final String PREF_USER_ID = "PREF_USER_ID";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        sharedPrefs = getSharedPreferences(MainActivity.SHARED_PREFS_NAME, Context.MODE_PRIVATE);

        // Moxtra code
        userId = sharedPrefs.getString(PREF_USER_ID, null);
        firstName = sharedPrefs.getString(NameActivity.FIRST_NAME, null);
        lastName = sharedPrefs.getString(NameActivity.LAST_NAME, null);

        if (userId == null) {
            userId = UUID.randomUUID().toString();
            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putString(PREF_USER_ID, userId);
            editor.commit();
        }

        try {
            accountManager = MXAccountManager.createInstance(this, "u-DTusZ0ruc", "vWLjlkGMZOk");
        } catch (MXSDKException.InvalidParameter invalidParameter) {
            invalidParameter.printStackTrace();
        }

        if (!accountManager.isLinked()) {
            MXSDKConfig.MXUserInfo userInfo = new MXSDKConfig.MXUserInfo(userId, MXSDKConfig.MXUserIdentityType.IdentityUniqueId);
            MXSDKConfig.MXProfileInfo profile = new MXSDKConfig.MXProfileInfo(firstName, lastName, null);
            accountManager.setupUser(userInfo, profile, null, new MXAccountManager.MXAccountLinkListener() {
                @Override
                public void onLinkAccountDone(boolean bSuccess) {
                    if (bSuccess) {
                        Log.i(TAG, "Account linked");
                        Toast.makeText(getBaseContext(), "Logged in", Toast.LENGTH_LONG).show();
                    } else {
                        Log.e(TAG, "Account link failed");
                        Toast.makeText(getBaseContext(), "Failed to log in", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        // Estimote code
        beaconManager = new BeaconManager(this);
        textView = (TextView) findViewById(R.id.textView);
    }

    // Call to get user to join binder with binderId assigned to closest beacon
    public void joinChat(View v) {
        new GetBinderTask().execute();
    }

    // Starts scanning for iBeacons nearby the user and times out after 2 seconds
    public void scanForBeacons(View v) {
        final Handler handler = new Handler();
        final Runnable stopScanning = new Runnable() {
            @Override
            public void run() {
                beaconManager.setRangingListener(new BeaconManager.RangingListener() {
                    @Override
                    public void onBeaconsDiscovered(Region region, List<Beacon> beacons) {
                        // Do nothing
                    }
                });
            }
        };
        handler.postDelayed(stopScanning, 2000L);

        beaconManager.setRangingListener(new BeaconManager.RangingListener() {
            @Override
            public void onBeaconsDiscovered(Region region, List<Beacon> beacons) {
                double minDistance = Double.MAX_VALUE;
                Beacon closestBeacon = null;

                for (int i = 0; i < beacons.size(); i++) {
                    double dist = Utils.computeAccuracy(beacons.get(i));
                    if (dist < minDistance) {
                        minDistance = dist;
                        closestBeacon = beacons.get(i);
                    }
                }

                handler.removeCallbacks(stopScanning);
                textView.setText("Minor value of the closest beacon: " + closestBeacon.getMinor());
                Log.d(TAG, "Closest beacon set");
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
            @Override
            public void onServiceReady() {
                try {
                    beaconManager.startRanging(ALL_ESTIMOTE_BEACONS);
                } catch (RemoteException e) {
                    Log.e(TAG, "Cannot start ranging", e);
                }
            }
        });
    }

    @Override
    protected void onStop() {
        try {
            beaconManager.stopRanging(ALL_ESTIMOTE_BEACONS);
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot stop but it does not matter now", e);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        beaconManager.disconnect();
        accountManager.unlinkAccount(new MXAccountManager.MXAccountUnlinkListener() {
            @Override
            public void onUnlinkAccountDone(MXSDKConfig.MXUserInfo mxUserInfo) {
                Log.i(TAG, "Logged out of " + mxUserInfo.userIdentity.toString());
            }
        });
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scan, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class GetBinderTask extends AsyncTask<Integer, Void, String> {

        @Override
        protected String doInBackground(Integer... integers) {
            int minorId = integers[0];
            String url = "http://artroom.ngrok.com/beacon/" + minorId;
            HttpGet get = new HttpGet(url);
            HttpClient client = new DefaultHttpClient();
            StringBuilder stringBuilder = new StringBuilder();

            try {
                HttpResponse response = client.execute(get);

                if(response.getStatusLine().getStatusCode() == 200) {
                    InputStream responseStream = response.getEntity().getContent();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream));
                    String line;

                    while((line = reader.readLine()) != null) {
                        stringBuilder.append(line);
                    }

                    return stringBuilder.toString();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String binderId) {
            if(binderId != null) {
                MXChatManager conversationMgr = MXChatManager.getInstance();
                try {
                    conversationMgr.openChat(binderId, new MXChatManager.OnOpenChatListener() {
                        @Override
                        public void onOpenChatSuccess() {
                            Log.i(TAG, "Opened chat");
                        }

                        @Override
                        public void onOpenChatFailed(int i, String s) {
                            Log.e(TAG, "i: " + i + " s: " + s);
                        }
                    });
                } catch (MXException.AccountManagerIsNotValid accountManagerIsNotValid) {
                    accountManagerIsNotValid.printStackTrace();
                }
            } else {
                Toast.makeText(getBaseContext(), "Could not find discussion for this piece", Toast.LENGTH_LONG);
            }
        }

    }
}
