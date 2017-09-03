package seng302.gameServer;

import seng302.gameServer.messages.*;
import seng302.model.GeoPoint;
import seng302.model.Player;
import seng302.model.PolarTable;
import seng302.model.ServerYacht;
import seng302.model.mark.CompoundMark;
import seng302.utilities.GeoUtility;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.LocalDateTime;
import java.util.*;

/**
 * A class describing the overall server, which creates and collects server threads for each client
 * Created by wmu16 on 13/07/17.
 */
public class MainServerThread implements Runnable, ClientConnectionDelegate {

    private static final int PORT = 4942;
    private static final Integer CLIENT_UPDATES_PER_SECOND = 60;
    private static final int LOG_LEVEL = 1;
    private static final int WARNING_TIME = 10 * -1000;
    private static final int PREPATORY_TIME = 5 * -1000;
    public static final int TIME_TILL_START = 10 * 1000;

    private static final int MAX_WIND_SPEED = 12000;
    private static final int MIN_WIND_SPEED = 8000;

    public static int windSpeed = 1000;

    private boolean terminated;

    private Thread thread;

    private ServerSocket serverSocket = null;
    private ArrayList<ServerToClientThread> serverToClientThreads = new ArrayList<>();

    public MainServerThread() {
        new GameState("localhost");
        try {
            serverSocket = new ServerSocket(PORT);
        } catch (IOException e) {
            serverLog("IO error in server thread handler upon trying to make new server socket", 0);
        }
        PolarTable.parsePolarFile(getClass().getResourceAsStream("/config/acc_polars.csv"));
        GameState.addMarkPassListener(this::broadcastMessage);
        terminated = false;
        thread = new Thread(this, "MainServer");
        startUpdatingWind();
        thread.start();
    }


    public void run() {

        new HeartbeatThread(this);
        new ServerListenThread(serverSocket, this);

        //You should handle interrupts in some way, so that the thread won't keep on forever if you exit the app.
        while (!terminated) {
            System.out.println("CUNT GF" + GameState.getCurrentStage());
            try {
                Thread.sleep(1000 / CLIENT_UPDATES_PER_SECOND);
            } catch (InterruptedException e) {
                serverLog("Interrupted exception in Main Server Thread thread sleep", 1);
            }
            if (GameState.getCurrentStage() == GameStages.LOBBYING && GameState
                .getCustomizationFlag()) {
                // TODO: 16/08/17 ajm412: This can probably be done in a nicer way via those fancy functional interfaces.
                for (ServerToClientThread thread : serverToClientThreads) {
                    thread.sendSetupMessages();
                }
                GameState.resetCustomizationFlag();
            }

            if (GameState.getCurrentStage() == GameStages.PRE_RACE) {
                updateClients();
            }

            //RACING
            if (GameState.getCurrentStage() == GameStages.RACING) {
                updateClients();
            }

            //FINISHED
            else if (GameState.getCurrentStage() == GameStages.FINISHED) {
                broadcastMessage(makeRaceStatusMessage());
                System.out.println("BUT I WAS HERE CUNTFACE");
                try {
                    Thread.sleep(100); //Hackish fix to make sure all threads have broadcasted
                    terminate();
                } catch (InterruptedException ie) {
                    serverLog("Thread interrupted while waiting to terminate clients", 1);
                }
            }
        }
        try {
            for (ServerToClientThread serverToClientThread : serverToClientThreads) {
                serverToClientThread.terminate();
            }
            serverSocket.close();
            System.out.println("closed");
        } catch (IOException e) {
            System.out.println("IO error in server thread handler upon closing socket");
        }
    }

    public void updateClients() {
        for (ServerToClientThread serverToClientThread : serverToClientThreads) {
            serverToClientThread.sendBoatLocationPackets();
        }
    }

    private void broadcastMessage(Message message) {
        for (ServerToClientThread serverToClientThread : serverToClientThreads) {
            serverToClientThread.sendMessage(message);
        }
    }

    private static void updateWind(){
        Integer direction = GameState.getWindDirection().intValue();
        Integer windSpeed = GameState.getWindSpeedMMS().intValue();

        Random random = new Random();

        if (Math.floorMod(random.nextInt(), 2) == 0){
            direction += random.nextInt(4);
            windSpeed += random.nextInt(20) + 50;
        }
        else{
            direction -= random.nextInt(4);
            windSpeed -= random.nextInt(20) + 50;
        }

        direction = Math.floorMod(direction, 360);

        if (windSpeed > MAX_WIND_SPEED){
            windSpeed -= random.nextInt(1000);
        }

        if (windSpeed <= MIN_WIND_SPEED){
            windSpeed += random.nextInt(1000);
        }

        GameState.setWindSpeed(Double.valueOf(windSpeed));
        GameState.setWindDirection(direction.doubleValue());
    }

