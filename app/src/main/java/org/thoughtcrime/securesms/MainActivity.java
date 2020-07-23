package org.thoughtcrime.securesms;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mesh.managers.GTMeshManager;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.AssertedSuccessListener;

import java.util.ArrayList;

public class MainActivity extends PassphraseRequiredActionBarActivity {

  private final DynamicTheme  dynamicTheme = new DynamicNoActionBarTheme();
  private final MainNavigator navigator    = new MainNavigator(this);

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);
    setContentView(R.layout.main_activity);

    navigator.onCreate(savedInstanceState);

    String action = getIntent().getAction();
    if (action != null && action.equals("com.samourai.txtenna.HEX")) {
      onNewIntent(getIntent());
    }
  }

  @Override
  protected void onPreCreate() {
    super.onPreCreate();
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public void onBackPressed() {
    if (!navigator.onBackPressed()) {
      super.onBackPressed();
    }
  }

  public @NonNull MainNavigator getNavigator() {
    return navigator;
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Log.i("MainActivity", "onNewIntent()");

    if (isFinishing()) {
      Log.w("MainActivity", "Activity is finishing...");
      return;
    }

    String action = intent.getAction();
    if (action != null && action.equals("com.samourai.txtenna.HEX")) {
      String hex = intent.getStringExtra(Intent.EXTRA_TEXT);
      String[] s = hex.split("-");
      Log.d("MainActivity", "network:" + s[1] + "hex:" + s[0]);

      if (GTMeshManager.getInstance().isPaired()) {
        final String gatewayGID = "555555555";
        GTMeshManager.getInstance().sendBitcoinTxInternal(gatewayGID, s[0], s[1]);
      }
    }
  }
}
