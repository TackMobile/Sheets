package com.tackmobile.sheetssample;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import com.tackmobile.sheets.SheetLayout;
import com.tackmobile.sheets.SimpleSheetFragmentAdapter;

public class SheetSampleMainActivity extends FragmentActivity {

  private static final String TAG = "MainActivity";

  public static final String ACTION_ADD_SHEET = "com.tackmobile.sheets.ACTION_ADD_SHEET";

  public static final String ACTION_POP_SHEET = "com.tackmobile.sheets.ACTION_POP_SHEET";
  
  public static final String ACTION_POP_ALL_SHEETS = "com.tackmobile.sheets.ACTION_POP_ALL_SHEETS";

  public static final String EXTRA_SHEET_CLASS_NAME = "com.tackmobile.sheets.EXTRA_SHEET_CLASS";

  public static final String EXTRA_ARGS = "com.tackmobile.sheets.EXTRA_ARGS";

  public static final String KEY_VIEW_INDEX = "com.tackmobile.sheets.KEY_VIEW_INDEX";

  private SheetLayout mSheetLayout;

  private SampleSheetAdapter mAdapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mSheetLayout = (SheetLayout) findViewById(R.id.sheet_layout);
    mAdapter = new SampleSheetAdapter(this, getSupportFragmentManager());
    mSheetLayout.setAdapter(mAdapter);
  }

  @Override
  protected void onResume() {
    super.onResume();

    LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
    lbm.registerReceiver(mAddBroadcastReceiver, new IntentFilter(ACTION_ADD_SHEET));
    lbm.registerReceiver(mPopBroadcastReceiver, new IntentFilter(ACTION_POP_SHEET));
    lbm.registerReceiver(mPopBroadcastReceiver, new IntentFilter(ACTION_POP_ALL_SHEETS));
  }

  @Override
  protected void onPause() {
    super.onPause();

    LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
    lbm.unregisterReceiver(mAddBroadcastReceiver);
    lbm.unregisterReceiver(mPopBroadcastReceiver);
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    // Let the sheet layout attempt to handle the key first
    if (mSheetLayout != null) {
      return mSheetLayout.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    } else {
      return super.dispatchKeyEvent(event);
    }
  }
  
  public void addSheetExplicitly(View view) {
    Bundle args = SheetSampleSheetFragment.getRandomColorArgs();
    mAdapter.addSheetFragment(SheetSampleSheetFragment.class, args);
  }

  public void addSheetImplicity(View view) {
    Intent intent = new Intent(ACTION_ADD_SHEET);
    intent.putExtra(EXTRA_SHEET_CLASS_NAME, SheetSampleSheetFragment.class.getCanonicalName());
    intent.putExtra(EXTRA_ARGS, SheetSampleSheetFragment.getRandomColorArgs());
    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
  }

  private BroadcastReceiver mAddBroadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (!ACTION_ADD_SHEET.equals(intent.getAction()))
        return;
      if (mAdapter == null)
        return;

      boolean hasSheetClass = intent.hasExtra(EXTRA_SHEET_CLASS_NAME);
      if (!hasSheetClass) {
        Log.e(TAG, "Intent with action SheetLayoutFragment.ACTION_ADD_SHEET must have an extra "
            + "with key SheetLayoutFragment.EXTRA_SHEET_CLASS and String value representing a " + "SheetFragment class");
      }
      String sheetFragmentClass = intent.getStringExtra(EXTRA_SHEET_CLASS_NAME);
      ClassLoader cl = getClass().getClassLoader();
      Class<?> clazz = null;
      try {
        clazz = cl.loadClass(sheetFragmentClass);
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
        return;
      }

      Bundle args = intent.hasExtra(EXTRA_ARGS) ? intent.getBundleExtra(EXTRA_ARGS) : new Bundle();
      args.putInt(KEY_VIEW_INDEX, mAdapter.getCount());

      if (clazz == null || !(Fragment.class.isInstance(clazz))) {
        mAdapter.addSheetFragment(clazz.asSubclass(Fragment.class), args);
      }
    }
  };

  private BroadcastReceiver mPopBroadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (mAdapter == null) return;
      
      String intentAction = intent.getAction();
      if (ACTION_POP_SHEET.equals(intentAction)) {
        mAdapter.popSheetFragment(mAdapter.getCount() - 1);
      } else if (ACTION_POP_ALL_SHEETS.equals(intentAction)) {
        mAdapter.popAllSheets();
      }
    }
  };
  
  private static class SampleSheetAdapter extends SimpleSheetFragmentAdapter {
    public SampleSheetAdapter(Context context, FragmentManager fm) {
      super(context, fm);
    }
    
    @Override
    public void addSheetFragment(SheetDescriptor descriptor) {
      descriptor.args.putInt(KEY_VIEW_INDEX, getCount());
      super.addSheetFragment(descriptor);
    }
  }
}
