package yhack2014.artroom;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.estimote.sdk.Utils;
import com.moxtra.sdk.MXAccountManager;
import com.moxtra.sdk.MXChatManager;
import com.moxtra.sdk.MXException;
import com.moxtra.sdk.MXSDKConfig;
import com.moxtra.sdk.MXSDKException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.UUID;

public class ScanActivity extends Activity {
    private static final String TAG = "yhack2014.artroom.ScanActivity";

    // Animation stuff
    private final float SCROLL_THRESHOLD = 10;
    private float xTouch;
    private float yTouch;
    private boolean isOnClick;
    private float mDownX;
    private float mDownY;

    private float xFabTouch;
    private float yFabTouch;
    private boolean isFabOnClick;
    private float mFabDownX;
    private float mFabDownY;

    // Estimote stuff
    private BeaconManager beaconManager;
    private boolean rangingOn = false;
    private boolean scanning = false;
    private static final String ESTIMOTE_PROXIMITY_UUID = "B9407F30-F5F8-466E-AFF9-25556B57FE6D";
    private static final Region ALL_ESTIMOTE_BEACONS = new Region("regionId", ESTIMOTE_PROXIMITY_UUID, null, null);

    // Moxtra stuff
    private MXAccountManager accountManager;
    private String userId, firstName, lastName;
    private static final String PREF_USER_ID = "PREF_USER_ID";

    private SharedPreferences sharedPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        sharedPrefs = getSharedPreferences(MainActivity.SHARED_PREFS_NAME, Context.MODE_PRIVATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Get touch coordinates
            final View touchView = findViewById(R.id.card);

            View.OnTouchListener touchListener = new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction() & MotionEvent.ACTION_MASK) {
                        case MotionEvent.ACTION_DOWN:
                            mDownX = event.getX();
                            mDownY = event.getY();
                            isOnClick = true;
                            break;
                        case MotionEvent.ACTION_UP:
                            if (isOnClick) {
                                xTouch = event.getX();
                                yTouch = event.getY();
                                cardClick(v);
                            }
                            break;
                        case MotionEvent.ACTION_MOVE:
                            if (isOnClick && (Math.abs(mDownX - event.getX()) > SCROLL_THRESHOLD
                                    || Math.abs(mDownY - event.getY()) > SCROLL_THRESHOLD)) {
                                isOnClick = false;
                            }
                            break;
                        default:
                            break;
                    }

