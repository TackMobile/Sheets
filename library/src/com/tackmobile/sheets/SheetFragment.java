package com.tackmobile.sheets;

import android.support.v4.app.Fragment;
import android.view.MotionEvent;

public abstract class SheetFragment extends Fragment {
  
  public boolean shouldInterceptLayoutMotionEvent(MotionEvent ev) {
    // For rent
    return false;
  }
  
}
