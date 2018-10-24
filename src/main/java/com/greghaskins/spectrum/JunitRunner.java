package com.greghaskins.spectrum;

import static java.util.stream.Collectors.toList;

import org.junit.runner.Computer;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JunitRunner {
  public void run(Class[] testClassesToRun, String[] classesToMatch, boolean listOnly)
      throws ClassNotFoundException {
    JUnitCore jUnitCore = new JUnitCore();
    jUnitCore.addListener(new MyOutputter());
    //jUnitCore.addListener(new TextListener(System.out));

    Filter fillter = new Filter() {
      @Override
      public boolean shouldRun(Description description) {
        String classAndMethod = description.getClassName() + "#" + description.getMethodName();

        if (description.getMethodName() == null) {
          return true;
        }

        if (listOnly) {
          System.out.println(classAndMethod);
        }

        if (classesToMatch.length == 0) {
          return true;
        }

        for (String toMatch : classesToMatch) {
          if (matches(toMatch, classAndMethod)) {
            return true;
          }
        }

        return false;
      }

      @Override
      public String describe() {
        return "Dummy filter =)";
      }
    };

    Request request = Request.classes(new Computer(), testClassesToRun);

    Runner runner = request.getRunner();
    try {
      fillter.apply(runner);
      if (!listOnly) {
        jUnitCore.run(runner);
      }
    } catch (NoTestsRemainException e) {
      if (!listOnly) {
        System.out.println("No tests found");
      }
    }
  }

  private boolean matches(String toMatch, String classAndMethod) {
    // Only method
    if (toMatch.startsWith("#")) {
      return classAndMethod.endsWith(toMatch);
    }

    // Method and class
    if (toMatch.contains("#")) {
      return classAndMethod.equals(toMatch);
    }

    System.out.println(toMatch);
    System.out.println(classAndMethod);
    // Only class

    return classAndMethod.startsWith(toMatch);
  }

  private static boolean hasRunWithAnotation(Class clazz) {
    return clazz.getAnnotation(org.junit.runner.RunWith.class) != null;
  }

  private static boolean hasTestAnotation(Class clazz) {
    for (Method method : clazz.getDeclaredMethods()) {
      if (method.getAnnotation(org.junit.Test.class) != null) {
        return true;
      }
    }

    return false;
  }

  public static class MyURLClassLoader extends URLClassLoader {

    public MyURLClassLoader(ClassLoader parent) {
      super(new URL[0], parent);
    }

    public void add(URL url) {
      this.addURL(url);
    }
  }

  public static void main(String... args)
      throws ClassNotFoundException, IOException, InterruptedException, NoSuchMethodException,
      InvocationTargetException, IllegalAccessException {

    URLClassLoader classLoader = (URLClassLoader) JunitRunner.class.getClassLoader();

    Method method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[] {URL.class});
    method.setAccessible(true);

    method.invoke(classLoader, new Object[] {new File("./build/classes/main").toURI().toURL()});
    method.invoke(classLoader, new Object[] {new File("./build/classes/test").toURI().toURL()});

    List<Path> jarPaths = Files.walk(new File("./build/dependency-cache/").toPath()).collect(toList());

    for (Path path : jarPaths) {
      if (path.toString().endsWith(".jar")) {
        method.invoke(classLoader, new Object[] {path.toUri().toURL()});
      }
    }

    // Find and load all junit test classes
    Set<Class> classes = new HashSet<>();
    Path absoluteTestClassPath = new File("build/classes/test/").toPath();
    List<Path> paths = Files.walk(absoluteTestClassPath).collect(toList());

    for (Path path : paths) {
      if (path.toString().contains("$")) {
        continue;
      }

      if (!new File(path.toString()).isFile()) {
        continue;
      }

      String className = path
          .toString()
          .replaceAll("^build\\/classes\\/test\\/", "")
          .replaceAll("\\.class$", "")
          .replaceAll("\\/", ".");
      Class clazz = classLoader.loadClass(className);

      if (Modifier.isAbstract(clazz.getModifiers())) {
        continue;
      }

      if (hasRunWithAnotation(clazz) || hasTestAnotation(clazz)) {
        classes.add(clazz);
      }
    }

    new JunitRunner().run(classes.toArray(new Class[0]), new String[0], false);

    /*
        # Goal
    
        	To have rspec like output and a good way to only rerun failed tests.
    
        	Formats: progress, doc, pluggable formatters
    
            spectrum org.spectrum.SomethingTest:34
            spectrum org.spectrum.SomethingTest#MethodName
            spectrum org.spectrum.SomethingTest#Method*Name
            spectrum org.spectrum.*#Method*Name
    
    
        1. A runnable jar that can run junit tests within a basic maven project.
        2. Add so that it can run a specific test based on class and line number.
            - For classic junit tests line number can be mapped to test based on line number table for methods.
            - For spectrum we let spectrum decide if a test should be run.
    
        # Todo
    
        // - Color dots
        // - F for failure
        // - List failures
        // - List failed examples, with how to run the failed spec
        // - Show only point of assertion point from stack trace
        // - Move to spectrum source code
        // - Create a runnable fat jar
    
        // - Add target/classes to classpath at runtime
        // - target/test-classes to classpath at runtime
        // - target/dependencies to classpath at runtime

        - To make it nice we need a continues compiler OR being able to run directly from source.
    
        - Add spectrum detection
    
        # Test
    
        Should be able to run the tests for the following
    
            - netty
            - guava
            - spring-framework
            - spectrum
    
            - mockito
            - power-mockito
            - jmockit
     */
  }

  public static class MyOutputter extends RunListener {

    HashMap<Description, String> fullStrings = new HashMap<>();
    long startTime = 0;

    public void testRunStarted(Description description) throws Exception {
      //System.out.println("Starting: " + description);
      startTime = System.currentTimeMillis();
      print(description, "", 1);
    }

    private void print(Description description, String part, int level) {
      if (level >= 3) { // We skip the two first as they are not interesting.
        if (description.isSuite()) {
          part = part.trim() + " " + description.getDisplayName();
        } else {
          part = part.trim() + " " + description.getMethodName();
        }
      }

      for (Description d : description.getChildren()) {
        print(d, part, level + 1);
      }

      if (description.getChildren().size() == 0) {
        fullStrings.put(description, part);
      }
    }

    public void testRunFinished(Result result) throws Exception {

      double timeInSeconds = (System.currentTimeMillis() - startTime) / 1000d;
      boolean wasFailure = result.getFailureCount() > 0;


      if (wasFailure) {
        System.out.println("\n\nFailures:");

        int count = 0;

        for (Failure failure : result.getFailures()) {
          count++;
          String fullString = fullStrings.get(failure.getDescription());
          StackTraceElement elementToPrint = null;

          for (StackTraceElement element : failure.getException().getStackTrace()) {
            if (!(element.getClassName().toLowerCase().contains("assert") ||
                element.getMethodName().toLowerCase().contains("assert"))) {
              elementToPrint = element;
              break;
            }
          }

          if (elementToPrint != null) {
            System.out.println("\n  " + count + ") " + fullString);
            System.out.println(reset(red(indentLines(failure.getException().getClass().getName() + " "
                + failure.getException().getLocalizedMessage(), "     "))));
            //            System.out.println(reset(red(indentLines(failure.getMessage(), "     "))));
            System.out.println("\n" + reset(cyan(indentLines(
                "// " + elementToPrint.getClassName() + ":" + elementToPrint.getLineNumber(), "     "))));
          }
        }
      }

      System.out.println("\n");
      System.out.println("Finished in " + timeInSeconds + " seconds");
      if (wasFailure) {
        System.out.println(
            reset(red(result.getRunCount() + " examples, " + result.getFailureCount() + " failures")));
      } else {
        System.out.println(
            reset(green(result.getRunCount() + " examples, " + result.getFailureCount() + " failures")));
      }
      System.out.println("");

      if (wasFailure) {
        System.out.println("Failed examples:");
        System.out.println("");

        for (Failure failure : result.getFailures()) {
          String fullString = fullStrings.get(failure.getDescription());
          StackTraceElement elementToPrint = null;

          for (StackTraceElement element : failure.getException().getStackTrace()) {
            if (!(element.getClassName().toLowerCase().contains("assert") ||
                element.getMethodName().toLowerCase().contains("assert"))) {
              elementToPrint = element;
              break;
            }
          }

          if (elementToPrint != null) {
            System.out.println(reset(
                red("spectrum " + elementToPrint.getClassName() + ":" + elementToPrint.getLineNumber()) +
                    cyan(" // " + fullString)));
          }
        }
      }
    }

    public void testStarted(Description description) throws Exception {
      //System.out.println("Test started:" + description);
    }

    public void testFinished(Description description) throws Exception {
      System.out.print(reset(green(".")));
      //System.out.println("Test finished:" + description);
    }

    public void testFailure(Failure failure) throws Exception {
      System.out.print(reset(red("F")));
      //System.out.println("Test failure:" + failure);
    }

    public void testAssumptionFailure(Failure failure) {
      System.out.println("Assumption failure:" + failure);
    }

    public void testIgnored(Description description) throws Exception {
      System.out.println("Ignored:" + description);
    }
  }

  public static String cyan(String string) {
    return ConsoleColors.CYAN + string;
  }

  public static String green(String string) {
    return ConsoleColors.GREEN + string;
  }

  public static String red(String string) {
    return ConsoleColors.RED + string;
  }

  public static String reset(String string) {
    return string + ConsoleColors.RESET;
  }

  public static String indentLines(String text, String indentWith) {
    return indentWith + String.join("\n" + indentWith, text.split("\n"));
  }

  public class ConsoleColors {
    // Reset
    public static final String RESET = "\033[0m"; // Text Reset

    // Regular Colors
    public static final String BLACK = "\033[0;30m"; // BLACK
    public static final String RED = "\033[0;31m"; // RED
    public static final String GREEN = "\033[0;32m"; // GREEN
    public static final String YELLOW = "\033[0;33m"; // YELLOW
    public static final String BLUE = "\033[0;34m"; // BLUE
    public static final String PURPLE = "\033[0;35m"; // PURPLE
    public static final String CYAN = "\033[0;36m"; // CYAN
    public static final String WHITE = "\033[0;37m"; // WHITE

    // Bold
    public static final String BLACK_BOLD = "\033[1;30m"; // BLACK
    public static final String RED_BOLD = "\033[1;31m"; // RED
    public static final String GREEN_BOLD = "\033[1;32m"; // GREEN
    public static final String YELLOW_BOLD = "\033[1;33m"; // YELLOW
    public static final String BLUE_BOLD = "\033[1;34m"; // BLUE
    public static final String PURPLE_BOLD = "\033[1;35m"; // PURPLE
    public static final String CYAN_BOLD = "\033[1;36m"; // CYAN
    public static final String WHITE_BOLD = "\033[1;37m"; // WHITE

    // Underline
    public static final String BLACK_UNDERLINED = "\033[4;30m"; // BLACK
    public static final String RED_UNDERLINED = "\033[4;31m"; // RED
    public static final String GREEN_UNDERLINED = "\033[4;32m"; // GREEN
    public static final String YELLOW_UNDERLINED = "\033[4;33m"; // YELLOW
    public static final String BLUE_UNDERLINED = "\033[4;34m"; // BLUE
    public static final String PURPLE_UNDERLINED = "\033[4;35m"; // PURPLE
    public static final String CYAN_UNDERLINED = "\033[4;36m"; // CYAN
    public static final String WHITE_UNDERLINED = "\033[4;37m"; // WHITE

    // Background
    public static final String BLACK_BACKGROUND = "\033[40m"; // BLACK
    public static final String RED_BACKGROUND = "\033[41m"; // RED
    public static final String GREEN_BACKGROUND = "\033[42m"; // GREEN
    public static final String YELLOW_BACKGROUND = "\033[43m"; // YELLOW
    public static final String BLUE_BACKGROUND = "\033[44m"; // BLUE
    public static final String PURPLE_BACKGROUND = "\033[45m"; // PURPLE
    public static final String CYAN_BACKGROUND = "\033[46m"; // CYAN
    public static final String WHITE_BACKGROUND = "\033[47m"; // WHITE

    // High Intensity
    public static final String BLACK_BRIGHT = "\033[0;90m"; // BLACK
    public static final String RED_BRIGHT = "\033[0;91m"; // RED
    public static final String GREEN_BRIGHT = "\033[0;92m"; // GREEN
    public static final String YELLOW_BRIGHT = "\033[0;93m"; // YELLOW
    public static final String BLUE_BRIGHT = "\033[0;94m"; // BLUE
    public static final String PURPLE_BRIGHT = "\033[0;95m"; // PURPLE
    public static final String CYAN_BRIGHT = "\033[0;96m"; // CYAN
    public static final String WHITE_BRIGHT = "\033[0;97m"; // WHITE

    // Bold High Intensity
    public static final String BLACK_BOLD_BRIGHT = "\033[1;90m"; // BLACK
    public static final String RED_BOLD_BRIGHT = "\033[1;91m"; // RED
    public static final String GREEN_BOLD_BRIGHT = "\033[1;92m"; // GREEN
    public static final String YELLOW_BOLD_BRIGHT = "\033[1;93m";// YELLOW
    public static final String BLUE_BOLD_BRIGHT = "\033[1;94m"; // BLUE
    public static final String PURPLE_BOLD_BRIGHT = "\033[1;95m";// PURPLE
    public static final String CYAN_BOLD_BRIGHT = "\033[1;96m"; // CYAN
    public static final String WHITE_BOLD_BRIGHT = "\033[1;97m"; // WHITE

    // High Intensity backgrounds
    public static final String BLACK_BACKGROUND_BRIGHT = "\033[0;100m";// BLACK
    public static final String RED_BACKGROUND_BRIGHT = "\033[0;101m";// RED
    public static final String GREEN_BACKGROUND_BRIGHT = "\033[0;102m";// GREEN
    public static final String YELLOW_BACKGROUND_BRIGHT = "\033[0;103m";// YELLOW
    public static final String BLUE_BACKGROUND_BRIGHT = "\033[0;104m";// BLUE
    public static final String PURPLE_BACKGROUND_BRIGHT = "\033[0;105m"; // PURPLE
    public static final String CYAN_BACKGROUND_BRIGHT = "\033[0;106m"; // CYAN
    public static final String WHITE_BACKGROUND_BRIGHT = "\033[0;107m"; // WHITE
  }
}
