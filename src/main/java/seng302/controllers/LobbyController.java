package seng302.controllers;

import java.io.IOException;
import java.net.URL;
import java.util.*;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import seng302.client.ClientState;
import seng302.client.ClientStateQueryingRunnable;
import seng302.gameServer.GameStages;
import seng302.gameServer.GameState;
import seng302.gameServer.MainServerThread;

/**
 * A class describing the actions of the lobby screen
 * Created by wmu16 on 10/07/17.
 */
public class LobbyController implements Initializable, Observer{
    @FXML
    private GridPane lobbyScreen;
    @FXML
    private Text lobbyIpText;
    @FXML
    private Button readyButton;
    @FXML
    private ListView firstListView;
    @FXML
    private ListView secondListView;
    @FXML
    private ListView thirdListView;
    @FXML
    private ListView fourthListView;
    @FXML
    private ListView fifthListView;
    @FXML
    private ListView sixthListView;
    @FXML
    private ListView seventhListView;
    @FXML
    private ListView eighthListView;
    @FXML
    private ImageView firstImageView;
    @FXML
    private ImageView secondImageView;
    @FXML
    private ImageView thirdImageView;
    @FXML
    private ImageView fourthImageView;
    @FXML
    private ImageView fifthImageView;
    @FXML
    private ImageView sixthImageView;
    @FXML
    private ImageView seventhImageView;
    @FXML
    private ImageView eighthImageView;

    private static List<ObservableList<String>> competitors = new ArrayList<>();
    private static ObservableList<String> firstCompetitor = FXCollections.observableArrayList();
    private static ObservableList<String> secondCompetitor = FXCollections.observableArrayList();
    private static ObservableList<String> thirdCompetitor = FXCollections.observableArrayList();
    private static ObservableList<String> fourthCompetitor = FXCollections.observableArrayList();
    private static ObservableList<String> fifthCompetitor = FXCollections.observableArrayList();
    private static ObservableList<String> sixthCompetitor = FXCollections.observableArrayList();
    private static ObservableList<String> seventhCompetitor = FXCollections.observableArrayList();
    private static ObservableList<String> eighthCompetitor = FXCollections.observableArrayList();
    private ClientStateQueryingRunnable clientStateQueryingRunnable;
    private static List<ImageView> imageViews;
    private static List<ListView> listViews;

    private int MAX_NUM_PLAYERS = 8;

    private Boolean switchedPane = false;
    private MainServerThread mainServerThread;

