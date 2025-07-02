package main.java;

import com.fazecast.jSerialComm.SerialPort;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class SerialService {
    private SerialPort port;
    private ExecutorService executorService;
    private AtomicBoolean isAutoDetecting = new AtomicBoolean(false);
    private Consumer<String> tagDetectedCallback;
    private String lastDetectedTag = "";
    
    public SerialService(String portName) {
        int baudrate = Integer.parseInt(MainApp.properties.getProperty("serial.baudrate"));
        int dataBits = Integer.parseInt(MainApp.properties.getProperty("serial.databits"));
        int stopBits = Integer.parseInt(MainApp.properties.getProperty("serial.stopbits"));
        int parity = Integer.parseInt(MainApp.properties.getProperty("serial.parity"));

        System.out.println("Trying to connect....");
        System.out.println("Port : " + portName);
        System.out.println("Baud Rate : " + baudrate);
        System.out.println("Data Bits : " + dataBits);
        System.out.println("Stop Bits : " + stopBits);
        System.out.println("Parity : " + parity);

        port = SerialPort.getCommPort(portName);
        port.setBaudRate(baudrate);
        port.setNumDataBits(dataBits);
        port.setNumStopBits(stopBits);
        port.setParity(parity);
        
        // Shorter timeout for auto-detection
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 500, 500);
        
        // Initialize executor service for background tasks
        executorService = Executors.newSingleThreadExecutor();

        if (port.openPort()) {
            System.out.println("Serial port opened successfully");
        } else {
            System.err.println("Failed to open serial port: " + portName);
        }
    }

    public String readTag() {
        byte[] buffer = new byte[64];
        int len = port.readBytes(buffer, buffer.length);

        if (len <= 0) {
            return "NO TAG";
        }

        return new String(buffer, 0, len).trim();
    }

    public boolean writeTag(String newId) {
        if (newId == null || newId.isEmpty()) return false;

        // Stop auto-detection during write operation
        boolean wasAutoDetecting = isAutoDetecting.get();
        if (wasAutoDetecting) {
            stopAutoDetection();
        }

        try {
            // DB02UHF specific write command format
            // You may need to adjust this based on your reader's protocol
            String writeCommand = constructWriteCommand(newId);
            byte[] data = writeCommand.getBytes();
            int bytesWritten = port.writeBytes(data, data.length);
            
            // Wait for write confirmation
            Thread.sleep(100);
            
            // Verify write operation
            String verification = readTag();
            boolean success = verification.equals(newId);
            
            return success && bytesWritten > 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            // Restart auto-detection if it was running
            if (wasAutoDetecting) {
                startAutoDetection(tagDetectedCallback);
            }
        }
    }
    
    /**
     * Construct write command for DB02UHF reader
     * This is a placeholder - adjust according to your reader's protocol
     */
    private String constructWriteCommand(String tagId) {
        // Example format for UHF readers - modify as needed
        // Format: [Command][Length][Data][Checksum]
        return "WRITE:" + tagId + "\r\n";
    }

    /**
     * Start auto-detection of RFID tags
     * @param callback Function to call when a tag is detected
     */
    public void startAutoDetection(Consumer<String> callback) {
        if (isAutoDetecting.get()) {
            return; // Already running
        }
        
        this.tagDetectedCallback = callback;
        isAutoDetecting.set(true);
        
        CompletableFuture.runAsync(() -> {
            System.out.println("Auto-detection started");
            
            while (isAutoDetecting.get()) {
                try {
                    String detectedTag = readTag();
                    
                    if (detectedTag != null && !detectedTag.equals("NO TAG") && 
                        !detectedTag.trim().isEmpty()) {
                        
                        // Only trigger callback if it's a new tag or first detection
                        if (!detectedTag.equals(lastDetectedTag)) {
                            lastDetectedTag = detectedTag;
                            
                            if (callback != null) {
                                callback.accept(detectedTag.trim());
                            }
                            
                            System.out.println("Auto-detected tag: " + detectedTag.trim());
                        }
                    } else {
                        // Clear last detected tag when no tag is present
                        if (!lastDetectedTag.isEmpty()) {
                            lastDetectedTag = "";
                            System.out.println("Tag removed from detection range");
                        }
                    }
                    
                    // Small delay to prevent excessive CPU usage
                    Thread.sleep(200);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Error during auto-detection: " + e.getMessage());
                    // Continue running unless interrupted
                }
            }
            
            System.out.println("Auto-detection stopped");
        }, executorService);
    }

    /**
     * Stop auto-detection
     */
    public void stopAutoDetection() {
        isAutoDetecting.set(false);
        lastDetectedTag = "";
    }

    /**
     * Check if auto-detection is currently running
     */
    public boolean isAutoDetecting() {
        return isAutoDetecting.get();
    }

    /**
     * Get the last detected tag without triggering a new read
     */
    public String getLastDetectedTag() {
        return lastDetectedTag;
    }

    /**
     * Send raw command to the RFID reader
     * Useful for reader-specific commands
     */
    public String sendCommand(String command) {
        try {
            byte[] cmdBytes = command.getBytes();
            port.writeBytes(cmdBytes, cmdBytes.length);
            
            // Wait for response
            Thread.sleep(100);
            
            byte[] buffer = new byte[256];
            int len = port.readBytes(buffer, buffer.length);
            
            if (len > 0) {
                return new String(buffer, 0, len).trim();
            }
            
            return "NO RESPONSE";
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "INTERRUPTED";
        }
    }

    /**
     * Configure reader for optimal tag detection
     * DB02UHF specific settings
     */
    public void configureReader() {
        try {
            // Example configuration commands for DB02UHF
            // Adjust these based on your reader's manual
            
            // Set read power (example: maximum power)
            sendCommand("SET_POWER:30\r\n");
            Thread.sleep(50);
            
            // Set frequency region (example: US band)
            sendCommand("SET_REGION:US\r\n");
            Thread.sleep(50);
            
            // Enable continuous inventory mode
            sendCommand("SET_MODE:CONTINUOUS\r\n");
            Thread.sleep(50);
            
            System.out.println("Reader configured for optimal detection");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void closePort() {
        try {
            // Stop auto-detection before closing
            stopAutoDetection();
            
            // Shutdown executor service
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdown();
            }
            
            // Close serial port
            if (port != null) {
                port.closePort();
            }
            
            System.out.println("Serial service closed successfully");
            
        } catch (Exception e) {
            System.out.println("Error during cleanup: " + e.getMessage());
        }
    }
}