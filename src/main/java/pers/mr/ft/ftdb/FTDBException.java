package pers.mr.ft.ftdb;

public class FTDBException extends Exception {
  private static final long serialVersionUID = 1L;

  public FTDBException(String message, Exception e) {
    super(message, e);
  }
}
