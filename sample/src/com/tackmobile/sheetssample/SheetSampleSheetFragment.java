package com.tackmobile.sheetssample;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
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
  
  private static final String TAG = "SheetFragment";
  
  private String[] data = { "just", "a", "simple", "list", "just", "a", "simple", "list", "just", "a", "simple",
      "list", "just", "a", "simple", "list", "just", "a", "simple", "list", "just", "a", "simple", "list" };
  
  private ISheetListener mListener;

  private ListView mList;

  private MyAdapter mAdapter;
  
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
      }
    });

    int viewIndex = args.getInt(SheetSampleMainActivity.KEY_VIEW_INDEX);
    TextView textViewIndex = (TextView) view.findViewById(R.id.text_view_index);
    textViewIndex.setText("View Index : "+viewIndex);
    
    mList = (ListView) view.findViewById(android.R.id.list);
    setListAdapter();
  }
  
  public void setListAdapter() {
    mAdapter = new MyAdapter(getActivity(), android.R.layout.simple_list_item_1, android.R.id.text1, new String[]{"first","set"});
    //mAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, android.R.id.text1, data);
    mList.setAdapter(mAdapter);
    mList.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        Toast.makeText(getActivity(), "List Item Clicked!", Toast.LENGTH_LONG).show();
      }
    });
    Toast.makeText(getActivity(), "List Adapter Set", Toast.LENGTH_SHORT).show();
    mList.setBackgroundColor(Color.GREEN);
  }
  
  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    Handler h = new Handler(getActivity().getMainLooper());
    h.postDelayed(new Runnable() {
    //h.post(new Runnable() {
      @Override
      public void run() {
        mAdapter.update(data);
        Toast.makeText(getActivity(), "List Adapter Updated", Toast.LENGTH_SHORT).show();
      }
    }, 3 * DateUtils.SECOND_IN_MILLIS);
  }
  
  private class MyAdapter extends ArrayAdapter<String> {
    String[] mData;
    public MyAdapter(Context context, int layout, int res, String[] data) {
      super(context, layout, res, data);
      mData = data;
    }
    
    public void update(String[] data) {
      mData = data;
      notifyDataSetChanged();
    }
    
    @Override
    public int getCount() {
      return mData != null ? mData.length : 0;
    }
    
    public String getItem(int position) {
      return mData != null && mData.length > position ? mData[position] : "";
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
