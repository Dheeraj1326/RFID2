package main.java;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {
	 final private static String PROPERTIES_FILE_PATH = "datasource.properties";
	 public static Properties properties = null;
	 
	    
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout.fxml"));
        Scene scene = new Scene(loader.load(), 600, 400);
        primaryStage.setTitle("RFID Panel");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
    	properties = new Properties();
    	InputStream inputStream = MainApp.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE_PATH);
    	try {
			properties.load(inputStream);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        launch(args);
    }
}
