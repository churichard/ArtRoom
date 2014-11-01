package yhack2014.artroom;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import android.widget.TextView;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.estimote.sdk.Utils;

import java.util.List;

import com.moxtra.sdk.MXAccountManager;
import com.moxtra.sdk.MXChatManager;
import com.moxtra.sdk.MXException;
import com.moxtra.sdk.MXSDKConfig;
import com.moxtra.sdk.MXSDKException;

import java.util.UUID;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    // Estimote stuff
    private BeaconManager beaconManager;
    private static final String ESTIMOTE_PROXIMITY_UUID = "B9407F30-F5F8-466E-AFF9-25556B57FE6D";
    private static final Region ALL_ESTIMOTE_BEACONS = new Region("regionId", ESTIMOTE_PROXIMITY_UUID, null, null);
    private TextView textView;

    // Moxtra stuff
    private MXAccountManager accountManager;
    private String userId, firstName = "First", lastName = "Last";
    private static final String PREF_USER_ID = "PREF_USER_ID";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Moxtra code
        SharedPreferences sharedPrefs = getSharedPreferences(PREF_USER_ID, Context.MODE_PRIVATE);
        userId = sharedPrefs.getString(PREF_USER_ID, null);

        if(userId == null) {
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

        if(!accountManager.isLinked()) {
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

                textView.setText("Minor value of the closest beacon: " + closestBeacon.getMinor());
                Log.d(TAG, "Closest beacon set");
            }
        });
    }

    public void createChat() {
        MXChatManager conversationMgr = MXChatManager.getInstance();
        try {
            conversationMgr.createChat(new MXChatManager.OnCreateChatListener() {
                @Override
                public void onCreateChatSuccess(String binderID) {
                    Log.d(TAG, "onCreateChatSuccess(), binderID = " + binderID);
                }

                @Override
                public void onCreateChatFailed(int errorCode, String message) {
                    Log.d(TAG, "onCreateChatFailed(), errorCode = " + errorCode + ", message = " + message);
                }
            });
        } catch (MXException.AccountManagerIsNotValid accountManagerIsNotValid) {
            accountManagerIsNotValid.printStackTrace();
        }
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
            }
        });
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
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
}
