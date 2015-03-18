/**
 * @author elmot
 */
public class TrackPoint {

    public final double lat;
    public final double lon;
    public final double alt;
    public final long timestamp;

    public TrackPoint(double lat, double lon, double alt, long timestamp) {
        this.lat = lat;
        this.lon = lon;
        this.alt = alt;
        this.timestamp = timestamp;
    }
}