    private static void startUpdatingWind(){
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateWind();
            }
        }, 0, 500);
    }


    static void serverLog(String message, int logLevel) {
        if (logLevel <= LOG_LEVEL) {
            System.out.println(
                "[SERVER " + LocalDateTime.now().toLocalTime().toString() + "] " + message);
        }
    }

    /**
     * A client has tried to connect to the server
     *
     * @param serverToClientThread The player that connected
     */
    @Override
    public void clientConnected(ServerToClientThread serverToClientThread) {
        serverLog("Player Connected From " + serverToClientThread.getThread().getName(), 0);
        if (serverToClientThreads.size() == 0) { //Sets first client as host.
            serverToClientThread.setAsHost();
        }
        serverToClientThreads.add(serverToClientThread);
        serverToClientThread.addConnectionListener(() -> {
            for (ServerToClientThread thread : serverToClientThreads) {
                thread.sendSetupMessages();
            }
        });
        serverToClientThread.addDisconnectListener(this::clientDisconnected);
    }

    /**
     * A player has left the game, remove the player from the GameState
     *
     * @param player The player that left
     */
    @Override
    public void clientDisconnected(Player player) {
//        try {
//            player.getSocket().close();
//        } catch (Exception e) {
//            serverLog("Cannot disconnect the socket for the disconnected player.", 0);
//        }
        serverLog("Player " + player.getYacht().getSourceId() + "'s socket disconnected", 0);
        GameState.removeYacht(player.getYacht().getSourceId());
        GameState.removePlayer(player);
        ServerToClientThread closedConnection = null;
        for (ServerToClientThread serverToClientThread : serverToClientThreads) {
            if (serverToClientThread.getSocket() == player.getSocket()) {
                closedConnection = serverToClientThread;
            } else if (GameState.getCurrentStage() != GameStages.RACING){
                serverToClientThread.sendSetupMessages();
            }
        }
        serverToClientThreads.remove(closedConnection);
        closedConnection.terminate();
    }

    public void startGame() {
        initialiseBoatPositions();
        Timer t = new Timer();

        t.schedule(new TimerTask() {
            @Override
            public void run() {
                broadcastMessage(makeRaceStatusMessage());
                if (GameState.getCurrentStage() == GameStages.PRE_RACE || GameState.getCurrentStage() == GameStages.LOBBYING) {
                    broadcastMessage(makeRaceStartMessage());
                }
            }
        }, 0, 500);
    }


    private RaceStartStatusMessage makeRaceStartMessage() {
        Long raceStartTime = GameState.getStartTime();

        return new RaceStartStatusMessage(1, raceStartTime ,
                1, RaceStartNotificationType.SET_RACE_START_TIME);
    }

    private RaceStatusMessage makeRaceStatusMessage() {
        // variables taken from GameServerThread

        List<BoatSubMessage> boatSubMessages = new ArrayList<>();
        RaceStatus raceStatus;

        for (Player player : GameState.getPlayers()) {
            ServerYacht y = player.getYacht();
            System.out.println(y.getBoatStatus());
            BoatSubMessage m = new BoatSubMessage(y.getSourceId(), y.getBoatStatus(),
                y.getLegNumber(),
                0, 0, 1234L,
                1234L);
            boatSubMessages.add(m);
        }

        long timeTillStart = System.currentTimeMillis() - GameState.getStartTime();

        if (GameState.getCurrentStage() == GameStages.LOBBYING) {
            raceStatus = RaceStatus.PRESTART;
        } else if (GameState.getCurrentStage() == GameStages.PRE_RACE) {
            raceStatus = RaceStatus.PRESTART;

            if (timeTillStart > WARNING_TIME) {
                raceStatus = RaceStatus.WARNING;
            }

            if (timeTillStart > PREPATORY_TIME) {
                raceStatus = RaceStatus.PREPARATORY;
            }
        } else if (GameState.getCurrentStage() == GameStages.FINISHED) {
            System.out.println("WHAT THE FUCKING FUCK");
            raceStatus = RaceStatus.TERMINATED;
        } else {
            raceStatus = RaceStatus.STARTED;
        }

        return new RaceStatusMessage(1, raceStatus, GameState.getStartTime(),
            GameState.getWindDirection(),
            GameState.getWindSpeedMMS().longValue(), GameState.getPlayers().size(),
            RaceType.MATCH_RACE, 1, boatSubMessages);
    }

    public void terminate() {
        System.out.println("done");
        terminated = true;
    }

    /**
     * Initialise boats to specific spaced out geopoints behind starting line.
     */
    private void initialiseBoatPositions() {
        // Getting the start line compound marks
//        if (gameClient== null) {
//            return;
//        }
        CompoundMark cm = GameState.getMarkOrder().getMarkOrder().get(0);
        GeoPoint startMark1 = cm.getSubMark(1);
        GeoPoint startMark2 = cm.getSubMark(2);

        // Calculating midpoint
        Double perpendicularAngle = GeoUtility.getBearing(startMark1, startMark2);
        Double length = GeoUtility.getDistance(startMark1, startMark2);
        GeoPoint midpoint = GeoUtility.getGeoCoordinate(startMark1, perpendicularAngle, length / 2);

        // Setting each boats position side by side
        double DISTANCE_FACTOR = 50.0;  // distance apart in meters
        int boatIndex = 0;
        for (ServerYacht yacht : GameState.getYachts().values()) {
            int distanceApart = boatIndex / 2;

            if (boatIndex % 2 == 1 && boatIndex != 0) {
                distanceApart++;
                distanceApart *= -1;
            }

            GeoPoint spawnMark = GeoUtility
                .getGeoCoordinate(midpoint, perpendicularAngle, distanceApart * DISTANCE_FACTOR);

            if (yacht.getHeading() < perpendicularAngle) {
                spawnMark = GeoUtility
                    .getGeoCoordinate(spawnMark, perpendicularAngle + 90, DISTANCE_FACTOR);
            } else {
                spawnMark = GeoUtility
                    .getGeoCoordinate(spawnMark, perpendicularAngle + 270, DISTANCE_FACTOR);
            }

            yacht.setLocation(spawnMark);
            boatIndex++;
        }
    }
}
