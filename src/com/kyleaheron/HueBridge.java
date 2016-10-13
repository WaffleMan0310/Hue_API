package com.kyleaheron;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HueBridge {

    Logger logger = Logger.getLogger(HueBridge.class.getName());

    private String ipAddress;
    private String uniqueDeviceId;
    private String username;
    private String name;
    private String macAddress;
    private String netmask;
    private boolean dhcp;
    private File usernameJsonFile;

    private boolean isAuthenticated = false;
    private boolean initialSyncComplete = false;

    private static List<HueLight> connectedLights = new ArrayList<>();

    HueBridge(String ipAddress, String uniqueDeviceId) {
        this.ipAddress = ipAddress;
        this.uniqueDeviceId = uniqueDeviceId;
        usernameJsonFile = new File("username.json");
        if (!usernameJsonFile.exists()) {
            try {
                logger.log(Level.INFO, "'username.json' does not exist! Creating...");
                usernameJsonFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            JsonObject usernameJson = new JsonObject();
            try {
                logger.log(Level.INFO, "'username.json' found! Loading username for bridge...");
                FileReader reader = new FileReader(usernameJsonFile);
                BufferedReader readBuffer = new BufferedReader(reader);
                StringBuilder builder = new StringBuilder();
                JsonParser parser = new JsonParser();
                String line;
                while ((line = readBuffer.readLine()) != null) {
                    builder.append(line).append("\n");
                }
                reader.close();
                readBuffer.close();
                usernameJson = parser.parse(builder.toString()).getAsJsonObject();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (usernameJson.get("id").getAsString().equals(getUniqueDeviceId())) {
                    this.isAuthenticated = true;
                    this.username = usernameJson.get("username").getAsString();
                    getConfigurationData();
                    completeInitialSync();
                    if (isAuthenticated && initialSyncComplete) {
                        logger.log(Level.INFO, String.format("Successfully connected to bridge: %s", getUniqueDeviceId()));
                    }
                } else {
                    logger.log(Level.WARNING, "Username not found for bridge, call 'authenticate' to create one.");
                }
            }
        }
    }

    public static List<HueBridge> discover() {
        return HueBridgeComm.discover();
    }

    public void authenticate() {
        if (!isAuthenticated) {
            logger.log(Level.INFO, "Bridge not authenticated! Authenticating...");
            boolean accessGranted = false;
            List<JsonObject> response;
            JsonObject output = new JsonObject();
            output.addProperty("devicetype", getClass().getName());
            if (username != null)
            {
                output.addProperty("username", username);
            }
            int waitInSeconds = 30;
            long startTime = System.currentTimeMillis();
            do {
                response = HueBridgeComm.request(HueBridgeComm.requestMethod.POST, String.format("http://%s/api", getIpAddress()), output);
                if (response.size() > 0) {
                    for (JsonObject responseObj : response) {
                        if (responseObj.has("success")) {
                            try {
                                username = responseObj.get("success").getAsJsonObject().get("username").getAsString();
                                isAuthenticated = true;

                                FileWriter writer = new FileWriter(usernameJsonFile);
                                BufferedWriter bufferedWriter = new BufferedWriter(writer);
                                JsonObject usernameJson = new JsonObject();
                                usernameJson.addProperty("id", getUniqueDeviceId());
                                usernameJson.addProperty("username", getUsername());
                                bufferedWriter.write(usernameJson.toString());
                                bufferedWriter.close();
                                writer.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            } finally {
                                logger.log(Level.INFO, String.format("Successfully authenticated with bridge: %s!", getUniqueDeviceId()));
                                getConfigurationData();
                                completeInitialSync();
                                accessGranted = true;
                            }
                        } else {
                            logger.log(Level.WARNING, "Link button not pressed!");
                        }
                    }
                } else {
                    // No response
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (!accessGranted || (System.currentTimeMillis() - startTime) > (waitInSeconds * 1000));
        }
    }

    private void completeInitialSync() {
        if (isAuthenticated && !initialSyncComplete) {
            List<JsonObject> response = HueBridgeComm.request(HueBridgeComm.requestMethod.GET, formatPath(getIpAddress(), getUsername(), "lights"), "");
            if (response.size() > 0 ) {
                for (Entry<String, JsonElement> property : response.get(0).entrySet()) {
                    HueLight light = new HueLight(this, Integer.parseInt(property.getKey()));
                    JsonObject stateObject = property.getValue().getAsJsonObject().get("state").getAsJsonObject();
                    light.initializeStateVariables(
                            stateObject.get("on").getAsBoolean(),
                            stateObject.get("bri").getAsInt(),
                            stateObject.get("hue").getAsInt(),
                            stateObject.get("sat").getAsInt(),
                            stateObject.get("xy").getAsJsonArray().get(0).getAsFloat(),
                            stateObject.get("xy").getAsJsonArray().get(1).getAsFloat(),
                            stateObject.get("ct").getAsInt(),
                            stateObject.get("alert").getAsString(),
                            stateObject.get("effect").getAsString(),
                            property.getValue().getAsJsonObject().get("type").getAsString(),
                            property.getValue().getAsJsonObject().get("name").getAsString(),
                            property.getValue().getAsJsonObject().get("modelid").getAsString(),
                            property.getValue().getAsJsonObject().get("swversion").getAsString()
                    );
                    connectedLights.add(light);
                }
            } else {
                // No response was recieved
            }
        } else {
            // Device not authenticated
        }
        initialSyncComplete = true;
    }

    public boolean searchForLights() {
        List<JsonObject> response = HueBridgeComm.request(HueBridgeComm.requestMethod.POST, formatPath(getIpAddress(), getUsername(), "lights"), "");
        try {
            if (response.size() > 0) {
                logger.log(Level.INFO, "Searching for new lights...");
                Thread.sleep(40000);
                return true;
            } else {
                return false;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void getConfigurationData() {
        List<JsonObject> response = HueBridgeComm.request(HueBridgeComm.requestMethod.GET, formatPath(getIpAddress(), getUsername(), "config"), "");
        // Fix this
        if (response.size() > 0) {
            JsonObject configuration = response.get(0);
            this.name = configuration.get("name").getAsString();
            this.macAddress = configuration.get("mac").getAsString();
            this.netmask = configuration.get("netmask").getAsString();
            this.dhcp = configuration.get("dhcp").getAsBoolean();
        } else {
            // No response
        }

    }

    static String formatPath(String ipAddr, String username, String path) {
        return String.format("http://%s/api/%s/%s", ipAddr, username, path);
    }

    public List<HueLight> getLights() {
        return connectedLights;
    }

    public String getName() {
        return name;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public String getNetmask() {
        return netmask;
    }

    public boolean isDhcp() {
        return dhcp;
    }

    public String getUniqueDeviceId() {
        return uniqueDeviceId;
    }

    public String getUsername() {
        return username;
    }

    public String getIpAddress() {
        return ipAddress;
    }
}
