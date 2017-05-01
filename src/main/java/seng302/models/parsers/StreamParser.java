package seng302.models.parsers;


import com.sun.deploy.util.StringUtils;
import javafx.geometry.Point3D;
import jdk.internal.util.xml.impl.Pair;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import seng302.models.Boat;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The purpose of this class is to take in the stream of divided packets so they can be read
 * and parsed in by turning the byte arrays into useful data. There are two public static hashmaps
 * that are threadsafe so the visualiser can always access the latest speed and position available
 * Created by kre39 on 23/04/17.
 */
public class StreamParser extends Thread{

     private static ConcurrentHashMap<Long,Point3D> boatPositions = new ConcurrentHashMap<>();
     private static ConcurrentHashMap<Long,Double> boatSpeeds = new ConcurrentHashMap<>();
     private String threadName;
     private Thread t;
     private static boolean raceStarted = false;
     public static XMLParser xmlObject;
     private static boolean raceFinished = false;
     private static boolean streamStatus = false;
     private static long timeSinceStart = -1;
     private static List<Boat> boats = new ArrayList<>();

     public StreamParser(String threadName){
        this.threadName = threadName;
     }

    /**
     * Used to within threading so when the stream parser thread runs, it will keep looking for a packet to
     * process until it is unable to find anymore packets
     */
    public void run(){
         try {
             System.out.println("START OF STREAM");
             streamStatus = true;
             xmlObject = new XMLParser();
             while (StreamReceiver.packetBuffer == null || StreamReceiver.packetBuffer.size() < 1) {
                 Thread.sleep(1);
             }
             while (StreamReceiver.packetBuffer.peek() != null){
//                 StreamPacket packet = StreamReceiver.packetBuffer.peek();
//                 int delayTime = 1000;
//                 int loopTime = delayTime + 1000;
//                 long sleepTime = 0;
//                 long transitTime = (System.currentTimeMillis()%loopTime - packet.getTimeStamp()%loopTime);
//                 if (transitTime < 0){
//                     transitTime = loopTime + delayTime;
//                 }
//                 if (transitTime < delayTime) {
//                     sleepTime = delayTime - (transitTime);
//                     Thread.sleep(sleepTime);
//                 }
//                 System.out.println(sleepTime);
                 StreamPacket packet = StreamReceiver.packetBuffer.take();
                 parsePacket(packet);
                 Thread.sleep(1);
                 while (StreamReceiver.packetBuffer.peek() == null) {
                     Thread.sleep(1);
                 }
             }
             System.out.println("END OF STREAM");
         } catch (Exception e){
             e.printStackTrace();
         }
     }

    /**
     * Used to start the stream parser thread when multithreading
     */
    public void start () {
        System.out.println("Starting " +  threadName );
        if (t == null) {
            t = new Thread (this, threadName);
            t.start ();
        }
    }

     private static void parsePacket(StreamPacket packet) {
        switch (packet.getType()){
            case HEARTBEAT:
                extractHeartBeat(packet);
                break;
            case RACE_STATUS:
                extractRaceStatus(packet);
                break;
            case DISPLAY_TEXT_MESSAGE:
                extractDisplayMessage(packet);
                break;
            case XML_MESSAGE:
                extractXmlMessage(packet);
                break;
            case RACE_START_STATUS:
                extractRaceStartStatus(packet);
                break;
            case YACHT_EVENT_CODE:
                extractYachtEventCode(packet);
                break;
            case YACHT_ACTION_CODE:
                extractYachtActionCode(packet);
                break;
            case CHATTER_TEXT:
                extractChatterText(packet);
                break;
            case BOAT_LOCATION:
                extractBoatLocation(packet);
                break;
            case MARK_ROUNDING:
                extractMarkRounding(packet);
                break;
            case COURSE_WIND:
                extractCourseWind(packet);
                break;
            case AVG_WIND:
                extractAvgWind(packet);
                break;
            default:
                break;
                //System.out.println(packet.getType().toString());
        }
    }

    /**
     * Extracts the seq num used in the heartbeat packet
     * @param packet Packet parsed in to use the payload
     */
    private static void extractHeartBeat(StreamPacket packet) {
        long heartbeat = bytesToLong(packet.getPayload());
    }

