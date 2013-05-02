package com.tackmobile.sheetssample;

import java.util.ArrayList;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.tackmobile.sheets.ISheetFragment;
import com.tackmobile.sheets.ISheetListener;
import com.tackmobile.sheets.SimpleSheetFragmentAdapter.SheetDescriptor;

public class SheetSampleSheetFragment extends Fragment implements ISheetFragment {

  public static final String KEY_COLOR = "keyColor";
  
  //private static final String TAG = "SheetFragment";
  
  private String[] data = { "just", "a", "simple", "list", "just", "a", "simple", "list", "just", "a", "simple",
      "list", "just", "a", "simple", "list", "just", "a", "simple", "list", "just", "a", "simple", "list" };
  
  private ArrayList<String> mData = new ArrayList<String>();
  
  private ISheetListener mListener;

  private ListView mList;

  private ArrayAdapter<String> mAdapter;

  private Handler mHandler;

  private Runnable updateRunnable = new Runnable() {
    //h.post(new Runnable() {
    @Override
    public void run() {
      updateData();
    }
  };

  private boolean mAdapterIsSet;
  
  @Override
  public void setSheetListener(ISheetListener listener) {
    mListener = listener;
  }

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
        //Intent addIntent = new Intent(SheetSampleMainActivity.ACTION_ADD_SHEET);
        //addIntent.putExtra(SheetSampleMainActivity.EXTRA_SHEET_CLASS_NAME, SheetSampleSheetFragment.class.getCanonicalName());
        //addIntent.putExtra(SheetSampleMainActivity.EXTRA_ARGS, getRandomColorArgs());
        //LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(addIntent);
        SheetDescriptor d = new SheetDescriptor(SheetSampleSheetFragment.class, getRandomColorArgs());
        mListener.addSheetFragment(d);
      }
    });

    view.findViewById(R.id.btn_pop_sheet).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent popIntent = new Intent(SheetSampleMainActivity.ACTION_POP_SHEET);
        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(popIntent);
      }
    });
    
    view.findViewById(R.id.btn_pop_all_sheets).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent popAllIntent = new Intent(SheetSampleMainActivity.ACTION_POP_ALL_SHEETS);
        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(popAllIntent);
        updateData();
      }
    });

    int viewIndex = args.getInt(SheetSampleMainActivity.KEY_VIEW_INDEX);
    TextView textViewIndex = (TextView) view.findViewById(R.id.text_view_index);
    textViewIndex.setText("View Index : "+viewIndex);

    mData.add("first");
    mData.add("set");
    
    mHandler = new Handler(getActivity().getMainLooper());
    mList = (ListView) view.findViewById(android.R.id.list);
    mList.setScrollingCacheEnabled(false);
    mList.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        Toast.makeText(getActivity(), "List Item Clicked!", Toast.LENGTH_LONG).show();
      }
    });
    
    //setListAdapter(mData);
  }
  
  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    setListAdapter(mData);
    mHandler.postDelayed(updateRunnable, 3 * DateUtils.SECOND_IN_MILLIS);
  }
  
  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
  }
  
  @Override
  public void onResume() {
    super.onResume();
  }
  
  @Override
  public void onPause() {
    super.onPause();
    mHandler.removeCallbacks(updateRunnable);
  }
  
  public void setListAdapter(ArrayList<String> data) {
    //mAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, android.R.id.text1, data);
    mAdapter = new ArrayAdapter<String>(
        getActivity(), 
        android.R.layout.simple_list_item_1, 
        android.R.id.text1, 
        data);
    mList.setAdapter(mAdapter);
    mAdapterIsSet = true;
    Toast.makeText(getActivity(), "List Adapter Set", Toast.LENGTH_SHORT).show();
  }
  
  public void updateData() {
    mData.clear();
    for (String s : data) {
      mData.add(s);
    }
    
    if (!mAdapterIsSet) {
      setListAdapter(mData);
    } else {
      mAdapter.notifyDataSetChanged();
    }
    mList.setBackgroundColor(Color.WHITE);
    
//    mList.destroyDrawingCache();
//    mList.invalidateViews();
//
//    MotionEvent e = MotionEvent.obtain(System.currentTimeMillis(),
//                                       System.currentTimeMillis(),
//                                        MotionEvent.ACTION_DOWN, 
//                                        10, 10, 0);
//    mList.dispatchTouchEvent(e);
//    e.recycle();
//    
//    mList.setVisibility(View.INVISIBLE);
//    mList.setVisibility(View.VISIBLE);
//    mList.refreshDrawableState();
//    mList.requestLayout();
    
//    final ViewGroup parent = (ViewGroup) mList.getParent();
//    final ViewGroup.LayoutParams lp = mList.getLayoutParams();
//    parent.removeView(mList);
//    mList.setAdapter(null);
//    
//    getActivity().runOnUiThread(new Runnable() {
//      
//      @Override
//      public void run() {
//        //mList = new ListView(getActivity());
//        mList.setAdapter(mAdapter);
//        parent.addView(mList, lp);
//      }
//    });
    
    //Toast.makeText(getActivity(), "List Adapter Updated", Toast.LENGTH_SHORT).show();
    
    if (mListener != null) {
      mListener.notifySheetDataChanged();
    }
  }

  public static Bundle getRandomColorArgs() {
    Bundle args = new Bundle();
    Double r, g, b;
    r = Math.random() * 255;
    g = Math.random() * 255;
    b = Math.random() * 255;
    int color = Color.rgb(r.intValue(), g.intValue(), b.intValue());
    args.putInt(SheetSampleSheetFragment.KEY_COLOR, color);
    return args;
  }
  
}
