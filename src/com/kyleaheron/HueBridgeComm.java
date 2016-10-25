package com.kyleaheron;

import com.google.gson.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

class HueBridgeComm
{
    static Logger logger = Logger.getLogger(HueBridgeComm.class.getName());

    private static final String hueBridgeNUPnP = "https://www.meethue.com/api/nupnp";
    private static final String requestCharset = "UTF-8";

    enum requestMethod
    {
        GET, POST, PUT, DELETE
    }

    static List<HueBridge> discover() {
        // Implement UPnP
        List<HueBridge> bridgeList = new ArrayList<>();
        JsonParser parser = new JsonParser();
        HttpURLConnection httpURLConnection;
        try {
            URL bridgeNUPnP = new URL(hueBridgeNUPnP);
            httpURLConnection = (HttpURLConnection) bridgeNUPnP.openConnection();
            BufferedReader reader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            reader.close();
            if (!builder.toString().equals("[]")) {
                JsonArray bridges = (JsonArray) parser.parse(builder.toString());
                for (JsonElement element : bridges) {
                    bridgeList.add(
                            new HueBridge(
                                    element.getAsJsonObject().get("internalipaddress").getAsString(),
                                    element.getAsJsonObject().get("id").getAsString()));
                }
            } else {
                // No bridges found on the network...
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bridgeList;
    }

    static List<JsonObject> request(requestMethod rm, String fullPath, String json) {
        List<JsonObject> response = new ArrayList<>();
        JsonParser parser = new JsonParser();
        HttpURLConnection connection;
        URL reqUrl;
        try {

            reqUrl = new URL(fullPath);
            connection = (HttpURLConnection) reqUrl.openConnection();
            connection.setRequestMethod(rm.name());
            if (!json.equals("")) {
                connection.setRequestProperty("Content-Length", String.valueOf(json.getBytes().length));
                connection.setDoOutput(true);
                connection.getOutputStream().write(json.getBytes(requestCharset));
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), requestCharset));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            reader.close();
            connection.disconnect();
            String jsonString = builder.toString().trim();
            if (jsonString.trim().startsWith("{")) {
                response.add((JsonObject) parser.parse(jsonString));
            } else {
                for (JsonElement element : (JsonArray) parser.parse(jsonString)) {
                    response.add(element.getAsJsonObject());
                }
            }
            for (JsonObject object : response) {
                if (object.has("error")) {
                    throw new HueException(object);
                }
            }
        } catch (HueException e) {
            logger.log(Level.WARNING, e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return response;
    }

    static List<JsonObject> request(requestMethod rm, String fullPath, JsonObject json) {
        return request(rm, fullPath, json.toString());
    }

    static List<JsonObject> request(requestMethod rm, String fullPath) {
        return request(rm, fullPath, "");
    }
}
