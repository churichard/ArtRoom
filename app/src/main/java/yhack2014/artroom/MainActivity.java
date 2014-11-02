package yhack2014.artroom;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Outline;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;

public class MainActivity extends Activity {
    private static final String TAG = "yhack2014.artroom.MainActivity";
    public static final String SHARED_PREFS_NAME = "yhack2014.artroom";
    public static final String FIRST_RUN = "FIRST_RUN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        SharedPreferences sharedPrefs = getSharedPreferences(FIRST_RUN, Context.MODE_PRIVATE);

        if (sharedPrefs.getBoolean(FIRST_RUN, true)) {
            Intent intent = new Intent(this, NameActivity.class);
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, ScanActivity.class);
            startActivity(intent);
        }
    }
}