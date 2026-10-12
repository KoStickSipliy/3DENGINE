package com.cgengine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApplicationResourceTest {

    @Test
    void fxmlResourceIsPackagedAtTheApplicationPath() {
        assertNotNull(Simple3DViewer.class.getResource("fxml/gui.fxml"));
    }
}
