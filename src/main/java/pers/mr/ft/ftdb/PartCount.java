package pers.mr.ft.ftdb;

public class PartCount {
  private int count = 0;
  private Integer ftdbCount = null;
  private Part part = null;
  private ElementStatus status;
  
  public int getCount() {
    return count;
  }
  
  public void setCount(int count) {
    // Selon que l'on a une valeur pour ftdbCount (le compte a été modifié manuellement) ou pas,
    // on manipule count ou ftdbCount.
    if (this.ftdbCount == null) { // Valeur FTDB dans count
      if (this.count!=count) {
        this.count = count;
        this.setStatus(ElementStatus.Updated);
      }
    }
    else { // Valeur manuelle dans count, on n'y touche pas
      if (this.ftdbCount!=count) {
        this.ftdbCount = count;
        this.setStatus(ElementStatus.Updated);
      }
    }
  }

  public Integer getFtdbCount() {
    return ftdbCount;
  }

  public void setFtdbCount(Integer ftdbCount) {
    this.ftdbCount = ftdbCount;
  }

  public ElementStatus getStatus() {
    return status;
  }

  public void setStatus(ElementStatus status) {
    this.status = status;
  }

  public Part getPart() {
    return part;
  }

  public PartCount(Part part, int count, ElementStatus status) {
    this.part = part;
    this.count = count;
    this.status = status;
  }
  
  public PartCount(Part part, int count, Integer ftdbCount, ElementStatus status) {
    this.part = part;
    this.count = count;
    this.ftdbCount = ftdbCount;
    this.status = status;
  }
  
  public PartCount(Part part, int count) {
    this.part = part;
    this.count = count;
    this.status = ElementStatus.Unknown;
  }
  
  public String toString () {
    return "["+count+" "+part.getId()+"]";
  }
}
