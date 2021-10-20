package pers.mr.ft.ftdbsync;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import pers.mr.ft.ftdb.Category;
import pers.mr.ft.ftdb.CategoryEnum;
import pers.mr.ft.ftdb.Color;
import pers.mr.ft.ftdb.FTDBException;
import pers.mr.ft.ftdb.Image;
import pers.mr.ft.ftdb.Part;
import pers.mr.ft.ftdb.PartCount;

public class FTDBSync {
  private static Logger logger = Logger.getLogger(FTDBSync.class);

  private String dbUrl = null;
  private String dbUser = null;
  private String dbPwd = null;
  private boolean doParts = false;
  private boolean doImages = false;
  private boolean saveImageTofiles = false;
  private File imageDirectory = new File("images");
  private File thumbnailDirectory = new File("thumb");

  private Connection dbConnection = null; 

  public FTDBSync withDbUser(String dbUser) {
    this.dbUser = dbUser;
    return this;
  }
  
  public FTDBSync withDbPwd(String dbPwd) {
    this.dbPwd = dbPwd;
    return this;
  }
  
  public FTDBSync withDoParts(boolean doParts) {
    this.doParts = doParts;
    return this;
  }
  
  public FTDBSync withDoImages(boolean doImages) {
    this.doImages = doImages;
    return this;
  }
  
  public FTDBSync withSaveImageTofiles(boolean saveImageTofiles) {
    this.saveImageTofiles = saveImageTofiles;
    return this;
  }
  
  public FTDBSync(String dbUrl) {
    this.dbUrl = dbUrl;
  }

  public List<Part> loadByCategory(int catId, boolean withContent) {
    // URL: https://ft-datenbank.de/api/tickets?drill_ft_cat_all=<cat>
    //
    // Les résultats sont paginés.
    
    List<PartCount> partCounts = Part.loadPaginagedPartListFromFTDB(dbConnection, "/api/tickets?drill_ft_cat_all="+catId, false, withContent);
    List<Part> parts = new LinkedList<>();
    for (PartCount pc: partCounts) {
      parts.add(pc.getPart());
    }
    return parts;
  }
  public List<Part>  loadByPart(int partId, boolean withContent, boolean forceSave) {
    
    List<Part> parts = new LinkedList<>();
    try {
      Part part = Part.loadFromFTDB(dbConnection, partId, withContent);
      part.save(dbConnection, forceSave);
      parts.add(part);
    } catch (FTDBException e) {
      logger.error("Chargement de la pièce "+partId+" impossible");
    }
    return parts;
  }

  public boolean connectDB (String user, String pwd) {
    Properties connectionProps = new Properties();
    connectionProps.put("user", user);
    connectionProps.put("password", pwd);
    try {
      dbConnection = DriverManager.getConnection(dbUrl, connectionProps);
      dbConnection.setAutoCommit(false);
    } catch (SQLException e) {
      logger.error(e.getMessage());
      return false;
    }
    return true;
  }


  public List<Part> synchronizeParts(int rootCategory) {
    // Récupère la liste de kits: type_id = 653
    List<Part> kitsList = loadByCategory(rootCategory, true);
    return kitsList;
  }

  public void synchronizeImages() {
    if (!connectDB (dbUser, dbPwd)) {
      return;
    }
    synchronizeImages(null, false);
  }
  
  public void synchronizeImages(int partId) {
    if (!connectDB (dbUser, dbPwd)) {
      return;
    }
    List<Part> updatedParts = loadByPart(partId, false, false);
    synchronizeImages(updatedParts, true);
  }
  
