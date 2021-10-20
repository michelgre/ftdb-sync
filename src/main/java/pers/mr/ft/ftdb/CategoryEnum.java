package pers.mr.ft.ftdb;

public enum CategoryEnum {
  
  ConstructionKit(653),
  IndividualPart(773),
  ;
  
  private int catId;
  
  
  private CategoryEnum (int catId) {
    this.catId = catId;
  }


  public int getCatId() {
    return catId;
  }
}
