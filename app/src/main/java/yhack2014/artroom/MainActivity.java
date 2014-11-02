package yhack2014.artroom;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.widget.Toast;
import android.widget.TextView;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.estimote.sdk.Utils;
import com.moxtra.sdk.MXAccountManager;
import com.moxtra.sdk.MXChatManager;
import com.moxtra.sdk.MXException;
import com.moxtra.sdk.MXSDKConfig;
import com.moxtra.sdk.MXSDKException;

import java.util.List;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    // Animation stuff
    private final float SCROLL_THRESHOLD = 10;
    private float xTouch;
    private float yTouch;
    private boolean isOnClick;
    private float mDownX;
    private float mDownY;

    // Estimote stuff
    private BeaconManager beaconManager;
    private static final String ESTIMOTE_PROXIMITY_UUID = "B9407F30-F5F8-466E-AFF9-25556B57FE6D";
    private static final Region ALL_ESTIMOTE_BEACONS = new Region("regionId", ESTIMOTE_PROXIMITY_UUID, null, null);

    // Moxtra stuff
    private MXAccountManager accountManager;
    private String userId, firstName = "First", lastName = "Last";
    private static final String PREF_USER_ID = "PREF_USER_ID";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // Scales text size
            final DisplayMetrics metrics =
                    Resources.getSystem().getDisplayMetrics();

            final float scale = metrics.density / 3;

            TextView title = (TextView) findViewById(R.id.title);
            TextView description = (TextView) findViewById(R.id.description);

            title.setTextSize(title.getTextSize() * scale);
            description.setTextSize(description.getTextSize() * scale);
        } else {
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
                                Log.d(TAG, xTouch + ", " + yTouch);
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
        SharedPreferences sharedPrefs = getSharedPreferences(PREF_USER_ID, Context.MODE_PRIVATE);
        userId = sharedPrefs.getString(PREF_USER_ID, null);

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
                if (closestBeacon != null) {
                    Log.d(TAG, "Minor value of the closest beacon: " + closestBeacon.getMinor());
                    Log.d(TAG, "Closest beacon set");
                } else {
                    Log.d(TAG, "Closest beacon not set");
                }

            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();/*
        beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
            @Override
            public void onServiceReady() {
                try {
                    beaconManager.startRanging(ALL_ESTIMOTE_BEACONS);
                } catch (RemoteException e) {
                    Log.e(TAG, "Cannot start ranging", e);
                }
            }
        });*/
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
                Log.d(TAG, "Logged out of " + mxUserInfo.userIdentity.toString());
            }
        });
        super.onDestroy();
    }

    // Method called when the art card is clicked
    public void cardClick(View view) {
        TextView description = (TextView) findViewById(R.id.description);

        // Lollipop and up
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ColorStateList colorStateList = ColorStateList.valueOf(R.color.green);
            RippleDrawable ripple = new RippleDrawable(colorStateList, null, null);
            ripple.setHotspot(xTouch, yTouch);

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
