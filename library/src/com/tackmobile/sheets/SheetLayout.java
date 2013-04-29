package com.tackmobile.sheets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.os.ParcelableCompat;
import android.support.v4.os.ParcelableCompatCreatorCallbacks;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewConfigurationCompat;
import android.support.v4.widget.EdgeEffectCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.Interpolator;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.view.ViewHelper;
import com.nineoldandroids.view.ViewPropertyAnimator;

public class SheetLayout extends ViewGroup {

  //
  // Static vars
  //
  private static final String TAG = "SheetLayout";
  
  private static final boolean DEBUG = true;
  
  /**
   * Sentinel value for no current active pointer.
   * Used by {@link #mActivePointerId}.
   */
  private static final int INVALID_POINTER = -1;
  
  private static final int DEFAULT_OFFSCREEN_SHEETS = 1;

  //private static final int MIN_DISTANCE_FOR_FLING = 25; // dp
  
  //private static final int MIN_FLING_VELOCITY = 200; // dp
  
  private static final boolean USE_CACHE = true;

  // If the Sheet is at least this close to its final position, complete the scroll
  // on touch down and let the user interact with the content inside instead of
  // "catching" the flinging pager.
  //private static final int CLOSE_ENOUGH = 2; // dp

  private static final Interpolator sInterpolator = new Interpolator() {
    public float getInterpolation(float t) {
      t -= 1.0f;
      return t * t * t * t * t + 1.0f;
    }
  };
  
  private static final Comparator<SheetInfo> COMPARATOR = new Comparator<SheetLayout.SheetInfo>() {
    @Override
    public int compare(SheetInfo lhs, SheetInfo rhs) {
      return lhs.position - rhs.position;
    }
  };
  
  /**
   * Indicates that the pager is in an idle, settled state. The current page
   * is fully in view and no animation is in progress.
   */
  public static final int SCROLL_STATE_IDLE = 0;

  /**
   * Indicates that the pager is currently being dragged by the user.
   */
  public static final int SCROLL_STATE_DRAGGING = 1;

  /**
   * Indicates that the pager is in the process of settling to a final position.
   */
  public static final int SCROLL_STATE_SETTLING = 2;

  private static final long DEFAULT_ANIM_DURATION = 500;
  
  private static final long POP_DURATION = 500;
  
  //
  // Member vars
  //
  
  private final ArrayList<SheetInfo> mItems = new ArrayList<SheetInfo>();
  
  private FragmentSheetAdapter mAdapter;

  private Parcelable mRestoredAdapterState = null;
  
  private ClassLoader mRestoredClassLoader = null;
  
  private boolean mNeedRestoreFragments;
  
  private SheetObserver mObserver;
  
  private boolean mInLayout;
  
  private boolean mScrollingCacheEnabled;
  
  private boolean mPopulatePending;
  
  private boolean mIsAnimating;
  
  private boolean mIsBeingDragged;
  
  private boolean mIsUnableToDrag;
  
  private int mTouchSlop;
  
  // Motion event positions
  private float mLastMotionX;
  private float mLastMotionY;
  private float mInitialMotionX;

  private int mActivePointerId = INVALID_POINTER;

  /**
   * Determines speed during touch scrolling
   */
  private VelocityTracker mVelocityTracker;
  private int mMaximumVelocity;
  
  private EdgeEffectCompat mLeftEdge;
  private EdgeEffectCompat mRightEdge;

  private boolean mFirstLayout = true;

  private OnSheetChangeListener mOnSheetChangeListener;
  //private OnSheetChangeListener mInternalSheetChangeListener;
  
  private int mScrollState = SCROLL_STATE_IDLE;

  private final Runnable mUpdateSheetsRunnable = new Runnable() {
    @Override
    public void run() {
      // Pop view from lowest view with "shouldPop" = true
      final int clientWidth = getClientWidth();
      int count = getChildCount();
      int index;
      View child;
      LayoutParams lp;
      ArrayList<View> remaining = new ArrayList<View>();
      for (index=0; index<count; index++) {
        child = getChildAt(index);
        
        // ignore views we don't recognize
        if (child == null || !(child.getLayoutParams() instanceof LayoutParams))
          continue;
        
        lp = (LayoutParams) child.getLayoutParams();
        
        // ignore decor views
        if (lp.isDecor)
          continue;
        
        if (lp.shouldPop) {
          ViewPropertyAnimator.animate(child)
            .setInterpolator(sInterpolator)
            .setDuration(POP_DURATION)
            .setListener(mPopAnimationListener)
            .x(clientWidth);
        } else {
          remaining.add(child);
        }
      }

      // Animate remaining sheets
      boolean isTop = true;
      float newX = 0;
      count = remaining.size();
      for (index=count-1; index>=0; index--) {
        child = remaining.get(index);
        if (isTop) {
          newX = clientWidth - child.getMeasuredWidth();
        }

        float currX = ViewHelper.getX(child);
        if (currX != newX) {
          ViewPropertyAnimator.animate(child)
              .setInterpolator(sInterpolator)
              .setDuration(DEFAULT_ANIM_DURATION)
              .x(newX);
        }
        
        if (isTop) {
          newX = 0;
          isTop = false;
        }
      }
    }
  };
  
  private Animator.AnimatorListener mPopAnimationListener = new Animator.AnimatorListener() {
    @Override
    public void onAnimationStart(Animator animation) {
      mPopulatePending = true;
      mIsAnimating = true;
    }
    
    @Override
    public void onAnimationRepeat(Animator animation) {
    }
    
    @Override
    public void onAnimationEnd(Animator animation) {
      if (mAdapter == null) return;
      
      mAdapter.startUpdate(SheetLayout.this);
      
      // Actually remove the views the need to be popped
      final int N = getChildCount();
      LayoutParams lp;
      View child;
      SheetInfo sheetInfo;
      for (int index=0; index<N; index++) {
        child = getChildAt(index);
        sheetInfo = infoForChild(child);
        lp = (LayoutParams) child.getLayoutParams();
        if (lp.shouldPop) {
          mAdapter.destroyItem(SheetLayout.this, sheetInfo.position, sheetInfo.sheetFragment);
          mItems.remove(sheetInfo);
          break;
        }
      }

      mAdapter.finishUpdate(SheetLayout.this);
      
      mPopulatePending = false;
      mIsAnimating = false;
    }
    
    @Override
    public void onAnimationCancel(Animator animation) {
    }
  };

