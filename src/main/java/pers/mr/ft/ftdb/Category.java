package pers.mr.ft.ftdb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

public class Category {
  private static Logger logger = Logger.getLogger(Category.class);
  
  private static Map<Integer, Category> loadedCategories = new HashMap<>();
  
  private int id;
  private MultilingualLabel name;
  private Category parentCategory = null;
  private Map<Integer, Category> children = new HashMap<>();
  private boolean inDB = false;
  private boolean changed = false;
  
  public int getId() {
    return id;
  }

  public MultilingualLabel getName() {
    return name;
  }

  public Category getParentCategory() {
    return parentCategory;
  }

  public boolean isChanged() {
    return changed;
  }

  public Category(int id) {
    this.id = id;
  }
  
  public static Category loadFromFTDB(Connection db, int id, String ftdbName, Category parentCategory) {
    
    Category cat = loadFromDB(db, id);
    cat.setData(db, ftdbName, parentCategory);
    
    if (parentCategory!=null) {
      parentCategory.addChild(cat);
    }
    return cat;
  }
  
  public void setData (Connection db, String ftdbName, Category parentCategory) {
    // Mise à jour / création ?
    FTDB ftdb = FTDB.getInstance();
    if (inDB) {
      // Existe déjà
      
      // Mise à jour ?
      if (!this.name.get(ftdb.getDbLang()).equals(ftdbName)) {
        this.name.setLabel(ftdb.getDbLang(), ftdbName);
        changed = true;
      }
      
      int oldParentCatId = 0;
      int parentCatId = 0;
      if (this.parentCategory!=null) {
        oldParentCatId = this.parentCategory.getId();
      }
      if (parentCategory!=null) {
        parentCatId = parentCategory.getId();
      }
      
      if (oldParentCatId!=parentCatId) {
        if (this.parentCategory!=null) {
          this.parentCategory.removeChild(this);
        }
        this.parentCategory = parentCategory;
        if (parentCategory!=null) {
          parentCategory.addChild(this);
        }
        changed = true;
      }
    }
    else {
      // Nouvelle catégorie
      this.name = MultilingualLabel.findInDB(db, id, ftdb.getDbLang(), ftdbName);
      this.parentCategory = parentCategory;
      changed = true;
      
    }
  }
  
  public void addChild(Category cat) {
    if (cat==null) return;
    
    children.put(cat.getId(), cat);
  }
  
  public void removeChild(Category cat) {
    if (cat==null) return;
    children.remove(cat.getId());
  }
  
  public static Category loadFromDB(Connection db, Integer id) {
    // Si on l'a déjà chargée on garde celle cachée
    Category cat = loadedCategories.get(id);
    
    if (cat==null) {
      cat = new Category(id);
      cat.loadFromDB(db);
    }
    return cat;
  }
  
  public void loadFromDB(Connection db) {
    try {
      PreparedStatement stmt = db.prepareStatement("SELECT parent_id, label_id FROM category WHERE id = ?");
      int numParam = 1;
      stmt.setInt(numParam++, id);
      ResultSet result = stmt.executeQuery();
      
      if (result.next()) {
        int numCol = 1;
        Integer parentId = result.getInt(numCol++);
        Integer labelId = result.getInt(numCol++);
        
        this.name = MultilingualLabel.findInDB(db, labelId, null);
        if (parentId!=0) {
          this.parentCategory = loadFromDB(db, parentId);
          this.parentCategory.addChild(this);
        }
        this.inDB = true;
        this.changed = false;
      }
      loadedCategories.put(id,this);
    } catch (SQLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  
  public void save(Connection db, boolean force) {
    if (force || changed) {
      try {
        // Enregistre les labels pour avoir les IDs
        int nameId = 0;
        if (name!=null) {
          name.save(db, null);
          nameId = name.getId();
        }
        int parentId = 0;
        if (parentCategory!=null) {
          parentCategory.save(db, force);
          parentId = parentCategory.getId();
        }
        
        // Enregistre la fiche 
        PreparedStatement stmt;
        if (!inDB) {
          // Pas déjà en base
          stmt = db.prepareStatement("INSERT INTO category "+
              " (id, parent_id, label_id) "+
              " VALUES (?,?,?)");
          int numParam = 1;
          stmt.setInt(numParam++, id);
          stmt.setInt(numParam++, parentId);
          stmt.setInt(numParam++, nameId);
        }
        else {
          // Mise à jour
          stmt = db.prepareStatement("UPDATE category SET "+
              " parent_id = ?, label_id = ?"+
              "WHERE id = ?");
          int numParam = 1;
          stmt.setInt(numParam++, parentId);
          stmt.setInt(numParam++, nameId);
          stmt.setInt(numParam++, id);
        }
        int res = stmt.executeUpdate();
        if (res==1) {
          db.commit();
          inDB = true;
          changed = false;
        }
        else {
          // Pas de ligne créée / maj ==> erreur ???
          logger.error("Pas de ligne mise à jour sur part #"+id);
        }
      } catch (SQLException e) {
        logger.error("Enregistrement de pièce: "+e.getMessage());
      }
    }
    
  }
  
  public static void saveAll(Connection db) {
    for (Category cat: loadedCategories.values()) {
      cat.save(db, false);
    }
  }
  
  public String toString () {
    return getName().toString();
  }
}
