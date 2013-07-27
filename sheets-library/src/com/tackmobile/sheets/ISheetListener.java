package com.tackmobile.sheets;

public interface ISheetListener {

  void addSheetFragment(SheetDescriptor descriptor);
  
  void popTopSheetFragment();
  
  void popAllSheets();

}
