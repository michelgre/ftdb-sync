package pers.mr.ft.ftdb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import com.neovisionaries.i18n.LanguageCode;

public class FTDB {
  private static Logger logger = Logger.getLogger(FTDB.class);
  
  private static String defaultFtdbUrl = "https://ft-datenbank.de";
  private static FTDB theFT = null;
  
  
  private String ftdbUrl = "";
  private LanguageCode dbLang = LanguageCode.de;
  
  private static String readAll(Reader rd) throws IOException {
    StringBuilder sb = new StringBuilder();
    int cp;
    while ((cp = rd.read()) != -1) {
      sb.append((char) cp);
    }
    return sb.toString();
  }

  public String getUrl() {
    return ftdbUrl;
  }
  
  public JSONObject readJsonFromUrl(String path) throws IOException, JSONException {
    String url = ftdbUrl + path;
    InputStream is = new URL(url).openStream();
    try {
      BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
      String jsonText = readAll(rd);
      JSONObject json = new JSONObject(jsonText);
      return json;
    } finally {
      is.close();
    }
  }
  
  public FTDB(String ftdbUrl) {
    this.ftdbUrl = ftdbUrl;
  }
  
  public static FTDB createInstance (String ftdbUrl) {
    logger.debug("Creation Base FTDB "+ftdbUrl);
    theFT = new FTDB(ftdbUrl);
    return theFT;
  }
  
  public static FTDB getInstance() {
    if (theFT==null) {
      createInstance(defaultFtdbUrl);
    }
    return theFT;
  }

  public LanguageCode getDbLang() {
    return dbLang;
  }
}
