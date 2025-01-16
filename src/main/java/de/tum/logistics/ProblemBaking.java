package de.tum.logistics;

import java.io.File;
import java.util.List;

import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;

import de.tum.logistics.osm.DepotNode;
import de.tum.logistics.osm.GeoJsonParser;
import de.tum.logistics.problems.MicroHubDeliveryProblem;
import me.tongfei.progressbar.ProgressBar;

import org.eclipse.sumo.libtraci.VehicleType;

public class ProblemBaking {

  public static void main(String[] args) {
    System.loadLibrary("libtracijni");
    File netFile = new File(Main.RESOURCE_FOLDER, "osm.net.xml.gz");
    Simulation.start(new StringVector(new String[]{"sumo", "-W", "-n", netFile.getAbsolutePath()}));
    VehicleType.copy("DEFAULT_BIKETYPE", "bicycle");
    VehicleType.copy("DEFAULT_VEHTYPE", "passenger");

    List<DepotNode> depotNodes = GeoJsonParser.parseDepotNodes(new File(Main.RESOURCE_FOLDER, "depots.json"));
    ProgressBar routableCheck = new ProgressBar("Checking routability of depot addresses", depotNodes.stream().mapToInt(d -> d.nodes().size()).sum());
    depotNodes = depotNodes.stream()
      .map(d -> {
        DepotNode mapped = d.withoutUnroutableNodes("bicycle");
        routableCheck.stepBy(d.nodes().size());
        return mapped;
      })
      .filter(d -> !d.nodes().isEmpty())
      .toList();

    routableCheck.close();

    MicroHubDeliveryProblem problem = new MicroHubDeliveryProblem();
    problem.init(15000, depotNodes);
    problem.solve();
    problem.writeRouteXML(new File(Main.RESOURCE_FOLDER, "routes_bikes.xml"), "monopoly", "#FFFF00");
    problem.plotSolution(Main.RESOURCE_FOLDER);

    Simulation.close("Done!");
    System.exit(0);
  }

}
