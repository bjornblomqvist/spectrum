package com.greghaskins.spectrum.app;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

public class MyClassLoader extends URLClassLoader {

  public MyClassLoader() {
    super(new URL[0], ClassLoader.getSystemClassLoader().getParent());
  }

  public void addClassRootDirectory(String classRootDirectory) {
    try {
      System.out.println("Adding: " + new File(classRootDirectory).toURI().toURL());
      addURL(new File(classRootDirectory).toURI().toURL());
    } catch (MalformedURLException e) {
      throw new RuntimeException("Should not happen!", e);
    }
  }

  public void runMain(String className, String... args)
      throws ClassNotFoundException, InvocationTargetException,
      IllegalAccessException {

    Thread.currentThread().setContextClassLoader(this);

    Class clazz = this.loadClass(className);
    for (Method method : clazz.getDeclaredMethods()) {
      if (method.getName().equals("main")
          && Modifier.isStatic(method.getModifiers())) {
        method.invoke(clazz, new Object[] {args});
      }
    }
  }

  public void addJarFile(URL jarFile) {
    System.out.println("Adding: " + jarFile);
    addURL(jarFile);
  }

  public void addJarFileDirectory(String jarFileDirectory) {
    try {
      for (File file : new File(jarFileDirectory).listFiles()) {
        if (file.getName().endsWith(".jar")) {
          System.out.println("Adding: " + file);
          addURL(file.toURI().toURL());
        }
      }
    } catch (MalformedURLException e) {
      throw new RuntimeException("Should not happen!", e);
    }
  }
}
