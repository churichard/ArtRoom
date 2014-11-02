package yhack2014.artroom;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;


public class NameActivity extends Activity {
    public static final String FIRST_NAME = "FIRST_NAME";
    public static final String LAST_NAME = "LAST_NAME";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_name);
    }

    public void submit(View v) {
        EditText firstName = (EditText) findViewById(R.id.nameFirstNameText);
        EditText lastName = (EditText) findViewById(R.id.nameLastNameText);
        SharedPreferences.Editor editor = getSharedPreferences(MainActivity.SHARED_PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(FIRST_NAME, firstName.getText().toString());
        editor.putString(LAST_NAME, lastName.getText().toString());
        editor.putBoolean(MainActivity.FIRST_RUN, true);
        editor.commit();

        Intent intent = new Intent(this, ScanActivity.class);
        startActivity(intent);
    }
}
