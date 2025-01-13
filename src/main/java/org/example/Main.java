package org.example;

import org.eclipse.sumo.libsumo.Simulation;
import org.eclipse.sumo.libsumo.StringVector;

public class Main {
    public static void main(String[] args) {
        System.loadLibrary("libtracijni");
        Simulation.start(new StringVector(new String[] {"sumo", "-n", "net.net.xml"}));
        for (int i = 0; i < 5; i++) {
            Simulation.step();
        }
        Simulation.close();
    }
}