                    return true;
                }
            };

            touchView.setOnTouchListener(touchListener);
        }

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
    }

    // Call to get user to join binder with given binderId
    public static void joinChat(String binderId) {
        MXChatManager conversationMgr = MXChatManager.getInstance();
        try {
            conversationMgr.openChat(binderId, new MXChatManager.OnOpenChatListener() {
                @Override
                public void onOpenChatSuccess() {
                    Log.i(TAG, "Opened discuss");
                }

                @Override
                public void onOpenChatFailed(int i, String s) {
                    Log.e(TAG, "i: " + i + " s: " + s);
                }
            });
        } catch (MXException.AccountManagerIsNotValid accountManagerIsNotValid) {
            accountManagerIsNotValid.printStackTrace();
        }
    }

    // Starts scanning for iBeacons nearby the user and times out after 3 seconds
    public void scanForBeacons(View v) {
        Log.i(TAG, "Scanning for beacons");
        scanning = true;
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

        findViewById(R.id.scanScanningLabel).setVisibility(View.VISIBLE);
        findViewById(R.id.scanArtContainer).setVisibility(View.INVISIBLE);
        findViewById(R.id.scanInitialLabel).setVisibility(View.INVISIBLE);
        findViewById(R.id.scanDiscussButton).setVisibility(View.INVISIBLE);
        findViewById(R.id.scanNoObjectsLabel).setVisibility(View.INVISIBLE);

        final Handler handler = new Handler();
        final Runnable stopScanning = new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "No beacons found");
                findViewById(R.id.scanNoObjectsLabel).setVisibility(View.VISIBLE);
                findViewById(R.id.scanScanningLabel).setVisibility(View.INVISIBLE);
                findViewById(R.id.scanDiscussButton).setVisibility(View.INVISIBLE);
                scanning = false;
                beaconManager.setRangingListener(new BeaconManager.RangingListener() {
                    @Override
                    public void onBeaconsDiscovered(Region region, List<Beacon> beacons) {
                        // Do nothing
                    }
                });
            }
        };
        handler.postDelayed(stopScanning, 3000L);

        beaconManager.setRangingListener(new BeaconManager.RangingListener() {
            @Override
            public void onBeaconsDiscovered(Region region, List<Beacon> beacons) {
                if(scanning) {
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
                    scanning = false;

                    if(closestBeacon != null) {
                        if (Utils.computeProximity(closestBeacon) == Utils.Proximity.IMMEDIATE) {
                            new GetObjectTask().execute(closestBeacon.getMinor());
                            findViewById(R.id.scanDiscussButton).setVisibility(View.VISIBLE);
                            findViewById(R.id.scanScanningLabel).setVisibility(View.INVISIBLE);
                            findViewById(R.id.scanNoObjectsLabel).setVisibility(View.INVISIBLE);

                            Log.d(TAG, "Minor value of the closest beacon: " + closestBeacon.getMinor());
                            Log.d(TAG, "Closest beacon set");
                        } else {
                            Log.d(TAG, "No beacons close enough");
                            findViewById(R.id.scanNoObjectsLabel).setVisibility(View.VISIBLE);
                            findViewById(R.id.scanScanningLabel).setVisibility(View.INVISIBLE);
                            findViewById(R.id.scanDiscussButton).setVisibility(View.INVISIBLE);
                        }
                    }

                    beaconManager.disconnect();
                }
            }
        });
    }

    // Method called when the art card is clicked
    public void cardClick(View view) {
        TextView description = (TextView) findViewById(R.id.description);

        // Lollipop and up
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            int width = size.x;

            if (description.getVisibility() == View.GONE) {
                // previously invisible view
                final View myView = findViewById(R.id.card_view);

                // get the center for the clipping circle
                final int cx = (int) xTouch;
                final int cy = (int) yTouch;

                final int initialRadius = 0;
                final int finalRadius = width;

                // create and start the animator for this view
                // (the start radius is zero)
                Animator anim = ViewAnimationUtils.createCircularReveal(myView, cx, cy, finalRadius, initialRadius);
                anim.start();

                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        final View description = findViewById(R.id.description);
                        description.setVisibility(View.VISIBLE);
                        findViewById(R.id.divider).setVisibility(View.VISIBLE);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            ViewAnimationUtils.createCircularReveal(myView, cx, cy, initialRadius, finalRadius).start();
                        }
                    }
                });
            } else {
                // previously visible view
                final View myView = findViewById(R.id.card_view);

                // get the center for the clipping circle
                final int cx = (int) xTouch;
                final int cy = (int) yTouch;

                final int initialRadius = width;
                final int finalRadius = 0;

                // create the animation (the final radius is zero)
                Animator anim =
                        ViewAnimationUtils.createCircularReveal(myView, cx, cy, initialRadius, finalRadius);
                anim.start();

                // make the view invisible when the animation is done
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        final View description = findViewById(R.id.description);
                        description.setVisibility(View.GONE);
                        findViewById(R.id.divider).setVisibility(View.GONE);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            ViewAnimationUtils.createCircularReveal(myView, cx, cy, finalRadius, initialRadius).start();
                        }
                    }
                });
            }
        }
        // Lower than Lollipop
        else {
            if (description.getVisibility() == View.GONE) {
                description.setVisibility(View.VISIBLE);
            } else {
                description.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
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
                Log.d(TAG, "Logged out of " + mxUserInfo.userIdentity);
            }
        });
        super.onDestroy();
    }

    private class GetObjectTask extends AsyncTask<Integer, Void, JSONObject> {

        @Override
        protected JSONObject doInBackground(Integer... integers) {
            int minorId = integers[0];
            String url = "http://artroom.ngrok.com/beacon/" + minorId;
            HttpGet get = new HttpGet(url);
            HttpClient client = new DefaultHttpClient();
            StringBuilder stringBuilder = new StringBuilder();

            try {
                HttpResponse response = client.execute(get);

                Log.d(TAG, "Status Code: " + response.getStatusLine().getStatusCode());
                if (response.getStatusLine().getStatusCode() == 200) {
                    InputStream responseStream = response.getEntity().getContent();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream));
                    String line;

                    while ((line = reader.readLine()) != null) {
                        stringBuilder.append(line);
                    }

                    return (new JSONArray(stringBuilder.toString()).getJSONObject(0));
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(JSONObject jsonObject) {
            if(jsonObject != null) {
                String author = null, title = null, curatorComment = null, image = null;
                final String binderId;
                try {
                    author = jsonObject.getString("Author");
                    title = jsonObject.getString("Title");
                    curatorComment = jsonObject.getString("CuratorComment");
                    image = jsonObject.getString("ImageURL");
                    new GetImageTask().execute(image);
                    //binderId = jsonObject.getString("BinderId");

                    findViewById(R.id.scanDiscussButton).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            //joinChat(binderId);
                        }
                    });
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                TextView authorText = (TextView) findViewById(R.id.scanArtAuthor);
                TextView titleText = (TextView) findViewById(R.id.scanArtTitle);
                TextView curatorCommentText = (TextView) findViewById(R.id.scanCuratorComment);

                if(author.length() == 0) {
                    authorText.setText("by Unknown Artist");
                } else {
                    authorText.setText("by " + author);
                }
                titleText.setText(title);
                curatorCommentText.setText("Curator Comment: " + curatorComment);

                findViewById(R.id.scanArtContainer).setVisibility(View.VISIBLE);
            }
        }
    }

    private class GetImageTask extends AsyncTask<String, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(String... urls) {
            String url = urls[0];
            final DefaultHttpClient client = new DefaultHttpClient();
            final HttpGet getRequest = new HttpGet(url);

            try {
                HttpResponse response = client.execute(getRequest);
                final int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode != HttpStatus.SC_OK) {
                    Log.w("ImageDownloader", "Error " + statusCode +
                            " while retrieving bitmap from " + url);
                    return null;
                }

                final HttpEntity entity = response.getEntity();

                if (entity != null) {
                    InputStream inputStream = null;
                    try {
                        inputStream = entity.getContent();
                        final Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                        return bitmap;
                    } finally {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                        entity.consumeContent();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            ((ImageView) findViewById(R.id.scanArtPicture)).setImageBitmap(bitmap);
            findViewById(R.id.scanArtContainer).setVisibility(View.VISIBLE);
        }
    }
}
