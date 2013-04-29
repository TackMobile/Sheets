package com.tackmobile.sheets;

import android.support.v4.app.Fragment;
import android.view.MotionEvent;

public abstract class SheetFragment extends Fragment {
  
  private boolean mEnableShadows = true;
  
  public boolean shouldInterceptLayoutMotionEvent(MotionEvent ev) {
    // For rent
    return false;
  }
  
  public void enableShadows(boolean enableShadows) {
    if (mEnableShadows != enableShadows) {
      mEnableShadows = enableShadows;
    }
  }
}
