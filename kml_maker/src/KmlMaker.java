import com.jhlabs.map.Datum;
import com.jhlabs.map.GeodeticPosition;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

/**
 * @author elmot
 */
public class KmlMaker {
    public static final String DIRTEAM = "D:\\projects\\rr\\Teams";
    public static final String DIRSONDE = "D:\\projects\\rr\\KP's";

    private static final Calendar OziBaseDay;
    public static final long startTime;
    public static final long endTime;

    static {
        OziBaseDay = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        OziBaseDay.set(1899, Calendar.DECEMBER, 30, -4, 0, 0);
        GregorianCalendar calStart = new GregorianCalendar(TimeZone.getTimeZone("MSK"));
        calStart.set(2014, GregorianCalendar.OCTOBER, 25, 5, 0);
//        startTime = calStart.getTimeInMillis();
        startTime = 0;
        GregorianCalendar calEnd = new GregorianCalendar(TimeZone.getTimeZone("MSK"));
        calStart.set(2014, GregorianCalendar.OCTOBER, 26, 0, 0);
//        endTime = calEnd.getTimeInMillis();
        endTime = Long.MAX_VALUE;
    }

    public static final Comparator<? super Track> TRACK_SORTER = (Track t1, Track t2) -> t1.name.compareToIgnoreCase(t2.name);
    public static final String GX_SCHEMA = "http://www.google.com/kml/ext/2.2";

    public static void main(String[] args) throws InterruptedException, IOException, XMLStreamException {
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        List<Track> tracksSondes = Collections.synchronizedList(new ArrayList<>());
        List<Track> tracksTeams = Collections.synchronizedList(new ArrayList<>());
        submitTrackParsers(executorService, tracksTeams, new File(DIRTEAM), Track.class, null);
        submitTrackParsers(executorService, tracksSondes, new File(DIRSONDE), SondeTrack.class, /*Datum.get("S-42")*/null);
        executorService.shutdown();
        executorService.awaitTermination(50, TimeUnit.SECONDS);
        System.out.println("tracksTeams.size = " + tracksTeams.size());
        System.out.println("tracksSondes.size = " + tracksSondes.size());
        Collections.sort(tracksSondes, TRACK_SORTER);
        Collections.sort(tracksTeams, TRACK_SORTER);


        UnaryOperator<String> sondeStyle = (name) -> name.contains("32") ? "../doc.kml#rescue" : name.contains("35") ? "../doc.kml#sondeBonus" : "../doc.kml#sonde";
        writeFolder("kml/staticSondes.kml", "Balloons routes", tracksSondes, new SondeStaticWriter(sondeStyle));
        writeFolder("kml/dynSondes.kml", "Balloons animation", tracksSondes, new SondeDynWriter(sondeStyle));

        writeFolder("kml/staticTeams.kml", "Team Routes", tracksTeams, new TeamStaticWriter((name) -> "../doc.kml#team"));
        writeFolder("kml/dynTeams.kml", "Team Animations", tracksTeams, new TeamDynWriter((name) -> "../doc.kml#team"));


        File allKml = new File("doc.kml");


        Runtime.getRuntime().exec(new String[]{"C:\\Program Files (x86)\\Google\\Google Earth\\client\\googleearth.exe", allKml.getAbsolutePath()});
    }

