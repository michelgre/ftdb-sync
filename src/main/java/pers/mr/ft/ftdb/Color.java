package pers.mr.ft.ftdb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import com.neovisionaries.i18n.LanguageCode;

public class Color {

  private static Map<Integer,Color> colors = new HashMap<>();
  
  private int id;
  private MultilingualLabel label;
  
  public int getId() {
    return id;
  }

  public MultilingualLabel getLabel() {
    return label;
  }

  private Color(int id, MultilingualLabel label) {
    this.id = id;
    this.label = label;
  }
  
  public static void loadAllFromDB(Connection db) {
    try {
      PreparedStatement stmt = db.prepareStatement("SELECT c.id, c.label_id, l.langcode, l.label FROM color c JOIN multilingual_label l ON l.id = c.label_id");
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        int col = 1;
        Integer colorId = rs.getInt(col++);
        Integer labelId = rs.getInt(col++);
        String langCode = rs.getString(col++);
        String label = rs.getString(col++);
        
        Color color = colors.get(colorId);
        MultilingualLabel colorLabel = null;
        if (color!=null) {
          colorLabel = color.label;
        }
        else {
          // Créer le label et la couleur
          colorLabel = new MultilingualLabel(-1, labelId);
          color = new Color(colorId, colorLabel);
          colors.put(colorId, color);
        }
        
        // Ajout du texte au label;
        LanguageCode lang = LanguageCode.valueOf(langCode);
        colorLabel.setLabel(lang, label);
      }
    } catch (SQLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  
  public String getName(LanguageCode lang) {
    return label.get(lang);
  }
  
  public String getFTDBName() {
    return getName(FTDB.getInstance().getDbLang());
  }
  
  public static Color findColor(String text) {
    for (Color c: colors.values()) {
      String s = "("+c.getFTDBName()+")";
      if (text.indexOf(s)>=0) {
        return c;
      }
    }
    return null;
  }
  
  public static Color get(Integer id) {
    return colors.get(id);
  }
  
  public String toString() {
    String colorName = label.get(LanguageCode.en);
    if (colorName==null) {
      colorName = label.get(FTDB.getInstance().getDbLang());
    }
    return "Color("+colorName+")";
  }
}
