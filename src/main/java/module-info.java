module com.cgengine {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires javafx.graphics;
  

    opens com.cgengine to javafx.fxml;
    exports com.cgengine;
}
