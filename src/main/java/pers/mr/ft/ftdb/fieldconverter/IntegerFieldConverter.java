package pers.mr.ft.ftdb.fieldconverter;

public class IntegerFieldConverter implements FieldConverter {

  @Override
  public Object fromFTDB(Object ftdbValue) {
    if (ftdbValue==null) return null;
    if (ftdbValue instanceof Integer) {
      return ftdbValue;
    }
    Integer value = Integer.parseInt((String) ftdbValue);
    return value;
  }

}
