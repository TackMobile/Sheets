package com.tackmobile.sheets;

import java.util.ArrayList;

import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.ViewGroup;

public abstract class FragmentSheetAdapter {
  
  private static final String TAG = "FragmentSheetAdapter";
  
  private static final float DEFAULT_SHEET_WIDTH_FACTOR = 0.75f;

  public static final String KEY_SHEET_WIDTH = "com.tackmobile.sheets.KEY_SHEET_WIDTH";
  
  public static final int POSITION_UNCHANGED = -1;
  
  public static final int POSITION_NONE = -2;

  private static final boolean DEBUG = false;

  private DataSetObservable mObservable = new DataSetObservable();

  private final FragmentManager mFragmentManager;

  private FragmentTransaction mCurTransaction = null;

  private ArrayList<Fragment.SavedState> mSavedStates = new ArrayList<Fragment.SavedState>();
  
  private ArrayList<Fragment> mFragments = new ArrayList<Fragment>();
  
  public FragmentSheetAdapter(FragmentManager fm) {
    mFragmentManager = fm;
  }

  public abstract int getCount();

  public abstract Fragment getItem(int position);
  
  public abstract void addSheetFragment(Class<? extends Fragment> clazz, Bundle args);
  
  public abstract void popSheetFragment(int position);
  
  public int getItemId(int position) {
    return position;
  }
  
  public Fragment findTopSheetFragment(ViewGroup container) {
    if (mFragments.size() > 0)
      return mFragments.get(mFragments.size() - 1);
    else
      return null;
  }
  
  /**
   * This method should be called by the application if the data backing this adapter has changed
   * and associated views should update.
   */
  public void notifyDataSetChanged() {
    mObservable.notifyChanged();
  }

  /**
   * Register an observer to receive callbacks related to the adapter's data changing.
   *
   * @param observer The {@link android.database.DataSetObserver} which will receive callbacks.
   */
  public void registerDataSetObserver(DataSetObserver observer) {
    mObservable.registerObserver(observer);
  }

  /**
   * Unregister an observer from callbacks related to the adapter's data changing.
   *
   * @param observer The {@link android.database.DataSetObserver} which will be unregistered.
   */
  public void unregisterDataSetObserver(DataSetObserver observer) {
    mObservable.unregisterObserver(observer);
  }
 
  //
  // Fragment specific methods
  //
  
  public int getItemPosition(Fragment fragment) {
    if (mFragments.contains(fragment))
      return mFragments.indexOf(fragment);
    else
      return POSITION_NONE;
  }

  /**
   * Only call this method once <code>getCount()</code> will return 
   * the correct new value.
   * 
   * Never call this from SheetLayout.
   * 
   * @param position
   */
  public void removeFragmentAtPosition(int position) {
    if (mFragments == null || position >= mFragments.size()) return;
      
    mFragments.set(position, null);
    notifyDataSetChanged();
  }
  
  public void removeAllFragments() {
    mFragments.clear();
    notifyDataSetChanged();
  }
  
  public Fragment instantiateItem(ViewGroup container, int position) {
    // If we already have this item instantiated, there is nothing
    // to do.  This can happen when we are restoring the entire pager
    // from its saved state, where the fragment manager has already
    // taken care of restoring the fragments we previously had instantiated.
    if (mFragments.size() > position) {
      Fragment f = mFragments.get(position);
      if (f != null) {
        return f;
      }
    }

    if (mCurTransaction == null) {
      mCurTransaction = mFragmentManager.beginTransaction();
    }
    
    Fragment fragment = getItem(position);
    if (DEBUG) Log.v(TAG, "Adding item\t #" + position + " f=" + fragment);
    if (mSavedStates.size() > position) {
      Fragment.SavedState fss = mSavedStates.get(position);
      if (fss != null) {
        fragment.setInitialSavedState(fss);
      }
    }
    while (mFragments.size() <= position) {
      mFragments.add(null);
    }
    fragment.setMenuVisibility(false);
    fragment.setUserVisibleHint(false);
    mFragments.set(position, fragment);
    mCurTransaction.add(container.getId(), fragment);

    return fragment;
  }

  public void destroyItem(ViewGroup container, int position, Fragment f) {
    if (mCurTransaction == null) {
      mCurTransaction = mFragmentManager.beginTransaction();
    }
    if (DEBUG) Log.v(TAG, "Removing item #" + position + ": f=" + f);
    while (mSavedStates.size() <= position) {
      mSavedStates.add(null);
    }
    mSavedStates.set(position, mFragmentManager.saveFragmentInstanceState(f));
    if (position < mFragments.size())
      mFragments.set(position, null);

    mCurTransaction.remove(f);
  }

  public void startUpdate(ViewGroup container) {
  }

  public void finishUpdate(ViewGroup container) {
    if (mCurTransaction != null) {
      mCurTransaction.commitAllowingStateLoss();
      mCurTransaction = null;
      mFragmentManager.executePendingTransactions();
    }
  }
  
  /**
   * Returns the proportional width of a given page as a percentage of the
   * ViewPager's measured width from (0.f-1.f]
   *
   * @param position The position of the page requested
   * @return Proportional width for the given page position
   */
  public float getSheetWidthFactor(int position) {
    float sheetWidthFactor = DEFAULT_SHEET_WIDTH_FACTOR;
    Fragment item = getItem(position);
    if (item != null) {
      final Bundle args = item.getArguments();
      if (args != null && args.containsKey(KEY_SHEET_WIDTH)) {
        sheetWidthFactor = args.getFloat(KEY_SHEET_WIDTH);
      }
    }
    return sheetWidthFactor;
  }

  public Parcelable saveState() {
    Bundle state = null;
    if (mSavedStates.size() > 0) {
      state = new Bundle();
      Fragment.SavedState[] fss = new Fragment.SavedState[mSavedStates.size()];
      mSavedStates.toArray(fss);
      state.putParcelableArray("states", fss);
    }
    for (int i = 0; i < mFragments.size(); i++) {
      Fragment f = mFragments.get(i);
      if (f != null) {
        if (state == null) {
          state = new Bundle();
        }
        String key = "f" + i;
        mFragmentManager.putFragment(state, key, f);
      }
    }
    return state;
  }

  public void restoreState(Parcelable state, ClassLoader loader) {
    if (state != null) {
      Bundle bundle = (Bundle) state;
      bundle.setClassLoader(loader);
      Parcelable[] fss = bundle.getParcelableArray("states");
      mSavedStates.clear();
      mFragments.clear();
      if (fss != null) {
        for (int i = 0; i < fss.length; i++) {
          mSavedStates.add((Fragment.SavedState) fss[i]);
        }
      }
      Iterable<String> keys = bundle.keySet();
      for (String key : keys) {
        if (key.startsWith("f")) {
          int index = Integer.parseInt(key.substring(1));
          Fragment f = mFragmentManager.getFragment(bundle, key);
          if (f != null) {
            while (mFragments.size() <= index) {
              mFragments.add(null);
            }
            f.setMenuVisibility(false);
            mFragments.set(index, f);
          } else {
            Log.w(TAG, "Bad fragment at key " + key);
          }
        }
      }
    }
  }
}