    private static void writeFolder(String fileName, String folderName, List<Track> tracks, TrackWriter trackWriter) throws XMLStreamException, IOException {
        File file = new File(fileName);
        try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file))) {
            XMLStreamWriter out = XMLOutputFactory.newInstance().createXMLStreamWriter(outputStream);
            out.writeStartDocument("1.0");
            out.writeStartElement("Folder");
            out.writeDefaultNamespace("http://www.opengis.net/kml/2.2");
            out.writeNamespace("gx", GX_SCHEMA);
            out.writeNamespace("xsi", XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI);
            out.writeAttribute("xsi", XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "schemaLocation", "http://www.opengis.net/kml/2.2 http://schemas.opengis.net/kml/2.2.0/ogckml22.xsd");
            writeTextTag(out, "name", folderName);
            for (Track track : tracks) {
                out.writeStartElement("Placemark");

                writeTextTag(out, "name", track.name);
                writeTextTag(out, "description", track.name);
                trackWriter.writeHeader(out, track);
                for (TrackPoint trackPoint : track.points.values()) {
                    trackWriter.writeTrackPointStart(out, trackPoint);
                    trackWriter.writeTrackPointEnd(out, trackPoint);
                }
                trackWriter.writeFooter(out, track);
                out.writeEndElement();
            }
            out.writeEndElement();
            out.writeEndDocument();
        }
    }

    private static void writeKml(File file, List<Track> tracksSondes, List<Track> tracksTeams) throws IOException, XMLStreamException {
        try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file))) {
            XMLStreamWriter out = XMLOutputFactory.newInstance().createXMLStreamWriter(outputStream);
            out.writeStartDocument("1.0");
            out.writeStartElement("kml");
            out.writeDefaultNamespace("http://www.opengis.net/kml/2.2");
            out.writeNamespace("gx", GX_SCHEMA);
            out.writeNamespace("xsi", XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI);
            out.writeAttribute("xsi", XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "schemaLocation", "http://www.opengis.net/kml/2.2 http://schemas.opengis.net/kml/2.2.0/ogckml22.xsd");
            out.writeStartElement("Document");
            writeTextTag(out, "name", "RR 25-Oct-2014 animation");
            out.writeStartElement("gx", "duration", GX_SCHEMA);
            out.writeCharacters("2000");
            out.writeEndElement();

            writeStyle(out, "team", "Af6799cc", "files/team.png");
            writeStyle(out, "sonde", "Afcc9967", "files/balloon.png");
            writeStyle(out, "sondeBonus", "AfccCC67", "files/balloonBonus.png");
            writeStyle(out, "resque", "Af20CC67", "files/team.png");
            writeTrackFolder(out, tracksSondes, true, "Balloons", (name) -> name.contains("32") ? "#rescue" : name.contains("35") ? "#sondeBonus" : "#sonde");
            writeTrackFolder(out, tracksTeams, false, "Teams", (name) -> "team");
            out.writeEndElement();
            out.writeEndElement();
            out.writeEndDocument();


        }
    }


    private static void writeTrackFolder(XMLStreamWriter out, List<Track> tracks, boolean sonde, String folderName,
                                         UnaryOperator<String> nameToStyle) throws XMLStreamException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.UK);
        out.writeStartElement("Folder");
        writeTextTag(out, "name", folderName);
        for (Track track : tracks) {
            out.writeStartElement("Placemark");
            writeTextTag(out, "name", track.name);
            String styleUrl = nameToStyle.apply(track.name);
            writeTextTag(out, "styleUrl", styleUrl);
/*
            out.writeStartElement("LineString");
            if (sonde) {
                writeTextTag(out, "altitudeMode", "absolute");
            }
            out.writeStartElement("coordinates");
            for (TrackPoint trackPoint : track.points.values()) {
                out.writeCharacters(String.valueOf(trackPoint.lon));
                out.writeCharacters(",");
                out.writeCharacters(String.valueOf(trackPoint.lat));
                out.writeCharacters(",");
                out.writeCharacters(String.valueOf(trackPoint.alt));
                out.writeCharacters(" ");
}

            out.writeEndElement();
            out.writeEndElement();
*/
            out.writeStartElement("gx", "Track", GX_SCHEMA);
            if (sonde) {
                writeTextTag(out, "altitudeMode", "absolute");
            }
            for (TrackPoint trackPoint : track.points.values()) {
                writeTextTag(out, "when", simpleDateFormat.format(new Date(trackPoint.timestamp)));
                out.writeStartElement("gx", "coord", GX_SCHEMA);
                out.writeCharacters(String.valueOf(trackPoint.lon));
                out.writeCharacters(" ");
                out.writeCharacters(String.valueOf(trackPoint.lat));
                out.writeCharacters(" ");
                out.writeCharacters(String.valueOf(trackPoint.alt));
                out.writeEndElement();
            }
            out.writeEndElement();
            out.writeEndElement();
        }
        out.writeEndElement();
    }

    private static void writeStyle(XMLStreamWriter out, String id, String color, String iconUrl) throws XMLStreamException {
        out.writeStartElement("Style");
        out.writeAttribute("id", id);
        out.writeStartElement("LineStyle");
        writeTextTag(out, "color", color);
        writeTextTag(out, "width", "2");
        out.writeEndElement();
        out.writeStartElement("IconStyle");
        out.writeStartElement("Icon");
        writeTextTag(out, "href", iconUrl);
        out.writeEndElement();
        out.writeEndElement();
        out.writeEndElement();
    }

    private static void writeTextTag(XMLStreamWriter streamWriter, String localName, String text) throws XMLStreamException {
        streamWriter.writeStartElement(localName);
        streamWriter.writeCharacters(text);
        streamWriter.writeEndElement();
    }

    private static <T extends Track> void submitTrackParsers(ExecutorService executorService, List<Track> tracks, File pltDir, Class<T> trackClass, Datum from) throws InterruptedException {
        File[] plts = pltDir.listFiles((dir, name) -> name.endsWith(".plt") /*&& !name.startsWith("41")*/);
        for (File plt : plts) {
            executorService.submit((Runnable) () -> tracks.add(readPlt(plt, trackClass, from)));
        }
    }

    private static <TrackT extends Track> TrackT readPlt(File pltFile, Class<TrackT> trackClass, Datum from) {
        String name = null;
        File srcKml = new File(pltFile.getParentFile(), pltFile.getName().replace(".plt", ".kml"));
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(srcKml);
            name = (String) XPathFactory.newInstance().newXPath().compile("//Document/Folder/name").evaluate(document, XPathConstants.STRING);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(pltFile)))) {
            reader.readLine();
            reader.readLine();
            reader.readLine();
            reader.readLine();
            String descriptor = reader.readLine();
            if (name == null) {
                name = descriptor.split(",")[3];
            }
            reader.readLine();
            TrackT track = trackClass.newInstance();
            track.name = name;
            for (String line; ((line = reader.readLine())) != null; ) {
                if (line.isEmpty()) continue;
                String[] fields = line.split(",");
                GregorianCalendar cal = (GregorianCalendar) OziBaseDay.clone();
                double oziTime = parseNumber(fields[4]);
                cal.add(Calendar.DAY_OF_YEAR, (int) Math.floor(oziTime));
                long timeStamp = cal.getTimeInMillis() + (long) (24.0D * 3600000 * (oziTime - Math.floor(oziTime)));
                if (timeStamp >= startTime && timeStamp <= endTime) {
                    double lat = parseNumber(fields[0]);
                    double lon = parseNumber(fields[1]);
                    double alt = parseNumber(fields[3])/* * 0.3048 + 88*/;
                    if (from != null) {
                        GeodeticPosition geodeticPosition = new GeodeticPosition(lat, lon, alt);
                        geodeticPosition = from.transformToWGS84(geodeticPosition);
                        lat = geodeticPosition.lat;
                        lon = geodeticPosition.lon;
                        alt = geodeticPosition.h;
                    }
                    TrackPoint trackPoint = new TrackPoint(lat, lon, alt, timeStamp);
                    track.points.put(timeStamp, trackPoint);
                }
            }
            return track;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static double parseNumber(String field) {
        return Double.parseDouble(field.trim());
    }

    private static abstract class TrackWriter {

        protected final UnaryOperator<String> name2Style;

        protected TrackWriter(UnaryOperator<String> name2Style) {
            this.name2Style = name2Style;
        }

        public abstract void writeHeader(XMLStreamWriter writer, Track track) throws XMLStreamException;

        public abstract void writeFooter(XMLStreamWriter writer, Track track) throws XMLStreamException;

        public abstract void writeTrackPointStart(XMLStreamWriter writer, TrackPoint trackPoint) throws XMLStreamException;

        public abstract void writeTrackPointEnd(XMLStreamWriter writer, TrackPoint trackPoint) throws XMLStreamException;

    }

    private static class TeamStaticWriter extends TrackWriter {

        private TeamStaticWriter(UnaryOperator<String> name2Style) {
            super(name2Style);
        }

        @Override
        public void writeHeader(XMLStreamWriter writer, Track track) throws XMLStreamException {
            writeTextTag(writer, "styleUrl", name2Style.apply(track.name));
            writer.writeStartElement("LineString");
            writer.writeStartElement("coordinates");
        }

        @Override
        public void writeFooter(XMLStreamWriter writer, Track track) throws XMLStreamException {
            writer.writeEndElement();
            writer.writeEndElement();
        }

        @Override
        public void writeTrackPointStart(XMLStreamWriter writer, TrackPoint trackPoint) throws XMLStreamException {
            writer.writeCharacters(String.valueOf(trackPoint.lon));
            writer.writeCharacters(",");
            writer.writeCharacters(String.valueOf(trackPoint.lat));
        }

        @Override
        public void writeTrackPointEnd(XMLStreamWriter writer, TrackPoint trackPoint) throws XMLStreamException {
            writer.writeCharacters(" ");
        }
    }

    private static class SondeStaticWriter extends TeamStaticWriter {

        private SondeStaticWriter(UnaryOperator<String> name2Style) {
            super(name2Style);
        }

        @Override
        public void writeHeader(XMLStreamWriter writer, Track track) throws XMLStreamException {
            writeTextTag(writer, "styleUrl", name2Style.apply(track.name));
            writer.writeStartElement("LineString");
            writeTextTag(writer, "altitudeMode", "absolute");
            writer.writeStartElement("coordinates");
        }

        @Override
        public void writeTrackPointStart(XMLStreamWriter writer, TrackPoint trackPoint) throws XMLStreamException {
            super.writeTrackPointStart(writer, trackPoint);
            writer.writeCharacters(",");
            writer.writeCharacters(String.valueOf(trackPoint.alt));
        }
    }

    private static class TeamDynWriter extends TrackWriter {

        private TeamDynWriter(UnaryOperator<String> name2Style) {
            super(name2Style);
        }

        private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.UK);

        @Override
        public void writeHeader(XMLStreamWriter writer, Track track) throws XMLStreamException {
            writeTextTag(writer, "styleUrl", name2Style.apply(track.name));
            writer.writeStartElement("gx", "Track", GX_SCHEMA);
        }

        @Override
        public void writeFooter(XMLStreamWriter writer, Track track) throws XMLStreamException {
            writer.writeEndElement();
        }

        @Override
        public void writeTrackPointStart(XMLStreamWriter writer, TrackPoint trackPoint) throws XMLStreamException {
            writeTextTag(writer, "when", simpleDateFormat.format(new Date(trackPoint.timestamp)));
            writer.writeStartElement("gx", "coord", GX_SCHEMA);
            writer.writeCharacters(String.valueOf(trackPoint.lon));
            writer.writeCharacters(" ");
            writer.writeCharacters(String.valueOf(trackPoint.lat));
        }

        @Override
        public void writeTrackPointEnd(XMLStreamWriter writer, TrackPoint trackPoint) throws XMLStreamException {
            writer.writeEndElement();
        }
    }

    private static class SondeDynWriter extends TeamDynWriter {
        private SondeDynWriter(UnaryOperator<String> name2Style) {
            super(name2Style);
        }

        @Override
        public void writeHeader(XMLStreamWriter writer, Track track) throws XMLStreamException {
            super.writeHeader(writer, track);
            writeTextTag(writer, "altitudeMode", "absolute");
        }

        @Override
        public void writeTrackPointStart(XMLStreamWriter writer, TrackPoint trackPoint) throws XMLStreamException {
            super.writeTrackPointStart(writer, trackPoint);
            writer.writeCharacters(" ");
            writer.writeCharacters(String.valueOf(trackPoint.alt));
        }

        @Override
        public void writeFooter(XMLStreamWriter writer, Track track) throws XMLStreamException {
            TrackPoint last = track.points.get(track.points.lastKey());
            TrackPoint first = track.points.get(track.points.firstKey());
            TrackPoint fakePoint = new TrackPoint(last.lat, last.lon, last.alt, first.timestamp + 12L * 3600000);
            writeTrackPointStart(writer, fakePoint);
            writeTrackPointEnd(writer, fakePoint);
            super.writeFooter(writer, track);
        }
    }
}
