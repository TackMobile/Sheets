package com.tackmobile.sheets;

import com.tackmobile.sheets.SimpleSheetFragmentAdapter.SheetDescriptor;

public interface ISheetListener {

  void addSheetFragment(SheetDescriptor descriptor);
  
  void popTopSheetFragment();
  
  void popAllSheets();
  
  void notifySheetDataChanged();
  
}
