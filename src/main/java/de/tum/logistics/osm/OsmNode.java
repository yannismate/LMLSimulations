package de.tum.logistics.osm;

public record OsmNode(
    double latitude,
    double longitude,
    String street,
    String houseNum,
    String postCode
) {}
