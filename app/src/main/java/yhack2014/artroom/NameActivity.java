package yhack2014.artroom;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_name, menu);
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