    private void setContentPane(String jfxUrl) {
        try {
            AnchorPane contentPane = (AnchorPane) lobbyScreen.getParent();
            contentPane.getChildren().removeAll();
            contentPane.getChildren().clear();
            contentPane.getStylesheets().add(getClass().getResource("/css/master.css").toString());
            contentPane.getChildren()
                    .addAll((Pane) FXMLLoader.load(getClass().getResource(jfxUrl)));
        } catch (javafx.fxml.LoadException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (ClientState.isHost()) {
            lobbyIpText.setText("Lobby Host IP: " + ClientState.getHostIp());
            readyButton.setDisable(false);
        }
        else {
            lobbyIpText.setText("Connected to IP: ");
            readyButton.setDisable(true);
        }

        imageViews = new ArrayList<>();
        Collections.addAll(imageViews, firstImageView, secondImageView, thirdImageView, fourthImageView,
                fifthImageView, sixthImageView, seventhImageView, eighthImageView);
        listViews = new ArrayList<>();
        Collections.addAll(listViews, firstListView, secondListView, thirdListView, fourthListView, fifthListView,
                sixthListView, seventhListView, eighthListView);
        competitors = new ArrayList<>();
        Collections.addAll(competitors, firstCompetitor, secondCompetitor, thirdCompetitor,
                fourthCompetitor, fifthCompetitor, sixthCompetitor, seventhCompetitor, eighthCompetitor);

        initialiseListView();
        initialiseImageView();  // parrot gif init

        // set up client state query thread, so that when it receives the race-started packet
        // it can switch to the race view
        ClientStateQueryingRunnable clientStateQueryingRunnable = new ClientStateQueryingRunnable();
        clientStateQueryingRunnable.addObserver(this);
        Thread clientStateQueryingThread = new Thread(clientStateQueryingRunnable, "Client State querying thread");
        clientStateQueryingThread.setDaemon(true);
        clientStateQueryingThread.start();
    }

    @Override
    public void update(Observable o, Object arg) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                if (arg.equals("game started") && !switchedPane) {
                    switchToRaceView();
                }
                if (arg.equals(("update players"))) {
                    initialiseListView();
                }
            }
        });
    }

    private void initialiseListView() {
        listViews.forEach(listView -> listView.getItems().clear());
        imageViews.forEach(gif -> gif.setVisible(false));
        competitors.forEach(ol -> ol.removeAll());

        List<Integer> ids = new ArrayList<>(ClientState.getBoats().keySet());
        for (int i = 0; i < ids.size(); i++) {
            competitors.get(i).add(String.format("Player ID: %d", ids.get(i)));
            listViews.get(i).setItems(competitors.get(i));
            imageViews.get(i).setVisible(true);
        }
    }

    private void initialiseImageView() {
        for (int i = 0; i < MAX_NUM_PLAYERS; i++) {
            imageViews.get(i).setImage(new Image(getClass().getResourceAsStream("/pics/sail.png")));
        }
//        Image image1 = new Image(getClass().getResourceAsStream("/pics/sail.png"));
//        firstImageView.setImage(image1);
//        Image image2 = new Image(getClass().getResourceAsStream("/pics/sail.png"));
//        secondImageView.setImage(image2);
//        Image image3 = new Image(getClass().getResourceAsStream("/pics/sail.png"));
//        thirdImageView.setImage(image3);
//        Image image4 = new Image(getClass().getResourceAsStream("/pics/sail.png"));
//        fourthImageView.setImage(image4);
//        Image image5 = new Image(getClass().getResourceAsStream("/pics/sail.png"));
//        fifthImageView.setImage(image5);
//        Image image6 = new Image(getClass().getResourceAsStream("/pics/sail.png"));
//        sixthImageView.setImage(image6);
//        Image image7 = new Image(getClass().getResourceAsStream("/pics/sail.png"));
//        seventhImageView.setImage(image7);
//        Image image8 = new Image(getClass().getResourceAsStream("/pics/sail.png"));
//        eighthImageView.setImage(image8);
    }

    @FXML
    public void leaveLobbyButtonPressed() {
        // TODO: 10/07/17 wmu16 - Finish function!
        setContentPane("/views/StartScreenView.fxml");
        GameState.setCurrentStage(GameStages.CANCELLED);
        // TODO: 20/07/17 wmu16 - Implement some way of terminating the game
        ClientState.setConnectedToHost(false);
    }

    @FXML
    public void readyButtonPressed() {
//        setContentPane("/views/RaceView.fxml");
//        playTheme();
        GameState.setCurrentStage(GameStages.RACING);
        mainServerThread.startGame();
    }


//    private static MediaPlayer mediaPlayer;
//
//    private void playTheme() {
//        Random random = new Random(System.currentTimeMillis());
//        Integer rand = random.nextInt();
//        if(rand == 10) {
//            URL file = getClass().getResource("/music/Disturbed - down with the sickness.mp3");
//            Media hit = new Media(file.toString());
//            mediaPlayer = new MediaPlayer(hit);
//            mediaPlayer.play();
//        } else if(rand == 9) {
//            URL file = getClass().getResource("/music/Owl City - Fireflies.mp3");
//            Media hit = new Media(file.toString());
//            mediaPlayer = new MediaPlayer(hit);
//            mediaPlayer.play();
//        }
//    }

    private void switchToRaceView() {
        if (!switchedPane) {
            switchedPane = true;
            setContentPane("/views/RaceView.fxml");
        }
    }

    public void setMainServerThread(MainServerThread mainServerThread) {
        this.mainServerThread = mainServerThread;
    }
}