  private int mOffscreenPageLimit = DEFAULT_OFFSCREEN_SHEETS;

  
  public SheetLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  public SheetLayout(Context context) {
    super(context);
    init(context);
  }

  private void init(final Context context) {
    setWillNotDraw(false);
    setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
    setFocusable(true);

    final ViewConfiguration configuration = ViewConfiguration.get(context);
    //final float density = context.getResources().getDisplayMetrics().density;

    mTouchSlop = ViewConfigurationCompat.getScaledPagingTouchSlop(configuration);
    //mMinimumVelocity = (int) (MIN_FLING_VELOCITY * density);
    mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
    mLeftEdge = new EdgeEffectCompat(context);
    mRightEdge = new EdgeEffectCompat(context);

    //mFlingDistance = (int) (MIN_DISTANCE_FOR_FLING * density);
    
    //mDefaultGutterSize = (int) (DEFAULT_GUTTER_SIZE * density);
    
    // TODO: Add accessibility delegate possibly
    // ViewCompat.setAccessibilityDelegate(this, new MyAccessibilityDelegate());
    //if (ViewCompat.getImportantForAccessibility(this)
    //    == ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
    //ViewCompat.setImportantForAccessibility(this,
    //        ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
    
  }

  private void setScrollingCacheEnabled(boolean enabled) {
      if (mScrollingCacheEnabled != enabled) {
          mScrollingCacheEnabled = enabled;
          if (USE_CACHE) {
              final int size = getChildCount();
              for (int i = 0; i < size; ++i) {
                  final View child = getChildAt(i);
                  if (child.getVisibility() != GONE) {
                      child.setDrawingCacheEnabled(enabled);
                  }
              }
          }
      }
  }
  
  public int getOffscreenPageLimit() {
    return mOffscreenPageLimit;
  }

  public void setOffscreenPageLimit(int limit) {
    if (limit < DEFAULT_OFFSCREEN_SHEETS) {
      limit = DEFAULT_OFFSCREEN_SHEETS;
    }
    if (limit != mOffscreenPageLimit) {
      mOffscreenPageLimit = limit;
      populate();
    }
  }
  
  @Override
  protected void onDetachedFromWindow() {
    removeCallbacks(mUpdateSheetsRunnable);
    super.onDetachedFromWindow();
  }
  
  public void setScrollState(int scrollState) {
    if (mScrollState == scrollState)
      return;

    mScrollState = scrollState;

    if (mOnSheetChangeListener != null) {
      mOnSheetChangeListener.onSheetScrollStateChange(mScrollState);
    }
  }

  public void setAdapter(FragmentSheetAdapter adapter) {
    if (mAdapter == adapter)
      return;

    if (mAdapter != null) {
      mAdapter.unregisterDataSetObserver(mObserver);
      mAdapter.startUpdate(this);
      for (int i = 0; i < mItems.size(); i++) {
          final SheetInfo item = mItems.get(i);
          mAdapter.destroyItem(this, item.position, item.sheetFragment);
      }
      mAdapter.finishUpdate(this);
      mItems.clear();
      scrollTo(0, 0);
    }
    
    mAdapter = adapter;
    
    if (mAdapter != null) {
      if (mObserver == null) {
        mObserver = new SheetObserver();
      }
      mAdapter.registerDataSetObserver(mObserver);
      mPopulatePending = false;
      final boolean wasFirstLayout = mFirstLayout;
      mFirstLayout = true;
      if (mRestoredAdapterState != null) {
        mAdapter.restoreState(mRestoredAdapterState, mRestoredClassLoader);
        mRestoredAdapterState = null;
        mRestoredClassLoader = null;
        if (mNeedRestoreFragments) {
          restoreSheetInfoFragments();
        }
        
        requestLayout();
      } if (!wasFirstLayout) {
        populate();
      } else {
        requestLayout();
      }
    }
  }
  
  public FragmentSheetAdapter getAdapter() {
    return mAdapter;
  }

  private int getClientWidth() {
      return getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
  }
  
  public int getCurrentItemPosition() {
    return mAdapter.getCount() - 1;
  }
  
