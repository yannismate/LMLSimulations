package de.tum.logistics.osm;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class GeoJsonParser {

    public static List<OsmNode> parseOsmNodes(File geoJsonFile) {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(geoJsonFile)) {
            Type nodeListType = new TypeToken<List<OsmNode>>() {}.getType();
          return gson.fromJson(reader, nodeListType);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
            return Collections.emptyList();
        }

    }

  public static List<DepotNode> parseDepotNodes(File geoJsonFile) {
    Gson gson = new Gson();
    try (FileReader reader = new FileReader(geoJsonFile)) {
      Type nodeListType = new TypeToken<List<DepotNode>>() {}.getType();
      return gson.fromJson(reader, nodeListType);
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
      return Collections.emptyList();
    }
  }

}