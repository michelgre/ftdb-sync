package pers.mr.ft.ftdb.fieldconverter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeFieldConverter implements FieldConverter {
  private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  @Override
  public Object fromFTDB(Object ftdbValue) {
    if (ftdbValue==null) return null;
    try {
      return LocalDateTime.parse((String) ftdbValue, formatter);
    } finally {
    }
  }
}
