package com.tackmobile.sheetssample;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ListFragment;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.tackmobile.sheets.ISheetFragment;
import com.tackmobile.sheets.ISheetListener;
import com.tackmobile.sheets.SheetDescriptor;

public class SheetSampleSheetFragment extends ListFragment implements ISheetFragment {

  public static final String KEY_COLOR = "keyColor";

  String[] data = { "just", "a", "simple", "list", "just", "a", "simple", "list", "just", "a", "simple", "list",
      "just", "a", "simple", "list", "just", "a", "simple", "list", "just", "a", "simple", "list" };

  private ISheetListener mListener;

  private MyAdapter mAdapter;

  private TextView mTextListCount;

  private DataSetObserver mObserver;

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
        SheetDescriptor d = new SheetDescriptor(SheetSampleSheetFragment.class, getRandomColorArgs());
        if (mListener != null)
          mListener.addSheetFragment(d);
      }
    });

    view.findViewById(R.id.btn_pop_sheet).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (mListener != null)
          mListener.popTopSheetFragment();
      }
    });

    view.findViewById(R.id.btn_pop_all_sheets).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (mListener != null)
          mListener.popAllSheets();
      }
    });

    int viewIndex = args.getInt(SheetSampleMainActivity.KEY_VIEW_INDEX);
    TextView textViewIndex = (TextView) view.findViewById(R.id.text_view_index);
    textViewIndex.setText("View Index : "+viewIndex);

    setListAdapter();

    mTextListCount = (TextView) view.findViewById(R.id.text_list_count);
    mTextListCount.setText("List count: "+getListAdapter().getCount());
  }

  @Override
  public void onResume() {
    super.onResume();
    mObserver = new DataSetObserver() {
      @Override
      public void onChanged() {
        mTextListCount.setText("List count: "+getListAdapter().getCount());
      }
    };
    getListAdapter().registerDataSetObserver(mObserver);

  }

  @Override
  public void onPause() {
    super.onPause();
    getListAdapter().unregisterDataSetObserver(mObserver);
    mObserver = null;
  }

  public void setListAdapter() {
    mAdapter = new MyAdapter(getActivity(), android.R.layout.simple_list_item_1, android.R.id.text1, new String[]{"first","set"});
    setListAdapter(mAdapter);
    getListView().setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> listview, View view, int position, long id) {
        Toast.makeText(getActivity(), "List Item Clicked!", Toast.LENGTH_LONG).show();
      }
    });
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    Handler h = new Handler(getActivity().getMainLooper());
    h.postDelayed(new Runnable() {
      @Override
      public void run() {
        updateData();
      }
    }, 3 * DateUtils.SECOND_IN_MILLIS);
  }

  void updateData() {
    mAdapter.update(data);

    if (isResumed()) {
      final View view = getView();

      Bundle randomColorArgs = getRandomColorArgs();
      int color = randomColorArgs.getInt(KEY_COLOR);
      view.setBackgroundColor(color);

      mTextListCount.setText("List count: "+getListAdapter().getCount());
    }
  }

  class MyAdapter extends ArrayAdapter<String> {
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

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      return super.getView(position, convertView, parent);
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
