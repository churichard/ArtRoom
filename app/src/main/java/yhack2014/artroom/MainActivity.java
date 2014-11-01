package yhack2014.artroom;

import android.app.Activity;
import android.os.AsyncTask;
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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.List;

import com.moxtra.sdk.MXAccountManager;
import com.moxtra.sdk.MXChatManager;
import com.moxtra.sdk.MXException;
import com.moxtra.sdk.MXSDKConfig;
import com.moxtra.sdk.MXSDKException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import java.util.UUID;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    // Estimote stuff
    private BeaconManager beaconManager;
    private static final String ESTIMOTE_PROXIMITY_UUID = "B9407F30-F5F8-466E-AFF9-25556B57FE6D";
    private static final Region ALL_ESTIMOTE_BEACONS = new Region("regionId", ESTIMOTE_PROXIMITY_UUID, null, null);
    private TextView textView;

    // Moxtra stuff
    private MXAccountManager acctMgr;

    //Alex stuff
    String urlStr = "http://media.jarnbjo.de/412.php";
    URL url;
    HttpURLConnection conn;
    BufferedReader br;
    String output;
    Beacon sentBeac;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Moxtra code
        try {
            acctMgr = MXAccountManager.createInstance(this, "u-DTusZ0ruc", "vWLjlkGMZOk");
        } catch (MXSDKException.InvalidParameter invalidParameter) {
            invalidParameter.printStackTrace();
        }

        if(!acctMgr.isLinked()) {
            MXSDKConfig.MXUserInfo userInfo = new MXSDKConfig.MXUserInfo(UUID.randomUUID().toString(), MXSDKConfig.MXUserIdentityType.IdentityUniqueId);
            MXSDKConfig.MXProfileInfo profile = new MXSDKConfig.MXProfileInfo("Replace", "Me", null);
            acctMgr.setupUser(userInfo, profile, null, new MXAccountManager.MXAccountLinkListener() {
                @Override
                public void onLinkAccountDone(boolean bSuccess) {
                    if (bSuccess) {
                        Toast.makeText(getBaseContext(), "Linked account!", Toast.LENGTH_LONG).show();
                        createChat();
                    } else {
                        Toast.makeText(getBaseContext(), "Account link failed!", Toast.LENGTH_LONG).show();
                    }
                }
            });
        } else {
            createChat();
        }

        // Estimote code
        beaconManager = new BeaconManager(this);
        textView = (TextView) findViewById(R.id.textView);

        beaconManager.setRangingListener(new BeaconManager.RangingListener() {
            @Override public void onBeaconsDiscovered(Region region, List<Beacon> beacons) {
                double minDistance = Integer.MAX_VALUE;
                Beacon closestBeacon = null;
                for (int i = 0; i < beacons.size(); i++) {
                    double dist = Utils.computeAccuracy(beacons.get(i));
                    if (dist < minDistance) {
                        minDistance = dist;
                        closestBeacon = beacons.get(i);
                    }
                }
                if(Utils.computeAccuracy(closestBeacon) < 1 && (closestBeacon == null || closestBeacon != sentBeac)) {
                    new GetDataTask().execute();
//                    try {
//                        System.out.println(4);
//                        url = new URL(urlStr);
//
//                        conn = (HttpURLConnection) url.openConnection();
//                        System.out.println(5);
//                        conn.setRequestMethod("GET");
//                        System.out.println(6);
//                        conn.setRequestProperty("Accept", "application/json");
//
//                        System.out.println(conn.getResponseCode());
//                        System.out.println(7);
//                        if (conn.getResponseCode() != 200) {
//                            throw new RuntimeException("Failed : HTTP error code : "
//                                    + conn.getResponseCode());
//                        }
//                        BufferedReader br = new BufferedReader(new InputStreamReader(
//                                (conn.getInputStream())));
//
//                        String output;
//                        System.out.println("Output from Server .... \n");
//                        while ((output = br.readLine()) != null) {
//                            Log.d(TAG, output);
//                        }
//
//                        conn.disconnect();
//
//                    } catch (Exception e) {
//                        System.out.println(e.getStackTrace());
//                        System.out.println("Fail");
//                    }
               }

                textView.setText("Minor value of the closest beacon: " + closestBeacon.getMinor());
                Log.d(TAG, "Number of beacons: " + beacons.size());
            }
        });
    }

    public void createChat() {
        Log.d("MoxtraChat", "createChat() called");
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
            Log.e(TAG, "createChatFailed.", accountManagerIsNotValid);
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
    private class GetDataTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            String url = "http://www.google.com";
            HttpClient httpClient = new DefaultHttpClient();
            try{
                HttpGet httpGet = new HttpGet(url);
                HttpResponse httpResponse = httpClient.execute(httpGet);
                HttpEntity httpEntity = httpResponse.getEntity();

                if(httpEntity != null) {


                    InputStream inputStream = httpEntity.getContent();

                    //Lecture du retour au format JSON
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder stringBuilder = new StringBuilder();

                    String ligneLue = bufferedReader.readLine();
                    while (ligneLue != null) {
                        stringBuilder.append(ligneLue + " \n");
                        ligneLue = bufferedReader.readLine();
                    }
                    bufferedReader.close();

                    Log.d(TAG, stringBuilder.toString());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }


            return null;
        }
    }
}
