package main.java;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.util.Duration;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.function.UnaryOperator;

public class RFIDController implements Initializable {
    
    @FXML private TextField tagField;
    @FXML private Label statusLabel;
    @FXML private TextArea logArea;
    @FXML private Button autoDetectButton;
    @FXML private CheckBox autoWriteCheckBox;
    
    private SerialService serialService;
    private Timeline hideMessageTimeline;
    private boolean isAutoDetectionEnabled = false;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            // Initialize serial service with error handling
            String portName = MainApp.properties.getProperty("rfid.default.port", "COM1");
            serialService = new SerialService(portName);
            
            // Configure the reader for optimal performance
            serialService.configureReader();
            
            // Set max character limit for tag field
            setTextFieldMaxLength(tagField, 
                Integer.parseInt(MainApp.properties.getProperty("rfid.tag.max.length", "20"))
            );
            
            // Initial log entry
            appendLog("RFID Controller initialized on port: " + portName);
            showInfo("System ready. Port: " + portName);
            
            // Set text field to uppercase
            tagField.setTextFormatter(new TextFormatter<>(change -> {
                change.setText(change.getText().toUpperCase());
                return change;
            }));
            
            // Initialize auto-detect button text
            updateAutoDetectButtonText();
            
        } catch (Exception e) {
            showError("Failed to initialize serial connection: " + e.getMessage());
            appendLog("INIT ERROR: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleScan() {
        try {
            clearMessage();
            showInfo("Scanning for RFID tag...");
            
            String tagId = serialService.readTag();
            
            if (tagId == null || tagId.equals("NO TAG") || tagId.trim().isEmpty()) {
                showWarning("No tag detected! Please place a tag near the reader.");
            } else {
                tagField.setText(tagId.trim());
                showSuccess("Tag scanned successfully: " + tagId.trim());
                appendLog("MANUAL SCAN: " + tagId.trim());
            }
            
        } catch (Exception e) {
            showError("Scan failed: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleWrite() {
        try {
            final String newId = tagField.getText();
            
            // Input validation
            if (newId == null || newId.trim().isEmpty()) {
                showWarning("Please enter a tag ID to write.");
                tagField.requestFocus();
                return;
            }
            
            // Confirmation dialog for write operation
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Confirm Write Operation");
            confirmAlert.setHeaderText("Write Tag ID: " + newId);
            confirmAlert.setContentText("Are you sure you want to write this ID to the tag?");
            
            confirmAlert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    performWrite(newId);
                }
            });
            
        } catch (Exception e) {
            showError("Write operation failed: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleAutoDetect() {
        try {
            if (isAutoDetectionEnabled) {
                // Stop auto-detection
                serialService.stopAutoDetection();
                isAutoDetectionEnabled = false;
                showInfo("Auto-detection stopped");
                appendLog("Auto-detection disabled by user");
            } else {
                // Start auto-detection
                serialService.startAutoDetection(this::onTagAutoDetected);
                isAutoDetectionEnabled = true;
                showInfo("Auto-detection started - Tags will be detected automatically");
                appendLog("Auto-detection enabled - waiting for tags...");
            }
            
            updateAutoDetectButtonText();
            
        } catch (Exception e) {
            showError("Failed to toggle auto-detection: " + e.getMessage());
        }
    }
    
    /**
     * Callback method called when a tag is auto-detected
     */
    private void onTagAutoDetected(String tagId) {
        Platform.runLater(() -> {
            try {
                // Update the tag field with the detected tag
                tagField.setText(tagId);
                showSuccess("🔍 Auto-detected: " + tagId);
                appendLog("AUTO-DETECTED: " + tagId);
                
                // If auto-write is enabled and we have a new ID to write
                if (autoWriteCheckBox != null && autoWriteCheckBox.isSelected()) {
                    // Check if there's a different ID in the field to write
                    String currentFieldText = tagField.getText();
                    if (!currentFieldText.equals(tagId)) {
                        showInfo("Auto-write enabled - preparing to write: " + currentFieldText);
                        // Small delay before writing
                        Timeline autoWriteDelay = new Timeline(
                            new KeyFrame(Duration.millis(1000), e -> performWrite(currentFieldText))
                        );
                        autoWriteDelay.play();
                    }
                }
                
            } catch (Exception e) {
                showError("Error processing auto-detected tag: " + e.getMessage());
            }
        });
    }
    
    private void performWrite(String tagId) {
        try {
            showInfo("Writing tag ID: " + tagId + "...");
            appendLog("WRITE ATTEMPT: " + tagId);
            
            boolean success = serialService.writeTag(tagId);
            
            if (success) {
                showSuccess("✅ Tag written successfully: " + tagId);
                appendLog("WRITE SUCCESS: " + tagId);
                
                // Verify the write by reading the tag again
                Platform.runLater(() -> {
                    Timeline verifyTimeline = new Timeline(
                        new KeyFrame(Duration.millis(500), e -> verifyWrittenTag(tagId))
                    );
                    verifyTimeline.play();
                });
                
            } else {
                showError("❌ Failed to write tag. Please check connection and try again.");
                appendLog("WRITE FAILED: " + tagId);
            }
            
        } catch (Exception e) {
            showError("Write failed: " + e.getMessage());
            appendLog("WRITE ERROR: " + e.getMessage());
        }
    }
    
    private void verifyWrittenTag(String expectedId) {
        try {
            String readId = serialService.readTag();
            if (readId != null && readId.trim().equals(expectedId)) {
                showSuccess("✅ Write verification successful: " + expectedId);
                appendLog("WRITE VERIFIED: " + expectedId);
            } else {
                showWarning("⚠️ Write verification failed. Expected: " + expectedId + ", Read: " + readId);
                appendLog("WRITE VERIFICATION FAILED - Expected: " + expectedId + ", Got: " + readId);
            }
        } catch (Exception e) {
            showWarning("⚠️ Could not verify written tag: " + e.getMessage());
        }
    }
    
    private void updateAutoDetectButtonText() {
        if (autoDetectButton != null) {
            if (isAutoDetectionEnabled) {
                autoDetectButton.setText("Stop Auto-Detect");
                autoDetectButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
            } else {
                autoDetectButton.setText("Start Auto-Detect");
                autoDetectButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
            }
        }
    }
    
    @FXML
    public void handleRefresh() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, 
                               "This will clear all fields and logs. Continue?", 
                               ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Confirm Refresh");
        alert.setTitle("Clear All Data");
        
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                // Stop auto-detection if running
                if (isAutoDetectionEnabled) {
                    serialService.stopAutoDetection();
                    isAutoDetectionEnabled = false;
                    updateAutoDetectButtonText();
                }
                
                tagField.clear();
                clearMessage();
                logArea.clear();
                
                if (autoWriteCheckBox != null) {
                    autoWriteCheckBox.setSelected(false);
                }
                
                appendLog("System refreshed by user");
                showInfo("System refreshed successfully");
            }
        });
    }
    
    @FXML
    private void handleTestConnection() {
        try {
            showInfo("Testing connection...");
            String response = serialService.sendCommand("STATUS\r\n");
            
            if (response != null && !response.equals("NO RESPONSE")) {
                showSuccess("Connection test successful: " + response);
                appendLog("CONNECTION TEST: " + response);
            } else {
                showWarning("Connection test failed - no response from reader");
                appendLog("CONNECTION TEST: Failed");
            }
            
        } catch (Exception e) {
            showError("Connection test error: " + e.getMessage());
        }
    }
    
    // Message display methods (keeping existing ones)
    private void showError(String message) {
        statusLabel.setText("❌ " + message);
        statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-background-color: #ffe6e6; " +
                           "-fx-padding: 8px; -fx-background-radius: 5px; " +
                           "-fx-border-color: #ff9999; -fx-border-width: 1px; -fx-border-radius: 5px;");
        appendLog("ERROR: " + message);
        autoHideMessage(6000);
    }
    
    private void showSuccess(String message) {
        statusLabel.setText("✅ " + message);
        statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-background-color: #e8f5e8; " +
                           "-fx-padding: 8px; -fx-background-radius: 5px; " +
                           "-fx-border-color: #27ae60; -fx-border-width: 1px; -fx-border-radius: 5px;");
        appendLog("SUCCESS: " + message);
        autoHideMessage(4000);
    }
    
    private void showWarning(String message) {
        statusLabel.setText("⚠️ " + message);
        statusLabel.setStyle("-fx-text-fill: #f39c12; -fx-background-color: #fef9e7; " +
                           "-fx-padding: 8px; -fx-background-radius: 5px; " +
                           "-fx-border-color: #f39c12; -fx-border-width: 1px; -fx-border-radius: 5px;");
        appendLog("WARNING: " + message);
        autoHideMessage(5000);
    }
    
    private void showInfo(String message) {
        statusLabel.setText("ℹ️ " + message);
        statusLabel.setStyle("-fx-text-fill: #3498db; -fx-background-color: #e8f4fd; " +
                           "-fx-padding: 8px; -fx-background-radius: 5px; " +
                           "-fx-border-color: #3498db; -fx-border-width: 1px; -fx-border-radius: 5px;");
        appendLog("INFO: " + message);
        autoHideMessage(3000);
    }
    
    private void clearMessage() {
        if (hideMessageTimeline != null) {
            hideMessageTimeline.stop();
        }
        statusLabel.setText("");
        statusLabel.setStyle("");
    }
    
    private void autoHideMessage(int delayMillis) {
        if (hideMessageTimeline != null) {
            hideMessageTimeline.stop();
        }
        
        hideMessageTimeline = new Timeline(
            new KeyFrame(Duration.millis(delayMillis), e -> {
                statusLabel.setText("");
                statusLabel.setStyle("");
            })
        );
        hideMessageTimeline.play();
    }
    
    private void appendLog(String logEntry) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        Platform.runLater(() -> {
            logArea.appendText("[" + time + "] " + logEntry + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }
    
    private void setTextFieldMaxLength(TextField textField, int maxLength) {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            if (change.getControlNewText().length() <= maxLength) {
                return change;
            }
            return null;
        };
        textField.setTextFormatter(new TextFormatter<>(filter));
    }
    
    // Cleanup method
    public void cleanup() {
        try {
            if (serialService != null) {
                serialService.stopAutoDetection();
                serialService.closePort();
                appendLog("Serial connection closed");
            }
            if (hideMessageTimeline != null) {
                hideMessageTimeline.stop();
            }
        } catch (Exception e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }
    
    public SerialService getSerialService() {
        return serialService;
    }
}