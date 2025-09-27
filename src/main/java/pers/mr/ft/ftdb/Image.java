package pers.mr.ft.ftdb;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.log4j.Logger;
import org.json.JSONException;

public class Image {
  private static Logger logger = Logger.getLogger(Image.class);
  
  private static Map<Integer,Image> loadedFromFTDB = new HashMap<>();
  private Integer id = 0;
  private byte[] image = null;
  private byte[] tn100 = null;
  private LocalDateTime time = null;
  private boolean inDB = false;
  private boolean changed = false;

  public Image(Integer id) {
    this.id = id;
  }
  
  public static Image loadFromFTDB(Connection db, Integer id, boolean force) throws FTDBException {
    FTDB ftdb = FTDB.getInstance();
    
    // On ne recharge pas l'image si on la déjà chargée
    Image image = loadedFromFTDB.get(id);
    if (image==null) {
      // Pas déjà chargé depuis FTDB
      try {
        image = loadFromDB(db, id);
        if (image.image==null || force) { // Ne pas recharger les images déjà en base sauf si force
          logger.info(""+loadedFromFTDB.size()+" - Charge Image "+id);
          URL url = new URL(ftdb.getUrl()+"/binary/"+id);
          
          // Lire l'image dans un byte array
          ByteArrayOutputStream output = new ByteArrayOutputStream();
          try (InputStream inputStream = url.openStream()) {
            int n = 0;
            byte [] buffer = new byte[ 1024 ];
            while (-1 != (n = inputStream.read(buffer))) {
                output.write(buffer, 0, n);
            }
          }
          image.image = output.toByteArray();
          image.time = LocalDateTime.now();
          loadedFromFTDB.put(id, image);
        }
      } catch (JSONException | IOException e) {
        logger.error(e.getMessage());
        throw new FTDBException("Chargement d'une pièce: "+e.getMessage(), e);
      }
    }
    return image;
  }
  
  public static Image loadFromDB(Connection db, Integer id) {
      Image image = new Image(id);
      image.loadFromDB(db);
        
      return image;
  }
  
  public void loadFromDB(Connection db) {
    try {
      PreparedStatement stmt = db.prepareStatement("SELECT image, tn_100, update_date FROM image WHERE id = ?");
      stmt.setInt(1, id);
      ResultSet rs = stmt.executeQuery();
      if (rs.next()) {
      
        int numCol = 1;
        image = rs.getBytes(numCol++); 
        tn100 = rs.getBytes(numCol++); 
        Timestamp ts = rs.getTimestamp(numCol++);
        
        if (ts!=null) {
          time = ts.toLocalDateTime();
        }
        inDB = true;
        changed = false;
      }
    } catch (SQLException e) {
      logger.error("Erreur au chargement de l'image "+id+": "+e.getMessage());
    }
  }
  
  private byte[] createThumbnail(byte[] image, int tnSize) {
    if (image==null || image.length==0) return null;
    try {
      Iterator readers = ImageIO.getImageReadersByFormatName("JPEG");
      BufferedImage img = ImageIO.read(new ByteArrayInputStream(image));
      int w = img.getWidth();
      int h = img.getHeight();
      BufferedImage tnImg = new BufferedImage(tnSize, tnSize, BufferedImage.TYPE_INT_ARGB);
      
      double scaleW = ((double) tnSize) / w;
      double scaleH = ((double) tnSize) / h;
      double scale = Math.min(scaleW, scaleH);
      
      AffineTransform at = new AffineTransform();
      at.scale(scale, scale);
      AffineTransformOp scaleOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
      tnImg = scaleOp.filter(img, tnImg);
      
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ImageIO.write( tnImg, "png", baos );
      baos.flush();
      byte[] bytes = baos.toByteArray();
      baos.close();
      return bytes;
    } catch (IOException e) {
      logger.error("Erreur en construisant le thumbnail pour l'image "+id+": "+e.getMessage());
    }

    return null;
  }
  
  public void save(Connection db) {
    if (image!=null && image.length>0 && (tn100==null||tn100.length==0)) {
      // Calculer le TN
      tn100 = createThumbnail(image, 100);
      changed = true;
    }
    if (!inDB || changed) {
      try {
        PreparedStatement stmt = null;
        if (!inDB) {
          stmt = db.prepareStatement("INSERT INTO image (id, image, tn_100, update_date) VALUES (?,?,?,?)");
          int numParam = 1;
          stmt.setInt(numParam++, id);
          
          if (image!=null) {
            stmt.setBytes(numParam++, image);
          }
          else {
            stmt.setNull(numParam++, Types.BINARY);
          }
          if (tn100!=null) {
            stmt.setBytes(numParam++, tn100); //tn100
          }
          else {
            stmt.setNull(numParam++, Types.BINARY);
          }
          stmt.setTimestamp(numParam++, Timestamp.valueOf(time));
        }
        else {
          stmt = db.prepareStatement("UPDATE image SET image = ?, tn_100 = ?, update_date = ? WHERE id = ?");
          int numParam = 1;
          if (image!=null) {
            stmt.setBytes(numParam++, image);
          }
          else {
            stmt.setNull(numParam++, Types.BINARY);
          }
          if (tn100!=null) {
            stmt.setBytes(numParam++, tn100);
          }
          else {
            stmt.setNull(numParam++, Types.BINARY);
          }
          stmt.setTimestamp(numParam++, Timestamp.valueOf(time));
          stmt.setInt(numParam++, id);

        }
        if (stmt.executeUpdate() == 1) {
          db.commit();
          inDB = true;
          changed = false;
        }
      } catch (SQLException e) {
        logger.error("Erreur à l'enregistrement de l'image "+id+": "+e.getMessage());
      } finally {
      }
    }
  }
  
  private void saveImageToFile(File f, byte[] image) {
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(f);
      fos.write(image);
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (fos!=null) {
        try {
          fos.close();
        } catch (IOException e) {
        }
      }
    }
  }
  
  public void saveImageToFile(File dir) {
    if (image!=null) {
      try {
        // Format de l'image ?
        String ext = "png" ; // Par défaut
        ByteArrayInputStream bais = new ByteArrayInputStream(image);
        ImageInputStream iis = ImageIO.createImageInputStream(bais);
        Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(iis);
  
        while (imageReaders.hasNext()) {
            ImageReader reader = (ImageReader) imageReaders.next();
            String fmt = reader.getFormatName();
            ext = fmt.toLowerCase();
            break;
        }
        saveImageToFile(new File(dir, "" + id + "." + ext), image); // TODO: image extension ??
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        
      }
    }
  }
  public void saveThumbnailToFile(File dir) {
    if (tn100!=null) {
      saveImageToFile(new File(dir, "" + id + "_tn100.png"), tn100); // TODO: image extension ??
    }
  }
}
