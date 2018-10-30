package com.greghaskins.spectrum.app;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

public class ClassPathSetup {

  /*
      - Add all dependencies
      - Add all classes (main + test)
      - Add spectrum jar (this jar)
   */
  public static MyClassLoader setupClassPath() {

    MyClassLoader myClassLoader = new MyClassLoader();

    if (isGradleProject()) {
      myClassLoader.addClassRootDirectory("./build/classes/main/");
      myClassLoader.addClassRootDirectory("./build/classes/test/");
      myClassLoader.addJarFileDirectory("./build/dependency-cache/");
      myClassLoader.addJarFile(getRootOrJarOfClass(ClassPathSetup.class));
    }

    if (isMavenProject()) {
      myClassLoader.addClassRootDirectory("./target/classes/");
      myClassLoader.addClassRootDirectory("./target/test-classes/");
      myClassLoader.addJarFileDirectory("./target/dependency/");
      myClassLoader.addJarFile(getRootOrJarOfClass(ClassPathSetup.class));
    }

    return myClassLoader;
  }

  public static boolean isGradleProject() {
    return new File("./build.gradle").exists();
  }

  public static boolean isMavenProject() {
    return new File("./pom.xml").exists();
  }

  private static URL getRootOrJarOfClass(Class clazz) {
    String classFileName = clazz.getName().replaceAll("\\.", "/") + ".class";
    ClassLoader classLoader = clazz.getClassLoader();
    if (classLoader == null) { // Boot classloader
      classLoader = ClassLoader.getSystemClassLoader().getParent(); // Ext classloader
    }

    URL classURL = classLoader.getResource(classFileName);

    if (classURL.toString().startsWith("jar:file:/")) {
      String url = classURL.toString().substring(4); // Remove jar:
      try {
        // Remove path in jar part !/org/bla/Test.class

        return new URL(url.substring(0, url.length() - classFileName.length() - 2));
      } catch (MalformedURLException ex) {
        throw new RuntimeException("Should not be possible", ex);
      }
    }

    if (classURL.toString().startsWith("file:/")) {
      String url = classURL.toString();
      try {
        // Remove className from path

        return new URL(url.substring(0, url.length() - classFileName.length())); //
      } catch (MalformedURLException ex) {
        throw new RuntimeException("Should not be possible", ex);
      }
    }

    throw new RuntimeException("Unsupported url!");
  }
}
