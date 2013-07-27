package com.tackmobile.sheets;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.app.Fragment;

/**
 * SheetDescriptor
 * Created on 6/24/13.
 *
 * @author Joshua Jamison
 */
public class SheetDescriptor implements Parcelable {

  public static final String KEY_DESCRIPTORS = "descriptors";
  public static final String KEY_DESCRIPTOR = "descriptor";

  public Class<? extends Fragment> clazz;
  public Bundle args;

  public SheetDescriptor(Class<? extends Fragment> clazz, Bundle args) {
    this.clazz = clazz;
    this.args = args;
  }

  /* Parcelable implementation */

  public static final Parcelable.Creator<SheetDescriptor> CREATOR = new Parcelable.Creator<SheetDescriptor>() {
    @Override
    public SheetDescriptor createFromParcel(Parcel source) {
      return new SheetDescriptor(source);
    }

    @Override
    public SheetDescriptor[] newArray(int size) {
      return new SheetDescriptor[size];
    }
  };

  public SheetDescriptor(Parcel parcel) {
    String className = parcel.readString();
    // Interpret class
    ClassLoader cl = getClass().getClassLoader();
    Class<?> clazz = null;
    try {
      clazz = cl.loadClass(className);
      if (clazz != null) {
        this.clazz = clazz.asSubclass(Fragment.class);
      }
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      return;
    }

    args = parcel.readBundle();
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(clazz.getCanonicalName());
    dest.writeBundle(args);
  }

  @Override
  public int describeContents() {
    return 0;
  }
}