    /**
     * Extracts the useful race status data from race status type packets. This method will also print to the
     * console the current state of the race (if it has started/finished or is about to start), along side
     * this it'll also display the amount of time since the race has started or time till it starts
     * @param packet Packet parsed in to use the payload
     */
    private static void extractRaceStatus(StreamPacket packet){
        byte[] payload = packet.getPayload();
        int messageVersionNo = payload[0];
        long currentTime = bytesToLong(Arrays.copyOfRange(payload,1,7));
        long raceId = bytesToLong(Arrays.copyOfRange(payload,7,11));
        int raceStatus = payload[11];
//        System.out.println("raceStatus = " + raceStatus);
        long expectedStartTime = bytesToLong(Arrays.copyOfRange(payload,12,18));
        DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        long timeTillStart = ((new Date (expectedStartTime)).getTime() - (new Date (currentTime)).getTime())/1000;
        if (timeTillStart > 0) {
            timeSinceStart = timeTillStart;
            System.out.println("Time till start: " + timeTillStart + " Seconds");
        } else {
            if (raceStatus == 4 || raceStatus == 8){
                raceFinished = true;
                raceStarted = false;
                System.out.println("RACE HAS FINISHED");
            } else if (!raceStarted){
                raceStarted = true;
                raceFinished = false;
                System.out.println("RACE HAS STARTED");
            }
            System.out.println("Time since start: " + -1 * timeTillStart + " Seconds");
            timeSinceStart = timeTillStart;
        }
        long windDir = bytesToLong(Arrays.copyOfRange(payload,18,20));
        long windSpeed = bytesToLong(Arrays.copyOfRange(payload,20,22));
        int noBoats = payload[22];
        int raceType = payload[23];
        ArrayList<String> boatStatuses = new ArrayList<>();
        for (int i = 0; i < noBoats; i++){
            String boatStatus = "SourceID: " + bytesToLong(Arrays.copyOfRange(payload,24 + (i * 20),28+ (i * 20)));
            boatStatus += "\nBoat Status: " + (int)payload[28 + (i * 20)];
            boatStatus += "\nLegNumber: " + (int)payload[29 + (i * 20)];
            boatStatus += "\nPenaltiesAwarded: " + (int)payload[29 + (i * 20)];
            boatStatus += "\nPenaltiesServed: " + (int)payload[30 + (i * 20)];
            boatStatus += "\nEstTimeAtNextMark: " + bytesToLong(Arrays.copyOfRange(payload,31 + (i * 20),37+ (i * 20)));
            boatStatus += "\nEstTimeAtFinish: " + bytesToLong(Arrays.copyOfRange(payload,37 + (i * 20),43+ (i * 20)));
            boatStatuses.add(boatStatus);
        }
    }

    /**
     * Used to extract the messages passed through with the display message packet
     * @param packet Packet parsed in to use the payload
     */
    private static void extractDisplayMessage(StreamPacket packet){
        byte[] payload = packet.getPayload();
        int messageVersionNo = payload[0];
        int numOfLines = payload[3];
        int totalLen = 0;
        for (int i = 0; i < numOfLines; i++){
            int lineNum = payload[4 + totalLen];
            int textLength = payload[5 + totalLen];
            byte[] messageTextBytes = Arrays.copyOfRange(payload,6 + totalLen,6 + textLength + totalLen);
            String messageText = new String(messageTextBytes);
            totalLen += 2 + textLength;
        }
    }

    /**
     * Used to read in the xml data. Will call the specific methods to create the course and boats
     * @param packet Packet parsed in to use the payload
     */
    private static void extractXmlMessage(StreamPacket packet){

        byte[] payload = packet.getPayload();

        int messageType = payload[9];
        long messagelength = bytesToLong(Arrays.copyOfRange(payload,12,14));
        String xmlMessage = new String((Arrays.copyOfRange(payload,14,(int) (14 + messagelength)))).trim();
        //System.out.println("xmlMessage2 = " + xmlMessage);

        //Create XML document Object
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = null;
        Document doc = null;
        try {
            db = dbf.newDocumentBuilder();
            doc = db.parse(new InputSource(new StringReader(xmlMessage)));
        } catch (ParserConfigurationException | IOException | SAXException e) {
            e.printStackTrace();
        }

        xmlObject.constructXML(doc, messageType);
    }

