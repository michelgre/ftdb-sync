package pers.mr.ft.ftdb;

import org.json.JSONArray;
import org.json.JSONObject;

import pers.mr.ft.ftdb.fieldconverter.DateTimeFieldConverter;
import pers.mr.ft.ftdb.fieldconverter.DoubleFieldConverter;
import pers.mr.ft.ftdb.fieldconverter.FieldConverter;
import pers.mr.ft.ftdb.fieldconverter.IntegerFieldConverter;
import pers.mr.ft.ftdb.fieldconverter.PartNumberFieldConverter;

public enum FTDBField {
  PartId("ticket_id", "id"),
  TypeID("type_id"),
  Weight("ft_weight", "ft_weight", new DoubleFieldConverter()),
  Icon("icon"),
  CreatedUTC("createdUTC", "createdutc", new DateTimeFieldConverter()),
  CreatedByUserName("createdByUserName"),
  Title("title", null),
  Description("description", null),
  FTIcon("ft_icon", "ft_icon", new IntegerFieldConverter()),
  FTCat("ft_cat_all", "ft_cat", new FieldConverter() {
    @Override
    public Object fromFTDB(Object ftdbValue) {
      if (ftdbValue==null) return null;
      
      JSONArray cats = (JSONArray) ftdbValue;
      if (cats.length()>0) {
        String sCat = (String) cats.get(0);
        return Integer.parseInt(sCat);
      }
      return null;
    }
    
  }),
  UUID("ft_variant_uuid"),
  FTCount("ft_count", null, new IntegerFieldConverter()),
  FTPartNumbers("ft_article_nos", null, new PartNumberFieldConverter(), false),
  ;
  
  private String ftdbName;
  private String dbName;
  private FieldConverter fieldConverter = null;
  private boolean automatic = true;
  
  private FTDBField(String ftdbName, String dbName) {
    this(ftdbName, dbName, null, true);
  }

  private FTDBField(String ftdbName, String dbName, FieldConverter fieldConverter) {
    this(ftdbName, dbName, fieldConverter, true);
  }
  
  private FTDBField(String ftdbName, String dbName, FieldConverter fieldConverter, boolean automatic) {
    this.ftdbName = ftdbName;
    this.dbName = dbName;
    this.fieldConverter = fieldConverter;
    this.automatic = automatic;
  }
  
  private FTDBField(String name) {
    this(name, name, null, true);
  }
  
  public String getFtdbName() {
    return ftdbName;
  }
  public String getDbName() {
    return dbName;
  }
  
  public Object get(JSONObject json) {
    Object value = null;
    if (!json.isNull(ftdbName)) {
      value = json.get(ftdbName);
    }
    if (fieldConverter!=null) {
      value = fieldConverter.fromFTDB(value);
    }
    return value;
  }
  
  public void put(JSONObject json, Object value) {
    json.put(ftdbName, value);
  }

  public boolean isAutomatic() {
    return automatic;
  }
}
