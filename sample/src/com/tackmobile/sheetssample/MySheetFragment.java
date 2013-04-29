package com.tackmobile.sheetssample;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.tackmobile.sheets.SheetFragment;

public class MySheetFragment extends SheetFragment {

  public static final String KEY_COLOR = "keyColor";

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.frag_sheet_sample, container, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    
    Bundle args = getArguments();
    if (args != null && args.containsKey(KEY_COLOR)) {
      view.setBackgroundColor(args.getInt(KEY_COLOR, Color.WHITE));
    }
    
    view.findViewById(R.id.btn_add_sheet).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent addIntent = new Intent(MainActivity.ACTION_ADD_SHEET);
        addIntent.putExtra(MainActivity.EXTRA_SHEET_CLASS_NAME, MySheetFragment.class.getCanonicalName());
        addIntent.putExtra(MainActivity.EXTRA_ARGS, getRandomColorArgs());
        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(addIntent);
      }
    });

    view.findViewById(R.id.btn_pop_sheet).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent popIntent = new Intent(MainActivity.ACTION_POP_SHEET);
        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(popIntent);
      }
    });

    int viewIndex = args.getInt(MainActivity.KEY_VIEW_INDEX);
    TextView textViewIndex = (TextView) view.findViewById(R.id.text_view_index);
    textViewIndex.setText("View Index : "+viewIndex);
  }

  public static Bundle getRandomColorArgs() {
    Bundle args = new Bundle();
    Double r, g, b;
    r = Math.random() * 255;
    g = Math.random() * 255;
    b = Math.random() * 255;
    int color = Color.rgb(r.intValue(), g.intValue(), b.intValue());
    args.putInt(MySheetFragment.KEY_COLOR, color);
    return args;
  }
}