    /**
     * Extracts the race start status from the packet, currently is unused within the app but
     * is here for potential future use
     * @param packet Packet parsed in to use the payload
     */
    private static void extractRaceStartStatus(StreamPacket packet){
        byte[] payload = packet.getPayload();
        int messageVersionNo = payload[0];
        long timeStamp = bytesToLong(Arrays.copyOfRange(payload,1,7));
        long raceStartTime = bytesToLong(Arrays.copyOfRange(payload,9,15));
        long raceId = bytesToLong(Arrays.copyOfRange(payload,15,19));
        int notificationType = payload[19];
    }

    /**
     * When a yacht event occurs this will parse the byte array to retrieve the necessary info,
     * currently unused
     * @param packet Packet parsed in to use the payload
     */
    private static void extractYachtEventCode(StreamPacket packet){
        byte[] payload = packet.getPayload();
        int messageVersionNo = payload[0];
        long timeStamp = bytesToLong(Arrays.copyOfRange(payload,1,7));
        long raceId = bytesToLong(Arrays.copyOfRange(payload,9,13));
        long subjectId = bytesToLong(Arrays.copyOfRange(payload,13,17));
        long incidentId = bytesToLong(Arrays.copyOfRange(payload,17,21));
        int eventId = payload[21];
    }

    /**
     * When a yacht action occurs this will parse the parse the byte array to retrieve the necessary info,
     * currently unused
     * @param packet Packet parsed in to use the payload
     */
    private static void extractYachtActionCode(StreamPacket packet){
        byte[] payload = packet.getPayload();
        int messageVersionNo = payload[0];
        long timeStamp = bytesToLong(Arrays.copyOfRange(payload,1,7));
        long subjectId = bytesToLong(Arrays.copyOfRange(payload,9,13));
        long incidentId = bytesToLong(Arrays.copyOfRange(payload,13,17));
        int eventId = payload[17];
//        System.out.println("eventId = " + eventId);
    }

    /**
     * Strips the message from the chatter text type packets, currently the message is unused
     * @param packet Packet parsed in to use the payload
     */
    private static void extractChatterText(StreamPacket packet){
        byte[] payload = packet.getPayload();
        int messageVersionNo = payload[0];
        int messageType = payload[1];
        int length = payload[2];
        String message = new String(Arrays.copyOfRange(payload,3,3 + length));
    }

    /**
     * Used to breakdown the boatlocation packets so the boat coordinates, id and groundspeed are all used
     * All the other extra data is still being read and translated however is unused.
     * @param packet Packet parsed in to use the payload
     */
    private static void extractBoatLocation(StreamPacket packet){
        byte[] payload = packet.getPayload();
        byte deviceType = payload[15];
        byte[] seqBytes = Arrays.copyOfRange(payload,11,15);
        byte[] latBytes = Arrays.copyOfRange(payload,16,20);
        byte[] lonBytes = Arrays.copyOfRange(payload,20,24);
        byte[] boatIdBytes = Arrays.copyOfRange(payload,7,11);
        byte[] headingBytes = Arrays.copyOfRange(payload,28,30);
        byte[] groundSpeedBytes = Arrays.copyOfRange(payload,38,40);

        long timeStamp = bytesToLong(Arrays.copyOfRange(payload,1,7));
//        int boatSeq =  ByteBuffer.wrap(seqBytes).getInt();
        long seq = bytesToLong(seqBytes);
        long boatId = bytesToLong(boatIdBytes);
        long lat = bytesToLong(latBytes);
        long lon = bytesToLong(lonBytes);
        long heading = bytesToLong(headingBytes);
//        long speed = bytesToLong(speedBytes);
        double groundSpeed = bytesToLong(groundSpeedBytes)/1000.0;
        short s = (short) ((groundSpeedBytes[1] & 0xFF) << 8 | (groundSpeedBytes[0] & 0xFF));
        if ((int)deviceType == 1 || (int)deviceType == 3){
//            System.out.println("boatId = " + boatId);
//            System.out.println("deviceType = " + (long)deviceType);
//            System.out.println("seq = " + seq);
            //needs to be validated
            Point3D point = new Point3D(((180d * (double)lat)/Math.pow(2,31)),((180d *(double)lon)/Math.pow(2,31)),(double)heading);
            boatPositions.put(boatId, point);
            boatSpeeds.put(boatId, groundSpeed);
//            boatPositions.replace(boatId, point);
//            boatSpeeds.replace(boatId, groundSpeed);
//            System.out.println("lon = " + ((180d * (double)lon)/Math.pow(2,31)));
//            System.out.println("lat = " +  ((180d *(double)lat)/Math.pow(2,31)));
        }
    }



