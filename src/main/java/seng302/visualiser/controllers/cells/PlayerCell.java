package seng302.visualiser.controllers.cells;

import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import seng302.visualiser.fxObjects.assets_3D.BoatMeshType;
import seng302.visualiser.fxObjects.assets_3D.BoatModel;
import seng302.visualiser.fxObjects.assets_3D.ModelFactory;

public class PlayerCell {

    //--------FXML BEGIN--------//
    @FXML
    private Label playerName;
    @FXML
    private GridPane playerListCell;
    @FXML
    private Pane boatPane;
    //---------FXML END---------//

    private String name;
    private Color boatColor;
    private Integer playerId;

    public PlayerCell(Integer playerId, String playerName, Color color) {
        this.playerId = playerId;
        this.name = playerName;
        this.boatColor = color;
    }

    public void initialize() {
        // Set Player Name
        playerName.setText(name);
        // Add Rotating Boat to Player Cell with players color on it.
        Group group = new Group();
        boatPane.getChildren().add(group);
        BoatModel bo = ModelFactory.boatIconView(BoatMeshType.CAT_ATE_A_MERINGUE, this.boatColor);
        group.getChildren().add(bo.getAssets());
    }

    public Integer getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return name;
    }

    public Color getBoatColor() {
        return boatColor;
    }
}
