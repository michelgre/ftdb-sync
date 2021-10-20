package pers.mr.ft.ftdb;

public class PartNumber {
  
  private Part part = null;
  private String year = "";
  private String number = "";
  private ElementStatus status;
  
  public ElementStatus getStatus() {
    return status;
  }

  public void setStatus(ElementStatus status) {
    this.status = status;
  }

  public Part getPart() {
    return part;
  }

  public void setPart(Part part) {
    this.part = part;
  }

  public String getYear() {
    return year;
  }

  public void setYear(String year) {
    this.year = year;
  }

  public String getNumber() {
    return number;
  }

  public void setNumber(String number) {
    if (this.number == null && number==null) return;
    if (this.number != null) {
      if (this.number.equals(number)) return;
    }
    this.number = number;
    status = ElementStatus.Updated;
  }

  public PartNumber(Part part, String year, String number, ElementStatus status) {
    this.part = part;
    this.year = year;
    this.number = number;
    this.status = status;
  }
  
  public boolean sameYear(PartNumber pn) {
    if (year == null && pn.year == null) return true;
    if (year!=null) {
      return year.equals(pn.year);
    }
    return false;
  }
  public String toString () {
    int partId = part == null ? 0 : part.getId();
    return "["+partId+" "+year+":"+number+"]";
  }
}
