package de.tum.logistics;

import org.eclipse.sumo.libsumo.Simulation;
import org.eclipse.sumo.libsumo.StringVector;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class Main {
  private static Set<String> libraries = new HashSet<>();
  static {
    libraries.add("libtracijni");
    libraries.add("iconv-2");
    libraries.add("intl-8");
    libraries.add("proj_9_0");
    libraries.add("libsumojni");
    libraries.add("libtracijni");
  }
  private static File resourceFolder = new File("src/main/resources");
  public static void main(String[] args) {
    for (String libName : libraries) {
      System.loadLibrary(libName);
    }

    File netFile = new File(resourceFolder, "osm.net.xml.gz");

    Simulation.start(new StringVector(new String[]{"sumo", "-n", netFile.getAbsolutePath()}));
    for (int i = 0; i < 5; i++) {
      Simulation.step();
    }
    Simulation.close();
  }
}