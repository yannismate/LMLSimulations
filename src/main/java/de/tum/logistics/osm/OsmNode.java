package de.tum.logistics.osm;

public record OsmNode(
    double latitude,
    double longitude,
    String street,
    String houseNum,
    String postCode
) {
    private final static int METERS_PER_DEGREE = 111_111;

    public double distanceMetersL1(OsmNode other) {
        double distanceCoord = Math.abs(latitude - other.latitude) + Math.abs(longitude - other.longitude);
        return distanceCoord * METERS_PER_DEGREE;
    }
}