    private static void extractMarkRounding(StreamPacket packet){
        byte[] payload = packet.getPayload();
        int messageVersionNo = payload[0];
        long timeStamp = bytesToLong(Arrays.copyOfRange(payload,1,7));
        long raceId = bytesToLong(Arrays.copyOfRange(payload,9,13));
        long subjectId = bytesToLong(Arrays.copyOfRange(payload,13,17));
        int boatStatus = payload[17];
        int roundingSide = payload[18];
        int markType = payload[19];
        int markId = payload[20];
    }

    private static void extractCourseWind(StreamPacket packet){
        byte[] payload = packet.getPayload();
        int messageVersionNo = payload[0];
        int selectedWindId = payload[1];
        int loopCount = payload[2];
        ArrayList<String> windInfo = new ArrayList<>();
        for (int i = 0; i < loopCount; i++){
            String wind = "WindId: " + payload[3 + (20 * i)];
            wind += "\nTime: " + bytesToLong(Arrays.copyOfRange(payload,4 + (20 * i),10 + (20 * i)));
            wind += "\nRaceId: " + bytesToLong(Arrays.copyOfRange(payload,10 + (20 * i),14 + (20 * i)));
            wind += "\nWindDirection: " + bytesToLong(Arrays.copyOfRange(payload,14 + (20 * i),16 + (20 * i)));
            wind += "\nWindSpeed: " + bytesToLong(Arrays.copyOfRange(payload,16 + (20 * i),18 + (20 * i)));
            wind += "\nBestUpWindAngle: " + bytesToLong(Arrays.copyOfRange(payload,18 + (20 * i),20 + (20 * i)));
            wind += "\nBestDownWindAngle: " + bytesToLong(Arrays.copyOfRange(payload,20 + (20 * i),22 + (20 * i)));
            wind += "\nFlags: " + String.format("%8s", Integer.toBinaryString(payload[22 + (20 * i)] & 0xFF)).replace(' ', '0');
            windInfo.add(wind);
        }
    }

    private static void extractAvgWind(StreamPacket packet){
        byte[] payload = packet.getPayload();
        int messageVersionNo = payload[0];
        long timeStamp = bytesToLong(Arrays.copyOfRange(payload,1,7));
        long rawPeriod = bytesToLong(Arrays.copyOfRange(payload,7,9));
        long rawSamplePeriod = bytesToLong(Arrays.copyOfRange(payload,9,11));
        long period2 = bytesToLong(Arrays.copyOfRange(payload,11,13));
        long speed2 = bytesToLong(Arrays.copyOfRange(payload,13,15));
        long period3 = bytesToLong(Arrays.copyOfRange(payload,15,17));
        long speed3 = bytesToLong(Arrays.copyOfRange(payload,17,19));
        long period4 = bytesToLong(Arrays.copyOfRange(payload,19,21));
        long speed4 = bytesToLong(Arrays.copyOfRange(payload,21,23));
    }

    /**
     * takes an array of up to 7 bytes and returns a positive
     * long constructed from the input bytes
     *
     * @return a positive long if there is less than 7 bytes -1 otherwise
     */
    private static long bytesToLong(byte[] bytes){
        long partialLong = 0;
        int index = 0;
        for (byte b: bytes){
            if (index > 6){
                return -1;
            }
            partialLong = partialLong | (b & 0xFFL) << (index * 8);
            index++;
        }
        return partialLong;
    }

    /**
     * returns false if race not started, true otherwise
     *
     * @return race started status
     */
    public static boolean isRaceStarted() {
        return raceStarted;
    }

    /**
     * returns false if stream not connected, true otherwise
     *
     * @return stream started status
     */
    public static boolean isStreamStatus() {
        return streamStatus;
    }

    /**
     * returns race timer
     *
     * @return race timer in long
     */
    public static long getTimeSinceStart() {
        return timeSinceStart;
    }

    /**
     * return false if race not finished, true otherwise
     *
     * @return race finished status
     */
    public static boolean isRaceFinished() {
        return raceFinished;
    }

    /**
     * return list of boats from the server
     *
     * @return list of boats
     */
    public static List<Boat> getBoats() {
        return boats;
    }

    public static ConcurrentHashMap<Long, Point3D> getBoatPositions() {
        return boatPositions;
    }

    public static ConcurrentHashMap<Long, Double> getBoatSpeeds() {
        return boatSpeeds;
    }

    public static XMLParser getXmlObject() {
        return xmlObject;
    }
}

