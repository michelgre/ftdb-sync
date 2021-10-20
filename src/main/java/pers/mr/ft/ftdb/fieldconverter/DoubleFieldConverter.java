package pers.mr.ft.ftdb.fieldconverter;

public class DoubleFieldConverter implements FieldConverter {

  @Override
  public Object fromFTDB(Object ftdbValue) {
    if (ftdbValue==null) return null;
    Double value = Double.parseDouble((String) ftdbValue);
    return value;
  }

}
