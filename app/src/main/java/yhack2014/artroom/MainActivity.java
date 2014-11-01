package yhack2014.artroom;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.moxtra.sdk.MXAccountManager;
import com.moxtra.sdk.MXSDKConfig;
import com.moxtra.sdk.MXSDKException;

import java.util.UUID;

public class MainActivity extends Activity {
    MXAccountManager acctMgr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            acctMgr = MXAccountManager.createInstance(this, "u-DTusZ0ruc", "vWLjlkGMZOk");
        } catch (MXSDKException.InvalidParameter invalidParameter) {
            invalidParameter.printStackTrace();
        }

        MXSDKConfig.MXUserInfo userInfo = new MXSDKConfig.MXUserInfo(UUID.randomUUID().toString(), MXSDKConfig.MXUserIdentityType.IdentityUniqueId);
        MXSDKConfig.MXProfileInfo profile = new MXSDKConfig.MXProfileInfo("Daven", "Wu", null);
        acctMgr.setupUser(userInfo, profile, null, new MXAccountManager.MXAccountLinkListener(){
            @Override
            public void onLinkAccountDone(boolean bSuccess){
                if(bSuccess) {
                    Toast.makeText(getBaseContext(), "Linked account!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getBaseContext(), "Account link failed!", Toast.LENGTH_LONG).show();
                }
            }
        });
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
