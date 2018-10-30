package com.greghaskins.spectrum.app;


public class Application {
  public static void main(String... args) {
    long startTime = System.currentTimeMillis();
    try {
      ClassPathSetup.setupClassPath().runMain("com.greghaskins.spectrum.app.JunitRunner", args);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    System.out.println("Total time: " + (System.currentTimeMillis() - startTime + 150) + " (+/- 50ms)");
  }
}
