package pers.mr.ft.ftdb.fieldconverter;

import java.util.LinkedList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import pers.mr.ft.ftdb.ElementStatus;
import pers.mr.ft.ftdb.PartNumber;

public class PartNumberFieldConverter implements FieldConverter {

  @Override
  public Object fromFTDB(Object ftdbValue) {
    List<PartNumber> partNumbers = new LinkedList<>();
    if (ftdbValue==null) {
      return partNumbers;
    }
    String strValue = (String) ftdbValue;
    JSONArray jsonNumbers = new JSONArray(strValue);
    for (int i=0; i<jsonNumbers.length();i++) {
      JSONArray numberInfo = jsonNumbers.getJSONArray(i);
      String year = null;
      String number = null;
      if (!numberInfo.isNull(0)) {
        year = numberInfo.getString(0);
      }
      if (!numberInfo.isNull(1)) {
        number = numberInfo.getString(1);
      }
      partNumbers.add(new PartNumber(null, year, number, ElementStatus.Added));
    }
    
    return partNumbers;
  }

}
