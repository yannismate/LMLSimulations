package de.tum.logistics;

import java.io.File;
import java.util.HashSet;
import java.util.List;

import me.tongfei.progressbar.ProgressBar;
import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;

import de.tum.logistics.osm.GeoJsonParser;
import de.tum.logistics.osm.OsmNode;
import de.tum.logistics.problems.TruckDeliveryProblem;
import org.eclipse.sumo.libtraci.VehicleType;

public class HubBaker {
  public static void main(String[] args) {
    System.loadLibrary("libtracijni");
    File netFile = new File(Main.RESOURCE_FOLDER, "osm.net.xml.gz");
    Simulation.start(new StringVector(new String[]{"sumo", "-W", "-n", netFile.getAbsolutePath()}));
    VehicleType.copy("DEFAULT_VEHTYPE", "passenger");

    List<OsmNode> possibleLocations = GeoJsonParser.parseOsmNodes(new File(Main.RESOURCE_FOLDER, "addresses.json"));

    for (OsmNode node : ProgressBar.wrap(new HashSet<>(possibleLocations),"Filtering unroutable OSM locations")) {
      if (!node.canBeReachedBy(TruckDeliveryProblem.ENTRY_EDGE,"passenger")) {
        possibleLocations.remove(node);
      }
      if (!node.canRouteTo(TruckDeliveryProblem.EXIT_EDGE,"passenger")) {
        possibleLocations.remove(node);
      }
    }

    {
      TruckDeliveryProblem problem = new TruckDeliveryProblem();
      problem.init(possibleLocations, (int) (15000*0.6));
      problem.solve();
      problem.writeRouteXML(new File(Main.RESOURCE_FOLDER, "routes_dhl.xml"), "dhl", "#FFFF00");
      problem.plotSolution(new File(Main.RESOURCE_FOLDER, "solution_dhl.png"));
    }
    {
      TruckDeliveryProblem problem = new TruckDeliveryProblem();
      problem.init(possibleLocations, (int) (15000*0.25));
      problem.solve();
      problem.writeRouteXML(new File(Main.RESOURCE_FOLDER, "routes_ups.xml"), "ups", "#704300");
      problem.plotSolution(new File(Main.RESOURCE_FOLDER, "solution_ups.png"));
    }
    {
      TruckDeliveryProblem problem = new TruckDeliveryProblem();
      problem.init(possibleLocations, (int) (15000*0.15));
      problem.solve();
      problem.writeRouteXML(new File(Main.RESOURCE_FOLDER, "routes_dpd.xml"), "dpd", "#FF0000");
      problem.plotSolution(new File(Main.RESOURCE_FOLDER, "solution_dpd.png"));
    }



    Simulation.close("Done!");
    System.exit(0);
  }
}