  //
  // Touch
  //
  
  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev) {
    final int action = ev.getAction() & MotionEventCompat.ACTION_MASK;
    
    if (getChildCount() == 0) return false;
    
    if (action == MotionEvent.ACTION_CANCEL || 
        action == MotionEvent.ACTION_UP || 
        (action != MotionEvent.ACTION_DOWN && mIsUnableToDrag)) {
      endDrag();
      return false;
    }
    
    switch (action) {
    case MotionEvent.ACTION_MOVE:
      Log.d(TAG, "onInterceptTouchEvent ACTION_MOVE !!!");
      
      // Determine if we should intercept the move event? (assuming we haven't already) 
      // ACTION_DOWN must have been over the top view, and ACTION_MOVE should be
      // in the right direction to intercept
      
      /*final int activePointerId = mActivePointerId;
      if (activePointerId == INVALID_POINTER) {
          // If we don't have a valid id, the touch down wasn't on content.
          break;
      }
      final int pointerIndex = MotionEventCompat.findPointerIndex(ev, activePointerId);
      final float x = MotionEventCompat.getX(ev, pointerIndex);
      final float dx = x - mLastMotionX;
      final float xDiff = Math.abs(dx);
      final float y = MotionEventCompat.getY(ev, pointerIndex);
      final float yDiff = Math.abs(y - mInitialMotionY);
      if (DEBUG) Log.v(TAG, "Moved x to " + x + "," + y + " diff=" + xDiff + "," + yDiff);

      if (dx != 0 && !isGutterDrag(mLastMotionX, dx) && 
          canScroll(this, false, (int) dx, (int) x, (int) y)) {
          // Nested view has scrollable area under this point. Let it be handled there.
          mLastMotionX = x;
          mLastMotionY = y;
          mIsUnableToDrag = true;
          return false;
      }
      if (xDiff > mTouchSlop && xDiff * 0.5f > yDiff) {
          if (DEBUG) Log.v(TAG, "Starting drag!");
          mIsBeingDragged = true;
          setScrollState(SCROLL_STATE_DRAGGING);
          mLastMotionX = dx > 0 ? mInitialMotionX + mTouchSlop :
                  mInitialMotionX - mTouchSlop;
          mLastMotionY = y;
          setScrollingCacheEnabled(true);
      } else if (yDiff > mTouchSlop) {
          // The finger has moved enough in the vertical
          // direction to be counted as a drag...  abort
          // any attempt to drag horizontally, to work correctly
          // with children that have scrolling containers.
          if (DEBUG) Log.v(TAG, "Starting unable to drag!");
          mIsUnableToDrag = true;
      }
      if (mIsBeingDragged) {
          // Scroll to follow the motion event
          if (performDrag(x)) {
              ViewCompat.postInvalidateOnAnimation(this);
          }
      }*/
      break;
    case MotionEvent.ACTION_DOWN:
      Log.d(TAG, "onInterceptTouchEvent ACTION_DOWN");
      /*
       * Remember location of down touch.
       * According to Google source code, ACTION_DOWN always refers to pointer index 0.
       */
      mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
      mLastMotionX = mInitialMotionX = ev.getX();
      mLastMotionY = ev.getY();
      mIsUnableToDrag = false;
      boolean touchAllowed = thisTouchAllowed(ev);
      Log.d(TAG, "touchAllowed = "+touchAllowed);
      if (touchAllowed) {
        if (mScrollState == SCROLL_STATE_SETTLING) {
          // Let the user 'catch' the pager as it animates.
          mIsBeingDragged = true;
          setScrollState(SCROLL_STATE_DRAGGING);
        } else {
          //completeScroll(false);
          mIsBeingDragged = false;
        }
      }

      if (DEBUG) Log.v(TAG, "Down at " + mLastMotionX + "," + mLastMotionY
              + " mIsBeingDragged=" + mIsBeingDragged
              + " mIsUnableToDrag=" + mIsUnableToDrag);
      break;
    case MotionEventCompat.ACTION_POINTER_UP:
      Log.d(TAG, "onInterceptTouchEvent ACTION_POINTER_UP");
      //onSecondaryPointerUp(ev);
      break;
    }
  
    if (mVelocityTracker == null) {
      mVelocityTracker = VelocityTracker.obtain();
    }
    mVelocityTracker.addMovement(ev);
  
    /*
    * The only time we want to intercept motion events is if we are in the
    * drag mode.
    */
    Log.d(TAG, "onInterceptTouchEvent returning mIsBeingDragged="+mIsBeingDragged);
    return mIsBeingDragged;
  }
  
  private boolean thisTouchAllowed(MotionEvent ev) {
    // must have children
    final int childCount = getChildCount();
    if (childCount < 1 ) return false;
    
    View topSheet = getChildAt(getChildCount() - 1);
    SheetInfo info = infoForChild(topSheet);
    if (info == null || info.sheetFragment == null) return false;   // Can't touch a sheet that doesn't exist yet
    
    float eX = ev.getX();
    float left = ViewHelper.getX(topSheet);
    float right = left + topSheet.getMeasuredWidth();
    Fragment f = info.sheetFragment;
    if (left < eX && eX < right && f instanceof ISheetFragment) {
      return !((ISheetFragment)f).shouldInterceptLayoutMotionEvent(ev);
    } else
      return false; 
  }
  
  @Override
  public boolean onTouchEvent(MotionEvent ev) {
    if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getEdgeFlags() != 0) {
      // Don't handle edge touches immediately -- they may actually belong to one of our
      // descendants.
      return false;
    }

    if (mAdapter == null || mAdapter.getCount() == 0) {
      // Nothing to present or scroll; nothing to touch.
      return false;
    }
    
    if (mIsAnimating) {
      // Don't allow touch/drags during animation
      return false;
    }
    
    if (mVelocityTracker == null) {
      mVelocityTracker = VelocityTracker.obtain();
    }
    mVelocityTracker.addMovement(ev);

    final int action = ev.getAction();
    boolean needsInvalidate = false;
    
    switch (action & MotionEventCompat.ACTION_MASK) {
    case MotionEvent.ACTION_DOWN:
      Log.d(TAG, "onTouchEvent ACTION_DOWN");
      //if (thisTouchAllowed(ev)) {
        mPopulatePending = false;
        populate();
        //mIsBeingDragged = true;
        //setScrollState(SCROLL_STATE_DRAGGING);
  
        // Remember where the motion event started
        mLastMotionX = mInitialMotionX = ev.getX();
        mLastMotionY = ev.getY();
        mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
      //}
      break;
    case MotionEvent.ACTION_MOVE:
      Log.d(TAG, "onTouchEvent ACTION_MOVE");
      if (!mIsBeingDragged) {
        final int pointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
        final float x = MotionEventCompat.getX(ev, pointerIndex);
        final float xDiff = Math.abs(x - mLastMotionX);
        final float y = MotionEventCompat.getY(ev, pointerIndex);
        final float yDiff = Math.abs(y - mLastMotionY);
        
        //if (DEBUG) Log.v(TAG, "Moved x to " + x + "," + y + " diff=" + xDiff + "," + yDiff);
        if (xDiff > mTouchSlop && xDiff > yDiff) {
          if (DEBUG) Log.v(TAG, "Starting drag!");
          mIsBeingDragged = true;
          mLastMotionX = x > mInitialMotionX ? mInitialMotionX + mTouchSlop : mInitialMotionX - mTouchSlop;
          mLastMotionY = y;
          setScrollState(SCROLL_STATE_DRAGGING);
          setScrollingCacheEnabled(true);
        }
      }
      
      // Not else! Note that mIsBeingDragged can be set above.
      if (mIsBeingDragged) {
        // Scroll to follow the motion event
        final int activePointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
        final float x = MotionEventCompat.getX(ev, activePointerIndex);
        needsInvalidate |= performDrag(x);
      }
      break;
    case MotionEvent.ACTION_UP:
      Log.d(TAG, "onTouchEvent ACTION_UP");
      if (mIsBeingDragged) {
        final VelocityTracker velocityTracker = mVelocityTracker;
        velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
        mPopulatePending = true;
        mActivePointerId = INVALID_POINTER;
        endDrag();
        needsInvalidate = mLeftEdge.onRelease() | mRightEdge.onRelease();
      }
      break;
    case MotionEvent.ACTION_CANCEL:
      if (mIsBeingDragged) {
        mActivePointerId = INVALID_POINTER;
        endDrag();
        needsInvalidate = mLeftEdge.onRelease() | mRightEdge.onRelease();
      }
      break;
    case MotionEventCompat.ACTION_POINTER_DOWN:
      Log.d(TAG, "onTouchEvent ACTION_POINTER_DOWN");
      final int index = MotionEventCompat.getActionIndex(ev);
      final float x = MotionEventCompat.getX(ev, index);
      mLastMotionX = x;
      mActivePointerId = MotionEventCompat.getPointerId(ev, index);
      break;
    case MotionEventCompat.ACTION_POINTER_UP:
      Log.d(TAG, "onTouchEvent ACTION_POINTER_UP");
      onSecondaryPointerUp(ev);
      mLastMotionX = MotionEventCompat.getX(ev, MotionEventCompat.findPointerIndex(ev, mActivePointerId));
      break;
    }
    
    if (needsInvalidate) {
      ViewCompat.postInvalidateOnAnimation(this);
    }

    return true;
  }
  
  /**
   * X is a position on the screen, not a delta
   */
  private boolean performDrag(float x) {
    boolean needsInvalidate = false;
    
    //final SheetInfo topItem = mItems.get(mItems.size() - 1); // assume last is top
    final View topChild = getChildAt(getChildCount() - 1);

    final float deltaX = mLastMotionX - x;
    mLastMotionX = x;

    float oldTopX = ViewHelper.getX(topChild);
    float newTopX = oldTopX - deltaX;
    final int width = getClientWidth();
    
    // Bounds apply to topChild X value
    float leftBound = width - topChild.getWidth(); // left bound is when top sheet right edge matches right edge of screen
    float rightBound = width; // right bound is when top sheet is off the screen

    // over-scroll calculations for blue edge effect
    // only visible for leftBound / right edge
    if (newTopX < leftBound) {
      float over = leftBound - newTopX;
      needsInvalidate = mLeftEdge.onPull(Math.abs(over) / width);
      newTopX = leftBound;
    } else if (newTopX > rightBound) {
      newTopX = rightBound;
    }
    
    // Don't lose the rounded component
    mLastMotionX += newTopX - (int) newTopX;
    
    //Log.v(TAG, "performDrag() x:"+x+"\t mLastMotionX:"+mLastMotionX+"\t newTopX:"+newTopX);
    
    // Translate relevant views to new X values
    ViewHelper.setX(topChild, newTopX);
    int next = getChildCount() - 2;
    View prevChild = topChild;
    View nextChild;
    float prevLeft = ViewHelper.getX(prevChild);
    float nextRight;
    float nextLeft;
    int nextWidth;
    while (next >= 0) {
      nextChild = getChildAt(next);
      nextWidth = nextChild.getMeasuredWidth();
      nextLeft = ViewHelper.getX(nextChild);
      nextRight = nextLeft + nextWidth;
      if (prevLeft > nextRight) {
        nextLeft = prevLeft - nextWidth;
        ViewHelper.setX(nextChild, nextLeft);
      } else if (nextLeft > prevLeft - nextWidth) {
        nextLeft = Math.max(0, prevLeft - nextWidth);
        ViewHelper.setX(nextChild, nextLeft);
      } else {
        break;
      }
      prevChild = nextChild;
      prevLeft = nextLeft;
      next--;
    }

    return needsInvalidate;
  }

  private void endDrag() {
    mIsBeingDragged = false;
    mIsUnableToDrag = false;

    if (mVelocityTracker != null) {
      mVelocityTracker.recycle();
      mVelocityTracker = null;
    }

    // Check current item
    final int topPosition = getChildCount() - 1;
    final View topSheet = getChildAt(topPosition);
    final float topX = ViewHelper.getX(topSheet);
    final int clientWidth = getClientWidth();
    final int halfSheetWidth = topSheet.getMeasuredWidth() / 2;
    if (topX + halfSheetWidth > clientWidth && mAdapter != null) {
      mAdapter.popSheetFragment(topPosition);
    } else {
      ViewCompat.postOnAnimation(this, mUpdateSheetsRunnable);
    }
  }

  private void onSecondaryPointerUp(MotionEvent ev) {
    final int pointerIndex = MotionEventCompat.getActionIndex(ev);
    final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
    if (pointerId == mActivePointerId) {
      // This was our active pointer going up. Choose a new
      // active pointer and adjust accordingly.
      final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
      mLastMotionX = MotionEventCompat.getX(ev, newPointerIndex);
      mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
      if (mVelocityTracker != null) {
        mVelocityTracker.clear();
      }
    }
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    // Let the focused view and/or our descendants get the key first
    return super.dispatchKeyEvent(event) || executeKeyEvent(event);
  }

  /**
   * You can call this function yourself to have the scroll view perform
   * scrolling from a key event, just as if the event had been dispatched to
   * it by the view hierarchy.
   *
   * @param event The key event to execute.
   * @return Return true if the event was handled, else false.
   */
  public boolean executeKeyEvent(KeyEvent event) {
    boolean handled = false;
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      switch (event.getKeyCode()) {
      case KeyEvent.KEYCODE_DPAD_LEFT:
        //handled = arrowScroll(FOCUS_LEFT);
        break;
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        //handled = arrowScroll(FOCUS_RIGHT);
        break;
      case KeyEvent.KEYCODE_BACK:
        // Pop top sheet fragment
        if (mAdapter.getCount() > 0) {
          mAdapter.popSheetFragment(mAdapter.getCount() - 1);
          handled = true;
        }
      }
    }
    return handled;
  }

  /**
   * We only want the current page that is being shown to be focusable.
   */
  @Override
  public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
    final int focusableCount = views.size();

    final int descendantFocusability = getDescendantFocusability();

    if (descendantFocusability != FOCUS_BLOCK_DESCENDANTS) {
      final int width = getClientWidth();
      for (int i=getChildCount()-1; i>=0; i--) {
        final View child = getChildAt(i);
        if (child.getVisibility() == VISIBLE &&
            child.getLeft() < width &&
            child.getRight() > 0) {
          child.addFocusables(views, direction, focusableMode);
        }
        if (child.getLeft() <= 0) break;
      }
    }

    // we add ourselves (if focusable) in all cases except for when we are
    // FOCUS_AFTER_DESCENDANTS and there are some descendants focusable. this is
    // to avoid the focus search finding layouts when a more precise search
    // among the focusable children would be more interesting.
    if (descendantFocusability != FOCUS_AFTER_DESCENDANTS ||
    // No focusable descendants
        (focusableCount == views.size())) {
      // Note that we can't call the superclass here, because it will
      // add all views in. So we need to do the same thing View does.
      if (!isFocusable()) {
        return;
      }
      if ((focusableMode & FOCUSABLES_TOUCH_MODE) == FOCUSABLES_TOUCH_MODE && isInTouchMode()
          && !isFocusableInTouchMode()) {
        return;
      }
      if (views != null) {
        views.add(this);
      }
    }
  }
  
  @Override
  public void addTouchables(ArrayList<View> views) {
    // Note that we don't call super.addTouchables(), which means that
    // we don't call View.addTouchables(). This is okay because the
    // SheetLayout is not touchable
    for (int i = 0; i < getChildCount(); i++) {
      final View child = getChildAt(i);
      if (child.getVisibility() == VISIBLE) {
        child.addTouchables(views);
      }
    }
  }
  
  // We want the duration of the page snap animation to be influenced by the
  // distance that
  // the screen has to travel, however, we don't want this duration to be
  // effected in a
  // purely linear fashion. Instead, we use this method to moderate the effect
  // that the distance
  // of travel has on the overall snap duration.
  float distanceInfluenceForSnapDuration(float f) {
    f -= 0.5f; // center the values about 0.
    f *= 0.3f * Math.PI / 2.0f;
    return (float) Math.sin(f);
  }

  SheetInfo addNewItem(int position, int index) {
    SheetInfo sheetInfo = new SheetInfo();
    sheetInfo.position = position;
    sheetInfo.sheetFragment = mAdapter.instantiateItem(this, position);
    sheetInfo.widthFactor = mAdapter.getSheetWidthFactor(position);
    sheetInfo.needsLayout = true;
    if (index < 0 || index >= mItems.size()) {
      mItems.add(sheetInfo);
    } else {
      mItems.add(index, sheetInfo);
    }
    return sheetInfo;
  }
  
  SheetInfo infoForChild(View child) {
    SheetInfo sheetInfo;
    ViewGroup fragView;
    for (int i = 0; i < mItems.size(); i++) {
      sheetInfo = mItems.get(i);
      fragView = (ViewGroup) sheetInfo.sheetFragment.getView();
      if (fragView == child || fragView.getChildAt(0) == child) {
        return sheetInfo;
      }
    }
    return null;
  }

  SheetInfo infoForAnyChild(View child) {
    ViewParent parent;
    while ((parent = child.getParent()) != this) {
      if (parent == null || !(parent instanceof View)) {
        return null;
      }
      child = (View) parent;
    }
    return infoForChild(child);
  }

  SheetInfo infoForPosition(int position) {
    for (int i = 0; i < mItems.size(); i++) {
      SheetInfo sheetInfo = mItems.get(i);
      if (sheetInfo.position == position) {
        return sheetInfo;
      }
    }
    return null;
  }

  boolean restoreSheetInfoFragments() {
    if (mAdapter == null) return false;
    
    for (SheetInfo info : mItems) {
      info.sheetFragment = mAdapter.instantiateItem(this, info.position);
    }
    
    return true;
  }

  /**
   * Removes stale sheet info objects and determines if we need to populate new objects
   */
  void dataSetChanged() {
    // This method only gets called if our observer is attached, so mAdapter is
    // non-null.

    final int adapterCount = mAdapter.getCount();
    final int itemsCount = mItems.size();
    boolean needPopulate = itemsCount < mOffscreenPageLimit * 2 + 1 || itemsCount != adapterCount;
    
    for (int i = 0; i < itemsCount; i++) {
      final SheetInfo sheetInfo = mItems.get(i);
      final int adapterPos = mAdapter.getItemPosition(sheetInfo.sheetFragment);
      
      // Ignore sheets with unchanged positions
      if (adapterPos == FragmentSheetAdapter.POSITION_UNCHANGED)
        continue;

      // Check is sheet was removed
      if (adapterPos == FragmentSheetAdapter.POSITION_NONE) {
        Log.d(TAG, "SheetInfo position not found. Removing view.");
        
        // Delay destroying till after animated off screen
        //mAdapter.destroyItem(this, sheetInfoPos, sheetInfo.sheetFragment);
        View v = sheetInfo.sheetFragment.getView();
        if (v == null) {
          Fragment f = mAdapter.instantiateItem(this, sheetInfo.position);
          v = f.getView();
        }
        LayoutParams lp = (LayoutParams) sheetInfo.sheetFragment.getView().getLayoutParams();
        lp.shouldPop = true;
        
        needPopulate = true;
        continue;
      }

      // Handle positions that have changed
      if (sheetInfo.position != adapterPos) {
        sheetInfo.position = adapterPos;
        needPopulate = true;
      }
    }

    Collections.sort(mItems, COMPARATOR);

    if (needPopulate) {
      requestLayout();
    }
  }

  /**
   * Populates sheet info items and visible fragments
   */
  void populate() {
    if (mAdapter == null) {
      return;
    }

    // Bail now if we are waiting to populate. This is to hold off
    // on creating views from the time the user releases their finger to
    // fling to a new position until we have finished the scroll to
    // that position, avoiding glitches from happening at that point.
    if (mPopulatePending) {
      if (DEBUG) Log.i(TAG, "populate is pending, skipping for now...");
      return;
    }

    // Also, don't populate until we are attached to a window. This is to
    // avoid trying to populate before we have restored our view hierarchy
    // state and conflicting with what is restored.
    if (getWindowToken() == null) {
      return;
    }

    mAdapter.startUpdate(this);
    
    final int adapterTopPosition = getCurrentItemPosition();
    final int pageLimit = mOffscreenPageLimit;
    final int startPos = Math.max(0, adapterTopPosition - pageLimit);
    final int N = mAdapter.getCount();
    
    // Locate the top item or add it if needed.
    int curIndex = 0;
    SheetInfo curItem = null;
    int itemsSize = mItems.size();
    for (curIndex = 0; curIndex < itemsSize; curIndex++) {
      final SheetInfo sheetInfo = mItems.get(curIndex);
      if (sheetInfo.position >= adapterTopPosition) {
        if (sheetInfo.position == adapterTopPosition)
          curItem = sheetInfo;
        break;
      }
    }
    if (curItem == null && N > 0) {
      curItem = addNewItem(adapterTopPosition, curIndex);
    }

    // Fill 3x the available width or up to the number of off-screen
    // pages requested to the left, whichever is larger.
    // If we have no current item we have no work to do.
    if (curItem != null) {
      float extraWidthLeft = 0.f;
      int itemIndex = curIndex - 1;
      SheetInfo sheetInfo = itemIndex >= 0 ? mItems.get(itemIndex) : null;
      final float leftWidthNeeded = 2.f - curItem.widthFactor + (float) getPaddingLeft() / (float) getClientWidth();
      for (int pos = adapterTopPosition - 1; pos >= 0; pos--) {
        if (extraWidthLeft >= leftWidthNeeded && pos < startPos) {
          if (sheetInfo == null) {
            break;
          }
          if (pos == sheetInfo.position) {
            mItems.remove(itemIndex);
            mAdapter.destroyItem(this, pos, sheetInfo.sheetFragment);
            if (DEBUG) Log.i(TAG, "populate() - destroyItem() with pos: " + pos + " view: " + sheetInfo.sheetFragment);
            itemIndex--;
            curIndex--;
            sheetInfo = itemIndex >= 0 ? mItems.get(itemIndex) : null;
          }
        } else if (sheetInfo != null && pos == sheetInfo.position) {
          extraWidthLeft += sheetInfo.widthFactor;
          itemIndex--;
          sheetInfo = itemIndex >= 0 ? mItems.get(itemIndex) : null;
        } else {
          sheetInfo = addNewItem(pos, itemIndex + 1);
          extraWidthLeft += sheetInfo.widthFactor;
          curIndex++;
          sheetInfo = itemIndex >= 0 ? mItems.get(itemIndex) : null;
        }
      }
    }
    
    /*if (DEBUG) {
      Log.i(TAG, "Current page list:");
      for (int i=0; i<mItems.size(); i++) {
        Log.i(TAG, "#" + i + ": page " + mItems.get(i).position);
      }
    }*/

    mAdapter.finishUpdate(this);
    
    // Update Z-index of all views
    SheetInfo sheetInfo;
    final int infoCount = mItems.size();
    for (int i=0; i<infoCount; i++) {
      sheetInfo = mItems.get(i);
      sheetInfo.sheetFragment.getView().bringToFront();
    }

    // Check width ement of current sheets
    // Update LayoutParams as needed.
    final int childCount = getChildCount();
    for (int i=0; i<childCount; i++) {
      final View child = getChildAt(i);
      final LayoutParams lp = (LayoutParams) child.getLayoutParams();
      sheetInfo = infoForChild(child);
      
      if (sheetInfo != null) {
        // Transfer needsLayout request to the LP, then discard
        lp.needsLayout |= sheetInfo.needsLayout;
        sheetInfo.needsLayout = false;

        // lp.childIndex = i;
        if (lp.widthFactor == 0.f) {
          // 0 means re-query the adapter for this, it doesn't have a valid width.
          lp.widthFactor = sheetInfo.widthFactor;
          // lp.position = sheetInfo.position;
        }
      }
    }

    if (hasFocus()) {
      View currentFocused = findFocus();
      sheetInfo = currentFocused != null ? infoForAnyChild(currentFocused) : null;
      if (sheetInfo == null || sheetInfo.position != adapterTopPosition) {
        for (int i = 0; i < getChildCount(); i++) {
          View child = getChildAt(i);
          sheetInfo = infoForChild(child);
          if (sheetInfo != null && sheetInfo.position == adapterTopPosition) {
            if (child.requestFocus(FOCUS_FORWARD)) {
              break;
            }
          }
        }
      }
    }
    
  }

  @Override
  public void addView(View child, int index, ViewGroup.LayoutParams params) {
    if (!checkLayoutParams(params)) {
      params = generateLayoutParams(params);
    }
    final LayoutParams lp = (LayoutParams) params;
    if (mInLayout) { // probably more like "mInMeasure"
      lp.needsMeasure = true;
      lp.needsLayout = true;
      addViewInLayout(child, index, params);
    } else {
      super.addView(child, index, params);
    }
    
    if (USE_CACHE) {
      if (child.getVisibility() != GONE) {
        child.setDrawingCacheEnabled(mScrollingCacheEnabled);
      } else {
        child.setDrawingCacheEnabled(false);
      }
    }
  }
  
  @Override
  protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
    return p instanceof LayoutParams && super.checkLayoutParams(p);
  }
  
  @Override
  public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
    return new LayoutParams(getContext(), attrs);
  }

  @Override
  public void removeView(View view) {
    if (mInLayout) {
      removeViewInLayout(view);
    } else {
      super.removeView(view);
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    // For simple implementation, our internal size is always 0.
    // We depend on the container to specify the layout size of
    // our view. We can't really know what it is since we will be
    // adding and removing different arbitrary views and do not
    // want the layout to change as this happens.
    setMeasuredDimension(getDefaultSize(0, widthMeasureSpec), getDefaultSize(0, heightMeasureSpec));
    
    final int measuredWidth = getMeasuredWidth();
    
    // Children are just made to fill our space.
    int childWidthSize = measuredWidth - getPaddingLeft() - getPaddingRight();
    int childHeightSize = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();

    /*
     * Make sure all children have been properly measured.
     */
    final int heightSpec = MeasureSpec.makeMeasureSpec(childHeightSize, MeasureSpec.EXACTLY);

    // Make sure we have created all fragments that we need to have shown.
    mInLayout = true;
    populate();
    mInLayout = false;

    int size = getChildCount();
    for (int i = 0; i < size; ++i) {
      final View child = getChildAt(i);
      if (child.getVisibility() != GONE) {
        //if (DEBUG)
        //  Log.v(TAG, "Measuring\t #" + i + " " + child.getClass().getSimpleName());

        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if (lp != null) {
          final int widthSpec = MeasureSpec.makeMeasureSpec((int) (childWidthSize * lp.widthFactor), MeasureSpec.EXACTLY);
          child.measure(widthSpec, heightSpec);
          lp.needsMeasure = false;
        }
      }
    }
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);

    // Make sure scroll position is set correctly.
    if (w != oldw) {
      //recomputeScrollPosition(w, oldw, 0, 0);
    }
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    final int count = getChildCount();
    int width = r - l;
    int height = b - t;
    int paddingLeft = getPaddingLeft();
    int paddingTop = getPaddingTop();
    int paddingRight = getPaddingRight();
    int paddingBottom = getPaddingBottom();
    
    // No decor views, no decor view skip.
    
    
    final int clientWidth = width - paddingLeft - paddingRight;
    final int clientHeight = height - paddingTop - paddingBottom;
    final int heightSpec = MeasureSpec.makeMeasureSpec(clientHeight, MeasureSpec.EXACTLY);
    int childTop = paddingTop;
    int childLeft;

    for (int i = 0; i < count; i++) {
      final View child = getChildAt(i);
      if (child.getVisibility() != GONE) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if (infoForChild(child) != null) {
          if (lp.needsMeasure) {
            // This was added during layout and needs measurement.
            // Do it now that we know what we're working with.
            final int widthSpec = MeasureSpec.makeMeasureSpec((int) (clientWidth * lp.widthFactor), MeasureSpec.EXACTLY);
            child.measure(widthSpec, heightSpec);
            childLeft = clientWidth;
            lp.needsMeasure = false;
          } else if (i == count - 1) {
            childLeft = clientWidth;
          } else {
            float currX = ViewHelper.getX(child);
            if (0 <= currX && currX < clientWidth)
              childLeft = (int) currX;
            else
              childLeft = 0;
          }
          
          //if (DEBUG) {
          //  Log.v(TAG, "Positioning\t #" + i + " f=" + sheetInfo.sheetFragment.getClass().getSimpleName() + ":"
          //      + childLeft + "," + childTop + " " + child.getMeasuredWidth() + "x" + child.getMeasuredHeight());
          //}
          
          if (lp.needsLayout) {
            child.layout(childLeft, childTop, childLeft + child.getMeasuredWidth(), childTop + child.getMeasuredHeight());
            lp.needsLayout = false;
          }
        }
      }
    }
    
    ViewCompat.postOnAnimation(this, mUpdateSheetsRunnable);

    mFirstLayout = false;
  }

  @Override
  public void draw(Canvas canvas) {
    super.draw(canvas);
    boolean needsInvalidate = false;

    final int overScrollMode = ViewCompat.getOverScrollMode(this);
    if (overScrollMode == ViewCompat.OVER_SCROLL_ALWAYS
        || (overScrollMode == ViewCompat.OVER_SCROLL_IF_CONTENT_SCROLLS && mAdapter != null && mAdapter.getCount() > 1)) {
      if (!mRightEdge.isFinished()) {
        final int restoreCount = canvas.save();
        final int width = getWidth();
        final int height = getHeight() - getPaddingTop() - getPaddingBottom();

        canvas.rotate(90);
        canvas.translate(-getPaddingTop(), -width); // -(mLastOffset + 1) * width);
        mRightEdge.setSize(height, width);
        needsInvalidate |= mRightEdge.draw(canvas);
        canvas.restoreToCount(restoreCount);
      }
    } else {
      mRightEdge.finish();
    }

    if (needsInvalidate) {
      // Keep animating
      ViewCompat.postInvalidateOnAnimation(this);
    }
  }


  @Override
  public Parcelable onSaveInstanceState() {
    Parcelable superState = super.onSaveInstanceState();
    SavedState ss = new SavedState(superState);
    if (mAdapter != null) {
      ss.adapterState = mAdapter.saveState();
//      if (mItems != null && mItems.size() > 0) {
//        ss.sheetInfos = new SheetInfo[mItems.size()];
//        mItems.toArray(ss.sheetInfos);
//      }
    }
    return ss;
  }

  @Override
  public void onRestoreInstanceState(Parcelable state) {
    if (!(state instanceof SavedState)) {
      super.onRestoreInstanceState(state);
      return;
    }

    SavedState ss = (SavedState) state;
    super.onRestoreInstanceState(ss.getSuperState());

    if (mAdapter != null) {
      mAdapter.restoreState(ss.adapterState, ss.loader);
    } else {
      mRestoredAdapterState = ss.adapterState;
      mRestoredClassLoader = ss.loader;
    }
    
//    mItems.clear();
//    if (ss.sheetInfos != null) {
//      for (int i=0; i<ss.sheetInfos.length; i++) {
//        mItems.add((SheetInfo)ss.sheetInfos[i]);
//      }
//    }
//    
//    mNeedRestoreFragments = restoreSheetInfoFragments();
  }

  //
  // 'Inner's
  //


  /**
   * This is the persistent state that is saved by ViewPager.  Only needed
   * if you are creating a sublass of ViewPager that must save its own
   * state, in which case it should implement a subclass of this which
   * contains that state.
   */
  public static class SavedState extends BaseSavedState {
    Parcelable[] sheetInfos;
    Parcelable adapterState;
    ClassLoader loader;

    public SavedState(Parcelable superState) {
      super(superState);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
      super.writeToParcel(out, flags);
      out.writeParcelableArray(sheetInfos, flags);
      out.writeParcelable(adapterState, flags);
    }

    @Override
    public String toString() {
      return "FragmentPager.SavedState{"+Integer.toHexString(System.identityHashCode(this))+"}";
    }

    public static final Parcelable.Creator<SavedState> CREATOR = ParcelableCompat
        .newCreator(new ParcelableCompatCreatorCallbacks<SavedState>() {
          @Override
          public SavedState createFromParcel(Parcel in, ClassLoader loader) {
            return new SavedState(in, loader);
          }

          @Override
          public SavedState[] newArray(int size) {
            return new SavedState[size];
          }
        });

    SavedState(Parcel in, ClassLoader loader) {
      super(in);
      if (loader == null) {
        loader = getClass().getClassLoader();
      }
      sheetInfos = in.readParcelableArray(loader);
      adapterState = in.readParcelable(loader);
      this.loader = loader;
    }
  }

  private static class SheetInfo implements Parcelable {
    Fragment sheetFragment;
    int position;
    float widthFactor;
    boolean needsLayout;

    /* Parcelable implementation */

    public SheetInfo() {
    }
      
    public SheetInfo(Parcel parcel) {
      position = parcel.readInt();
      widthFactor = parcel.readFloat();
      needsLayout = true;
    }

    @SuppressWarnings("unused")
    public final Parcelable.Creator<SheetInfo> CREATOR = new Parcelable.Creator<SheetInfo>() {
      @Override
      public SheetInfo createFromParcel(Parcel source) {
        return new SheetInfo(source);
      }

      @Override
      public SheetInfo[] newArray(int size) {
        return new SheetInfo[size];
      }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      // Only save position and width factor. 
      // Fragment is restored from adapter and needsLayout is always true after a restore state
      dest.writeInt(position);
      dest.writeFloat(widthFactor);
    }

    @Override
    public int describeContents() {
      return 0;
    }
  }
  
  /**
   * Callback interface for responding to changing state of the selected Sheet.
   */
  public interface OnSheetChangeListener {
    
    public void onSheetAdded();

    /**
     * This method will be invoked when the current Sheet is scrolled, either as part
     * of a programmatically initiated smooth scroll or a user initiated touch scroll.
     *
     * @param position Position index of the first Sheet currently being displayed.
     *                 Sheet position+1 will be visible if positionOffset is nonzero.
     * @param positionOffset Value from [0, 1) indicating the offset from the Sheet at position.
     * @param positionOffsetPixels Value in pixels indicating the offset from position.
     */
    public void onSheetScrolled(int position, float positionOffset, int positionOffsetPixels);

    /**
     * This method will be invoked when a new Sheet becomes selected. Animation is not
     * necessarily complete.
     *
     * @param position Position index of the new selected Sheet.
     */
    public void onSheetSelected(int position);
    
    public void onSheetRemoved();
    
    public void onSheetScrollStateChange(int state);
  }
  
  public static class SimpleOnSheetChangeListener implements OnSheetChangeListener {
    @Override
    public void onSheetAdded() {
    }

    @Override
    public void onSheetScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onSheetSelected(int position) {
    }

    @Override
    public void onSheetRemoved() {
    }
    
    @Override
    public void onSheetScrollStateChange(int state) {
    }
  }
  
  private class SheetObserver extends DataSetObserver {
    @Override
    public void onChanged() {
      dataSetChanged();
    }
    
    @Override
    public void onInvalidated() {
      dataSetChanged();
    }
  }

  private class LayoutParams extends ViewGroup.LayoutParams {


    /**
     * Width as a 0-1 multiplier of the measured pager width
     */
    float widthFactor = 0.f;

    /**
     * true if this view was added during layout and needs to be measured
     * before being positioned.
     */
    boolean needsMeasure;
    boolean needsLayout;

    /**
     * Adapter position for this view
     */
    //int position;

    /**
     * Current child index within the ViewPager that this view occupies
     */
    //int childIndex;
    
    /**
     * 
     */
    public boolean shouldPop = false;
    
    
    public boolean isDecor = false;

    public LayoutParams() {
      super(MATCH_PARENT, MATCH_PARENT);
    }

    public LayoutParams(Context context, AttributeSet attrs) {
      super(context, attrs);
    }
  }

}
