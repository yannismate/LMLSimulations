package de.tum.logistics;

import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class Main {
  private static File resourceFolder = new File("src/main/resources");
  public static void main(String[] args) {
    System.loadLibrary("libtracijni");
    File netFile = new File(resourceFolder, "osm.net.xml.gz");
    Simulation.start(new StringVector(new String[]{"sumo-gui", "-n", netFile.getAbsolutePath()}));
    for (int i = 0; i < 5; i++) {
      Simulation.step();
    }
    Simulation.close();
    Simulation.subscribe();
  }
}