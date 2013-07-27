package com.tackmobile.sheets;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.ViewGroup;

import java.util.ArrayList;

public class SimpleSheetFragmentAdapter extends FragmentSheetAdapter implements ISheetListener {

  private ArrayList<SheetDescriptor> mDescriptors;
  private Context mContext;

  public SimpleSheetFragmentAdapter(Context context, FragmentManager fm) {
    super(fm);
    mContext = context;
  }

  public SheetDescriptor getDescriptor(int position) {
    if (mDescriptors != null && mDescriptors.size() > position)
      return mDescriptors.get(position);
    else
      return null;
  }

  @Override
  public int getCount() {
    return mDescriptors != null ? mDescriptors.size() : 0;
  }

  @Override
  public Fragment getItem(int position) {
    SheetDescriptor info = mDescriptors.get(position);
    return Fragment.instantiate(mContext, info.clazz.getName(), info.args);
  }

  @Override
  public void addSheetFragment(Class<? extends Fragment> clazz, Bundle args) {
    addSheetFragment(new SheetDescriptor(clazz, args));
  }

  @Override
  public void popSheetFragment(int position) {
    if (mDescriptors == null || mDescriptors.size() == 0 || position >= mDescriptors.size())
      return;

    mDescriptors.remove(position);
    removeFragmentAtPosition(position);
    destroySavedStates(position);
  }

  @Override
  public void addSheetFragment(SheetDescriptor descriptor) {
    if (mDescriptors == null) {
      mDescriptors = new ArrayList<SheetDescriptor>();
    }
    mDescriptors.add(descriptor);
    notifyDataSetChanged();
  }

  @Override
  public void popTopSheetFragment() {
    popSheetFragment(getCount() - 1);
  }

  @Override
  public void popAllSheets() {
    mDescriptors.clear();
    removeAllFragments();
  }

  @Override
  public Fragment instantiateItem(ViewGroup container, int position) {
    Fragment fragment = super.instantiateItem(container, position);
    if (fragment instanceof ISheetFragment) {
      ((ISheetFragment) fragment).setSheetListener(this);
    }
    return fragment;
  }

  @Override
  public Parcelable saveState() {
    Bundle state = (Bundle) super.saveState();
    if (mDescriptors != null && mDescriptors.size() > 0) {
      if (state == null) {
        state = new Bundle();
      }
      SheetDescriptor[] descriptors = new SheetDescriptor[mDescriptors.size()];
      mDescriptors.toArray(descriptors);
      state.putParcelableArray(SheetDescriptor.KEY_DESCRIPTORS, descriptors);
    }
    return state;
  }

  @Override
  public void restoreState(Parcelable state, ClassLoader loader) {
    super.restoreState(state, loader);
    Bundle bundle = (Bundle) state;
    if (mDescriptors == null) {
      mDescriptors = new ArrayList<SheetDescriptor>();
    }
    mDescriptors.clear();

    if (bundle != null && bundle.containsKey(SheetDescriptor.KEY_DESCRIPTORS)) {
      Parcelable[] descriptors = bundle.getParcelableArray(SheetDescriptor.KEY_DESCRIPTORS);
      if (descriptors != null) {
        for (int i = 0; i < descriptors.length; i++) {
          mDescriptors.add((SheetDescriptor) descriptors[i]);
        }
      }
    }
  }

}
