package de.tum.logistics;

import java.io.File;
import java.util.List;

import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;

import de.tum.logistics.osm.GeoJsonParser;
import de.tum.logistics.osm.OsmNode;
import de.tum.logistics.problems.TruckDeliveryProblem;

public class ProblemBaking {

  public static void main(String[] args) {
    System.loadLibrary("libtracijni");
    File netFile = new File(Main.RESOURCE_FOLDER, "osm.net.xml.gz");
    Simulation.start(new StringVector(new String[]{"sumo-gui", "-n", netFile.getAbsolutePath()}));

    List<OsmNode> possibleLocations = GeoJsonParser.parseOsmNodes(new File(Main.RESOURCE_FOLDER, "addresses.json"));
    TruckDeliveryProblem problem = new TruckDeliveryProblem();
    problem.init(possibleLocations);
    problem.solve();
    problem.writeRouteXML(new File(Main.RESOURCE_FOLDER, "routes.xml"));
  }

}
