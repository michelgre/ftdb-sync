package pers.mr.ft.ftdb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import com.neovisionaries.i18n.LanguageCode;

public class MultilingualLabel {
  private static Logger logger = Logger.getLogger(MultilingualLabel.class);
  private static Map<Integer, MultilingualLabel> loadedLabels = new HashMap<>();
  
  private Integer id;
  private Integer parentId;
  private Map<LanguageCode, String> localizedLabels = new HashMap<>();
  private Set<LanguageCode> updatedLanguages = new HashSet<>();
  
  public Integer getId() {
    return id;
  }

  public MultilingualLabel(Integer parentId, Integer id) {
    this.parentId = parentId;
    this.id = id;
  }

  private MultilingualLabel setLabel(LanguageCode lang, String label, boolean isUpdate) {
    if (label!=null) {
      String oldLabel = localizedLabels.get(lang);
      if (!label.equals(oldLabel)) {
        localizedLabels.put(lang, label);
        if (isUpdate) {
          updatedLanguages.add(lang);
        }
      }
    }
    else {
      // Suppression d'un label ??? Non inplémenté
    }
    return this;
  }
  
  public MultilingualLabel setLabel(LanguageCode lang, String label) {
    return setLabel(lang, label, true);
  }
  
  /*
   * Recherche d'un label en base par son utilisation (id de l'item parent) et son libellé
   */
  public static MultilingualLabel findInDB(Connection db, Integer parentId, LanguageCode lang, String label) {
    PreparedStatement stmt = null;
    MultilingualLabel mlLabel = null;
    try {
      stmt = db.prepareStatement("SELECT id FROM Multilingual_Label WHERE parent_id = ? AND langcode = ? AND label = ?");
      int numParam = 1;
      stmt.setInt(numParam++, parentId);
      stmt.setString(numParam++, lang.toString());
      stmt.setString(numParam++, label);
      ResultSet result = stmt.executeQuery();
      while (result.next()) {
        Integer id = result.getInt(1);
        mlLabel = loadedLabels.get(id);
        if (mlLabel==null) {
          mlLabel = new MultilingualLabel(parentId, id);
          loadedLabels.put(id, mlLabel);
        }
        mlLabel.setLabel(lang, label, false); // Chargement depuis la base: ce n'est pas un update
        break;
      }
      result.close();
      if (mlLabel==null) {
        // n'existe pas
        return new MultilingualLabel(parentId, 0).setLabel(lang, label); // Nouveau label
      }
      
      return mlLabel;
    } catch (SQLException e) {
      logger.error(e.getMessage());
    } finally {
    }
    return null;
  }
  
  /*
   * Charge un label multilingue par son id. Si la langue n'est pas fournie on les charge toutes
   */
  public static MultilingualLabel findInDB(Connection db, Integer id, LanguageCode lang) {
    PreparedStatement stmt = null;
    MultilingualLabel mlLabel = null;
    try {
      if (lang!=null) {
        stmt = db.prepareStatement("SELECT parent_id, langcode, label FROM Multilingual_Label WHERE id = ? AND langcode = ?");
        int numParam = 1;
        stmt.setInt(numParam++, id);
        stmt.setString(numParam++, lang.toString());
      }
      else {
        stmt = db.prepareStatement("SELECT parent_id, langcode, label FROM Multilingual_Label WHERE id = ?");
        int numParam = 1;
        stmt.setInt(numParam++, id);
      }
      ResultSet result = stmt.executeQuery();
      
      while (result.next()) {
        int numCol = 1;
        Integer parentId = result.getInt(numCol++);
        String langName = result.getString(numCol++);
        String label = result.getString(numCol++);
        lang = LanguageCode.valueOf(langName);
        mlLabel = loadedLabels.get(id);
        if (mlLabel==null) {
          mlLabel = new MultilingualLabel(parentId, id);
          loadedLabels.put(id, mlLabel);
        }
        mlLabel.setLabel(lang, label);
      }
      result.close();
      return mlLabel;
    } catch (SQLException e) {
      logger.error(e.getMessage());
    } finally {
    }
    return null;
  }
  
  /*
   * Enregistrement en base, éventuellement partiel si lang est fourni
   */
  public void save(Connection db, LanguageCode lang) {
    PreparedStatement stmt = null;

    // Il faut qu'il y ait au moins 1 langue
    if (localizedLabels.size()==0) {
      return;
    }
    
    // Si la langue est forcée il faut avoir cette langue
    if (lang!=null && !localizedLabels.containsKey(lang)) {
      return;
    }
    
    for (LanguageCode oneLang : localizedLabels.keySet()) {
      try {
        if (lang!=null && lang!=oneLang) {
          // Pas la bonne langue
          continue;
        }
        
        if (id==0) {
          // Nouveau label
          
          stmt = db.prepareStatement("INSERT INTO Multilingual_Label (parent_id, langcode, label) VALUES (?,?,?)",
              Statement.RETURN_GENERATED_KEYS);
          int numParam = 1;
          stmt.setInt(numParam++, parentId);
          stmt.setString(numParam++, oneLang.toString());
          stmt.setString(numParam++, localizedLabels.get(oneLang));
          int rowCount = stmt.executeUpdate();
          if (rowCount==0) {
            logger.error("Creation de label multilingue impossible: "+parentId+", "+oneLang.toString()+", "+localizedLabels.get(oneLang));
          }
          else {
            ResultSet generatedKeys = stmt.getGeneratedKeys();
            if (generatedKeys.next()) {
              int id = generatedKeys.getInt(1);
              this.id = id;
            }
          }
        }
        else {
          // On a l'id de label mais il faut peut etre creer/màj les langues : vérifier si le update touche des lignes
          // et sinon les créer
          stmt = db.prepareStatement("UPDATE Multilingual_Label SET label = ?, version = version + 1 WHERE id = ? AND langcode = ?");
          int numParam = 1;
          stmt.setString(numParam++, localizedLabels.get(oneLang));
          stmt.setInt(numParam++, id);
          stmt.setString(numParam++, oneLang.toString());
          int rowCount = stmt.executeUpdate();
          if (rowCount==0) {
            // Pas de ligne à màj, la créer
            stmt = db.prepareStatement("INSERT INTO Multilingual_Label (id,parent_id, langcode, label) VALUES (?,?,?,?)");
            numParam = 1;
            stmt.setInt(numParam++, id);
            stmt.setInt(numParam++, parentId);
            stmt.setString(numParam++, oneLang.toString());
            stmt.setString(numParam++, localizedLabels.get(oneLang));
            rowCount = stmt.executeUpdate();
            if (rowCount==0) {
              logger.error("Creation de label multilingue impossible: "+id+", "+parentId+", "+oneLang.toString()+", "+localizedLabels.get(oneLang));
            }
          }
        }
      } catch (SQLException e) {
        logger.error(e.getMessage());
      } finally {
        
      }
    }
    try {
      db.commit();
    } catch (SQLException e) {
      logger.error(e.getMessage());
    }
  }
  
  public String get(LanguageCode lang) {
    return localizedLabels.get(lang);
  }
  
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append("MultilingualLabel[");
    sb.append(id);
    for (Entry<LanguageCode,String> e: localizedLabels.entrySet()) {
      sb.append(",");
      sb.append(e.getKey().toString());
      sb.append(":");
      sb.append(e.getValue());
    }
    sb.append("]");
    return sb.toString();
  }
  
  
}
