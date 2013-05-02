package com.tackmobile.sheetssample;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

public class SheetSampleListView extends ListView {

  public SheetSampleListView(Context context) {
    super(context);
  }

  public SheetSampleListView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }
  
  public SheetSampleListView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }
  
  public void forceLayoutChildren() {
    layoutChildren();
  }

}
