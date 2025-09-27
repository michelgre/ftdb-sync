package pers.mr.ft.ftdb;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Part {
  private static Logger logger = Logger.getLogger(Part.class);
  
  private static Map<Integer,Part> loadedFromFTDB = new HashMap<>();
  private static String tablename = "part";
  
  private Map<FTDBField,Object> data = new HashMap<>();
  private Integer partId = 0;
  private MultilingualLabel titleLabel = null;
  private MultilingualLabel descriptionLabel = null;
  private boolean inDB = false;
  private boolean changed = false;
  private Map<Integer,PartCount> content = new HashMap<>();
  private boolean kit = false;
  private List<Category> categories = new LinkedList<>();
  private List<PartNumber> partNumbers = new LinkedList<>();
  private Color color = null;
  
  public boolean isKit() {
    return kit;
  }

  public Part(Integer partId) {
    this.partId = partId;
  }
  
  public String getTablename() {
    return tablename;
  }
  
  public static Part loadFromFTDB(Connection db, Integer partId, boolean withContent) throws FTDBException {
    FTDB ftdb = FTDB.getInstance();
    
    // On ne recharge pas la pièce si on la déjà chargée depuis FTDB dans cette session
    Part part = loadedFromFTDB.get(partId);
    if (part==null) {
      // Pas déjà chargé depuis FTDB
      try {
        logger.info(""+loadedFromFTDB.size()+" - Charge Part "+partId);
        JSONObject jsonPart = ftdb.readJsonFromUrl("/api/ticket/"+partId).getJSONObject("results");
        part = new Part(db, jsonPart);
        loadedFromFTDB.put(partId, part);
        if (withContent && part!=null && part.isKit()) {
          part.loadContentFromFTDB(db, withContent); // Si on charge le contenu on le fait de façon récursive, pour l'instant
        }
      } catch (JSONException | IOException e) {
        logger.error(e.getMessage());
        throw new FTDBException("Chargement d'une pièce: "+e.getMessage(), e);
      }
    }
    
    return part;
  }
  
  public static List<PartCount> loadPaginagedPartListFromFTDB(Connection db, String partsPath, boolean withCount, boolean withContent) {
    FTDB ftdb = FTDB.getInstance();
    List<PartCount> partCounts = new LinkedList<>();
    try {
      JSONObject partList = ftdb.readJsonFromUrl(partsPath);
      int totalPages = 1;
      try {
        totalPages = partList.getInt("cPages");
      } catch(JSONException e) {
        logger.debug(e.getMessage()); // Il y a des kits sans contenu
        return partCounts;
      }
      int numPage = 1;
      while (numPage<=totalPages) {
        if (numPage > 1) { // 1e page déjà lue
          String sep = "&";
          if (!partsPath.contains("?")) {
            sep = "?"; 
          }
          partList = ftdb.readJsonFromUrl(partsPath+sep+"page="+numPage);
        }
        JSONArray jsonParts = partList.getJSONArray("results");
        for (int iPart = 0; iPart < jsonParts.length(); iPart++) {
          JSONObject json = jsonParts.getJSONObject(iPart);
          
          // L'item json n'est pas complet dans une demande de liste d'items
          // Il faut relancer une requête sur cet item.
          Integer partId = (Integer) json.get(FTDBField.PartId.getFtdbName());
          Integer count = 1;
          if (withCount) {
            count = (Integer) FTDBField.FTCount.get(json);
          }
          Part part;
          try {
            part = Part.loadFromFTDB(db, partId, withContent);
            part.save(db, false);
            partCounts.add(new PartCount(part, count));
          } catch (FTDBException e) {
            logger.error("Chargement de la pièce "+partId+" impossible");
          }
          
        }
        numPage++;
      }
    } catch (JSONException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return partCounts;
  }
  
  public void loadContentFromFTDB(Connection db, boolean withSubContent) throws FTDBException {
    List<PartCount> partCounts = loadPaginagedPartListFromFTDB(db, "/api/ft-partslist/"+partId, true, withSubContent);
    
    Map<Integer,PartCount> newParts = new HashMap<>();
    for (PartCount partCount: partCounts) {
      Integer pieceId = partCount.getPart().getId();
      PartCount currentPartCount = content.get(pieceId);
      if (currentPartCount==null) {
        partCount.setStatus(ElementStatus.Added);// Nouveau dans FTDB
        content.put(pieceId, partCount);
      }
      else {
        currentPartCount.setCount(partCount.getCount()); // Mise à jour éventuelle si count change
      }
      newParts.put(pieceId, partCount); // Pour chercher ensuite celles à supprimer
    }
    for (PartCount partCount: content.values()) {
      Integer partId = partCount.getPart().getId();
      if (!newParts.containsKey(partId)) {
        // Ancienne pièce à supprimer
        partCount.setStatus(ElementStatus.Removed);
      }
    }
  }
  
  public Part(Connection db, JSONObject json) {
    
    // On recherche l'objet en base si possible pour vérifier les changements
    this.partId = (Integer) json.get(FTDBField.PartId.getFtdbName());
    loadFromDB(db);
    
    // A faire avant le traitement des champs JSON car on modifie éventuellement le titre
    // Titre => multilingue
    String ftdbTitle = (String) FTDBField.Title.get(json);
    
    // Couleur (extraite du label, si possible)
    Color ftdbColor = Color.findColor(ftdbTitle);
    if (color!=ftdbColor) {
      color = ftdbColor;
      changed = true;
    }
    
    // Si on a une couleur on enlève l'info du label
    if (color!=null) {
      String toRemove = "(" + color.getFTDBName() + ")";
      ftdbTitle = ftdbTitle.replace(toRemove, "");
      ftdbTitle = ftdbTitle.trim();
      // Certaines couleurs ont un blanc à la fin (chrom-farben )
      toRemove = "(" + color.getFTDBName() + " )";
      ftdbTitle = ftdbTitle.replace(toRemove, "");
      ftdbTitle = ftdbTitle.trim();
      FTDBField.Title.put(json, ftdbTitle);
    }
    
    for (FTDBField field: FTDBField.values()) {
      if (field.getFtdbName()!=null && field.isAutomatic()) {
        try {
          Object value = field.get(json);
          Object prevValue = data.get(field);
          if ((prevValue==null && value!=null) || (prevValue!=null && !prevValue.equals(value))) {
            data.put(field, value);
            this.changed = true;
          }
        } catch (JSONException e) {
          // Valeur non présente
        }
      }
    }
    
    // Cas spéciaux: labels multilingues, catégorie,...
    FTDB ftdb = FTDB.getInstance();
    
    // Recherche du label :
    //  - s'il est déjà chargé on regarde s'il faut le mettre à jour
    //  - sinon on le recherche en base et/ou on le crée
    if (ftdbTitle!=null) {
      if (titleLabel==null) {
        // Label pas encore créé/chargé
        this.titleLabel = MultilingualLabel.findInDB(db, partId, ftdb.getDbLang(), ftdbTitle);
      }
      else {
        if (!ftdbTitle.equals(titleLabel.get(ftdb.getDbLang()))) {
          // Label changé
          titleLabel.setLabel(ftdb.getDbLang(), ftdbTitle);
          changed = true;
        }
      }
    }
    
    //if (ftdbTitle!=null && (titleLabel==null || !ftdbTitle.equals(titleLabel.get(ftdb.getDbLang())))) {
    //  this.titleLabel = MultilingualLabel.findInDB(db, partId, ftdb.getDbLang(), ftdbTitle);
    //}
    
    // Description => multilingue
    String ftdbDescription = (String) FTDBField.Description.get(json);
    if (ftdbDescription!=null) {
      if (descriptionLabel==null) {
        // Label pas encore créé/chargé
        this.descriptionLabel = MultilingualLabel.findInDB(db, partId, ftdb.getDbLang(), ftdbDescription);
      }
      else {
        if (!ftdbDescription.equals(descriptionLabel.get(ftdb.getDbLang()))) {
          // Label changé
          descriptionLabel.setLabel(ftdb.getDbLang(), ftdbDescription);
          changed = true;
        }
      }
    }
    
    // Hiérarchie de catégories : on en profite pour récupérer les informations sur les catégories
    // Les noms sont récupérables dans le champ: ft_cat_all_formatted": "Baukästen &gt; Einstieg &gt; 3-6 + modell &gt; 3"
    // L'ordre est inverse du tableau de codes de catégories.
    JSONArray cats = json.getJSONArray(FTDBField.FTCat.getFtdbName());
    String htmlFormattedCategories = json.getString("ft_cat_all_formatted");
    String sep = ""+(char) 160 + "&gt; ";
    String[] categoryLabels = htmlFormattedCategories.split(sep);
    
    // Parcourir le tableau de codes de catégories en commençant par le dernier qui est le plus général, et le premier
    // dans la chaine de labels
    Category parentCategory = null;
    int nbCategories = cats.length();
    for (int i = 0; i<nbCategories; i++) {
      String catLabel = categoryLabels[i];
      String sCat = (String) cats.get(nbCategories - 1 - i);
      Integer catId = Integer.parseInt(sCat);
      
      // Si une des catégories est un KIT on note qu'il y a un contenu (TODO: sera sans doute généralisé)
      if (catId==CategoryEnum.ConstructionKit.getCatId()) {
        this.kit = true;
      }
      Category cat = Category.loadFromFTDB(db, catId, catLabel, parentCategory);
      categories.add(cat);
      parentCategory = cat;
    }
    
    // Part numbers
    @SuppressWarnings("unchecked")
    List<PartNumber> ftdbPartNumbers = (List<PartNumber>) FTDBField.FTPartNumbers.get(json);
    List<PartNumber> newPartNumbers = new LinkedList<PartNumber>();
    
    for (PartNumber pn: ftdbPartNumbers) {
      pn.setPart(this);
      boolean found = false;
      for (PartNumber oldPn: partNumbers) {
        if (oldPn.sameYear(pn)) {
          found = true;
          oldPn.setNumber(pn.getNumber());
          if (oldPn.getStatus()==ElementStatus.Updated) {
            changed = true;
          }
          newPartNumbers.add(oldPn);
          partNumbers.remove(oldPn);
          break;
        }
      }
      if (!found) {
        pn.setStatus(ElementStatus.Added);
        newPartNumbers.add(pn);
        changed = true;
      }
    }
    for (PartNumber pn: partNumbers) {
      pn.setStatus(ElementStatus.Removed);
      newPartNumbers.add(pn);
      changed = true;
    }
    partNumbers = newPartNumbers;
    
  }
  
  public Integer getId() {
    return (Integer) data.get(FTDBField.PartId);
  }
  
  public static Part loadFromDB(Connection db, Integer id) {
    Part part = new Part(id);
    part.loadFromDB(db);
    return part;
  }
  
  public void loadFromDB(Connection db) {
    FTDB ftdb = FTDB.getInstance();
    data.clear();
    titleLabel = null;
    descriptionLabel = null;
    data.put(FTDBField.PartId, partId);
    
    try {
      PreparedStatement stmt = db.prepareStatement("SELECT type_id, ft_weight, icon, createdUTC, createdByUserName, title_id, description_id, ft_icon, ft_cat, ft_variant_uuid, color_id FROM " + getTablename() + " WHERE id = ?");
      int numParam = 1;
      stmt.setInt(numParam++, partId);
      ResultSet result = stmt.executeQuery();
      
      if (result.next()) {
        int numCol = 1;
        data.put(FTDBField.TypeID, result.getInt(numCol++));
        data.put(FTDBField.Weight, result.getObject(numCol++));
        data.put(FTDBField.Icon, result.getString(numCol++));
        Timestamp ts = result.getTimestamp(numCol++);
        if (ts!=null) {
          data.put(FTDBField.CreatedUTC, ts.toLocalDateTime());
        }
        data.put(FTDBField.CreatedByUserName, result.getString(numCol++));
        int titleId = result.getInt(numCol++);
        int descriptionId = result.getInt(numCol++);
        data.put(FTDBField.FTIcon, result.getInt(numCol++));
        data.put(FTDBField.FTCat, result.getInt(numCol++));
        data.put(FTDBField.UUID, result.getString(numCol++));
        int colorId = result.getInt(numCol++);
        result.close();
        
        this.titleLabel = MultilingualLabel.findInDB(db, titleId, null);
        this.descriptionLabel = MultilingualLabel.findInDB(db, descriptionId, null);
        
        if (titleLabel!=null) {
          data.put(FTDBField.Title, titleLabel.get(ftdb.getDbLang()));
        }
        if (descriptionLabel!=null) {
          data.put(FTDBField.Description, descriptionLabel.get(ftdb.getDbLang()));
        }
        
        // Contenu
        content.clear();
        stmt = db.prepareStatement("SELECT part_id, count, ftdb_count FROM " + getTablename() + "_contains WHERE container_id = ?");
        numParam = 1;
        stmt.setInt(numParam++, partId);
        result = stmt.executeQuery();
        while (result.next()) {
          numCol = 1;
          Integer partId = result.getInt(numCol++);
          Integer count = result.getInt(numCol++);
          Integer ftdbCount = (Integer) result.getObject(numCol++);
          Part piece = Part.loadFromDB(db, partId);
          content.put(partId, new PartCount(piece, count, ftdbCount, ElementStatus.Unchanged));
        }
        
        // Part Numbers
        partNumbers.clear();
        stmt = db.prepareStatement("SELECT year, number FROM " + getTablename() + "_number WHERE part_id = ?");
        numParam = 1;
        stmt.setInt(numParam++, partId);
        result = stmt.executeQuery();
        while (result.next()) {
          numCol = 1;
          String year = result.getString(numCol++);
          String number = result.getString(numCol++);
          PartNumber pn = new PartNumber(this, year, number,ElementStatus.Unchanged);
          partNumbers.add(pn);
        }
        
        // Couleur
        color = Color.get(colorId);

        this.inDB = true;
        this.changed = false;
      }
      else {
        // L'objet n'existe pas encore
        this.inDB = false;

      }
    } catch (SQLException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
  }
  
  public String toString() {
    return "Part["+getId()+" "+data.toString()+"]";
  }
  
  public void save(Connection db, boolean force) {
    if (force || changed) {
      try {
        // Enregistre les labels pour avoir les IDs
        int titleId = 0;
        int descriptionId = 0;
        if (titleLabel!=null) {
          titleLabel.save(db, null);
          titleId = titleLabel.getId();
        }
        if (descriptionLabel!=null) {
          descriptionLabel.save(db, null);
          descriptionId = descriptionLabel.getId();
        }
        // Enregistre la fiche Part
        PreparedStatement stmt;
        if (!inDB) {
          // Pas déjà en base
          stmt = db.prepareStatement("INSERT INTO " + getTablename() + " "+
              " (id, type_id, ft_weight, icon, createdUTC, createdByUserName, title_id, description_id, ft_icon, ft_cat, ft_variant_uuid,color_id) "+
              " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)");
          int numParam = 1;
          stmt.setInt(numParam++, partId);
          stmt.setObject(numParam++, data.get(FTDBField.TypeID));
          stmt.setObject(numParam++, data.get(FTDBField.Weight));
          stmt.setObject(numParam++, data.get(FTDBField.Icon));
          stmt.setObject(numParam++, data.get(FTDBField.CreatedUTC));
          stmt.setObject(numParam++, data.get(FTDBField.CreatedByUserName));
          stmt.setInt(numParam++, titleId);
          stmt.setInt(numParam++, descriptionId);
          stmt.setObject(numParam++, data.get(FTDBField.FTIcon));
          stmt.setObject(numParam++, data.get(FTDBField.FTCat));
          stmt.setObject(numParam++, data.get(FTDBField.UUID));
          stmt.setInt(numParam++, color==null ? 0 : color.getId());
        }
        else {
          // Mise à jour
          stmt = db.prepareStatement("UPDATE " + getTablename() + " SET "+
              " type_id = ?, ft_weight = ?, icon = ?, createdUTC = ?, createdByUserName = ?, title_id = ?, description_id = ?, ft_icon = ?, ft_cat = ?, ft_variant_uuid = ?, color_id = ?"+
              "WHERE id = ?");
          int numParam = 1;
          stmt.setObject(numParam++, data.get(FTDBField.TypeID));
          stmt.setObject(numParam++, data.get(FTDBField.Weight));
          stmt.setObject(numParam++, data.get(FTDBField.Icon));
          stmt.setObject(numParam++, data.get(FTDBField.CreatedUTC));
          stmt.setObject(numParam++, data.get(FTDBField.CreatedByUserName));
          stmt.setInt(numParam++, titleId);
          stmt.setInt(numParam++, descriptionId);
          stmt.setObject(numParam++, data.get(FTDBField.FTIcon));
          stmt.setObject(numParam++, data.get(FTDBField.FTCat));
          stmt.setObject(numParam++, data.get(FTDBField.UUID));
          stmt.setInt(numParam++, color==null ? 0 : color.getId());
          stmt.setInt(numParam++, partId);
        }
        int res = stmt.executeUpdate();
        if (res==1) {
          db.commit();
          inDB = true;
          changed = false;
        }
        else {
          // Pas de ligne créée / maj ==> erreur ???
          logger.error("Pas de ligne mise à jour sur " + getTablename() + " #"+partId);
        }
        
        // Enregistrement du contenu
        for (PartCount pc : content.values()) {
          int numParam;
          switch (pc.getStatus()) {
          case Added:
            stmt = db.prepareStatement("INSERT INTO " + getTablename() + "_contains (container_id, part_id, count) VALUES (?,?,?)");
            numParam = 1;
            stmt.setInt(numParam++, partId);
            stmt.setInt(numParam++, pc.getPart().getId());
            stmt.setInt(numParam++, pc.getCount());
            res = stmt.executeUpdate();
            break;
          case Removed:
            stmt = db.prepareStatement("DELETE FROM " + getTablename() + "_contains WHERE container_id = ? AND part_id = ?");
            numParam = 1;
            stmt.setInt(numParam++, partId);
            stmt.setInt(numParam++, pc.getPart().getId());
            res = stmt.executeUpdate();
            break;
          case Updated:
            // Attention: selon que l'on a modifié manuellement ou non le nombre de pièces, la valeur lue dans FTDB
            // doit aller dans count ou dans ftdb_count.
            String countFieldName = "count";
            int newCount = pc.getCount();
            if (pc.getFtdbCount()!=null) {
              // Valeur manuelle dans count
              countFieldName = "ftdb_count";
              newCount = pc.getFtdbCount();
            }
            
            stmt = db.prepareStatement("UPDATE " + getTablename() + "_contains SET " + countFieldName + " = ? WHERE container_id = ? AND part_id = ?");
            numParam = 1;
            stmt.setInt(numParam++, newCount);
            stmt.setInt(numParam++, partId);
            stmt.setInt(numParam++, pc.getPart().getId());
            res = stmt.executeUpdate();
            break;
            default:
              break;
          }
          
        }
        db.commit();
        
        // Enregistrement des Part Numbers
        for (PartNumber pn : partNumbers) {
          int numParam;
          switch (pn.getStatus()) {
          case Added:
            stmt = db.prepareStatement("INSERT INTO " + getTablename() + "_number (part_id, year, number) VALUES (?,?,?)");
            numParam = 1;
            stmt.setInt(numParam++, pn.getPart().getId());
            stmt.setString(numParam++, pn.getYear());
            stmt.setString(numParam++, pn.getNumber());
            res = stmt.executeUpdate();
            break;
          case Removed:
            stmt = db.prepareStatement("DELETE FROM " + getTablename() + "_number WHERE part_id = ? AND year = ?");
            numParam = 1;
            stmt.setInt(numParam++, pn.getPart().getId());
            stmt.setString(numParam++, pn.getYear());
            res = stmt.executeUpdate();
            break;
          case Updated:
            stmt = db.prepareStatement("UPDATE " + getTablename() + "_number SET number = ? WHERE part_id = ? AND year = ?");
            numParam = 1;
            stmt.setString(numParam++, pn.getNumber());
            stmt.setInt(numParam++, pn.getPart().getId());
            stmt.setString(numParam++, pn.getYear());
            res = stmt.executeUpdate();
            break;
            default:
              break;
          }
        }
        db.commit();
      } catch (SQLException e) {
        logger.error("Enregistrement de pièce: "+e.getMessage());
        try {
          db.rollback();
        } catch (SQLException e1) {
        }
      }
    }
  }
  
  public static void saveAll(Connection db) {
    saveAll(db, loadedFromFTDB.values());
  }
  
  public static void saveAll(Connection db, Collection<Part> parts) {
    for (Part object: parts) {
      object.save(db, false);
    }
  }
  
  public Integer getFTIcon() {
    return (Integer) data.get(FTDBField.FTIcon);
  }
}
