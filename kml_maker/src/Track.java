import java.util.*;

/**
 * @author elmot
 */
public class Track {
    public SortedMap<Long,TrackPoint> points = new TreeMap<>();
    public double maxAlt;
    public long maxAltTimestamp;
    public String name;
}