  public void synchronizeImages(List<Part> updatedParts, boolean forceLoadFT) {
    // On ne récupèrera que les images pas encore chargées, à voir si on peut faire par rapport à la date,
    // sauf si forceLoadFT
    Set<Integer> imagesToLoad = new HashSet<Integer>();
    for (Part part: updatedParts) {
      Integer iconId = part.getFTIcon();
      if (iconId!=null && iconId>0) {
        imagesToLoad.add(iconId);
      }
    }
    
    // En cas d'enregistrment local des images
    if (saveImageTofiles) {
      imageDirectory.mkdirs();
      thumbnailDirectory.mkdirs();
    }
    
    // Recherche des images référencées dans la base ou dans FTDB mais sans contenu
    try {
      int iconCount = 0;
      StringBuffer querySb = new StringBuffer();
      querySb.append("SELECT DISTINCT ft_icon FROM part p LEFT JOIN image i ON i.id = p.ft_icon WHERE ft_icon > 0 AND (i.image IS NULL OR i.tn_100 IS NULL)");
      
      if (updatedParts!=null) {
        if (updatedParts.size()==0) {
          // Pas de parts à màj
          return;
        }
        querySb.append(" AND p.ft_icon IN (");
        boolean first = true;
        for (Part part: updatedParts) {
          Integer iconId = part.getFTIcon();
          if (iconId != null && iconId>0) {
            if (first) {
              first = false;
            }
            else {
              querySb.append(",");
            }
            querySb.append(iconId.toString());
            iconCount++;
          }
        }
        querySb.append(")");
      }
      if (iconCount>0) {
        PreparedStatement stmt = dbConnection.prepareStatement(querySb.toString());
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
          int numCol = 1;
          int iconId = rs.getInt(numCol++);
          
          try {
            logger.info("Chargement Image " + iconId);
            Image image = Image.loadFromFTDB(dbConnection, iconId, false);
            image.save(dbConnection);
            imagesToLoad.remove(iconId); // Cette image a été chargée
            if (saveImageTofiles) {
              image.saveImageToFile(imageDirectory);
              image.saveThumbnailToFile(thumbnailDirectory);
            }
          } catch (FTDBException e) {
          }
        }
        rs.close();
      }
    } catch (SQLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    // Si chargement forcé, on charge aussi les images restantes
    for (Integer iconId: imagesToLoad) {
      try {
        logger.info("Chargement Image " + iconId);
        Image image = Image.loadFromFTDB(dbConnection, iconId, false);
        image.save(dbConnection);
        imagesToLoad.remove(iconId); // Cette image a été chargée
        if (saveImageTofiles) {
          image.saveImageToFile(imageDirectory);
          image.saveThumbnailToFile(thumbnailDirectory);
        }
      } catch (FTDBException e) {
      }
    }
  }
  
  public void synchronizeCategory(int rootCategory) {
    if (!connectDB (dbUser, dbPwd)) {
      return;
    }

    // Charger la liste des couleurs pour l'analyse des labels
    Color.loadAllFromDB(dbConnection);
    
    List<Part> updatedParts = null;
    
    if (doParts) {
      updatedParts = synchronizeParts(rootCategory);
      Category.saveAll(dbConnection);
    }
    
    if (doImages) {
      synchronizeImages(updatedParts, false);
    }
  }
  
  public void synchronizePart(int partId) {
    if (!connectDB (dbUser, dbPwd)) {
      return;
    }

    // Charger la liste des couleurs pour l'analyse des labels
    Color.loadAllFromDB(dbConnection);
    
    // Liste des pièces reçues
    List<Part> updatedParts = null;
    
    if (doParts) {
      updatedParts = loadByPart(partId, true, true);
      Category.saveAll(dbConnection);
    }
    
    if (doImages) {
      synchronizeImages(updatedParts, true);
    }
  }
  
  
  public static void main(String[] args) {
    String pgUrl = "jdbc:postgresql://qnap251/ftdb";
    Integer rootCategory = CategoryEnum.ConstructionKit.getCatId();
    FTDBSync sync = new FTDBSync(pgUrl);
    Pattern argPattern = Pattern.compile("--([^=]+)(=(.*))?");
    for (String arg: args) {
      Matcher m = argPattern.matcher(arg);
      if (m.matches()) {
        String opt = m.group(1);
        String value = m.group(3);
        
        if (opt.equals("pg")) {
          pgUrl = value;
        }
        else if (opt.equals("user")) {
          sync.dbUser = value;
        }
        else if (opt.equals("pwd")) {
          sync.dbPwd = value;
        }
        else if (opt.equals("parts")) {
          sync.doParts = true;
        }
        else if (opt.equals("images")) {
          sync.doImages = true;
        }
        else if (opt.equals("images-to-files")) {
          sync.saveImageTofiles = true;
        }
        else if (opt.equals("cat")) {
          rootCategory = Integer.parseInt(value);
        }
      }
          
    }
    
    sync.synchronizeCategory(rootCategory);
  }

}
