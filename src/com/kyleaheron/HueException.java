package com.kyleaheron;

import com.google.gson.JsonObject;

class HueException extends Exception {

    private int errorType;
    private String errorAddress;
    private String errorDescription;

    HueException(JsonObject object) {
        if (object.has("error")) {
            JsonObject errorInfo = object.get("error").getAsJsonObject();
            errorType = errorInfo.get("type").getAsInt();
            errorAddress = errorInfo.get("address").getAsString();
            errorDescription = errorInfo.get("description").getAsString();
        }
    }

    @Override
    public String getMessage() {
       return formatErrorString(errorType, errorAddress, errorDescription);
    }

    private String formatErrorString(int errorType, String errorAddress, String errorDescription) {
        return String.format("%s - Error %d: %s", errorAddress, errorType, errorDescription);
    }
}
