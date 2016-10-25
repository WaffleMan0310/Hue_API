package com.kyleaheron;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.logging.Logger;

public class HueLight {

    static Logger logger = Logger.getLogger(HueLight.class.getName());

    public static final int MIN_BRIGHTNESS = 1;
    public static final int MAX_BRIGHTNESS = 254;

    public static final int MIN_HUE = 0;
    public static final int MAX_HUE = 65535;

    public static final int MIN_SATURATION = 0;
    public static final int MAX_SATURATION = 254;

    enum HueEffect {
        NONE, COLORLOOP
    }

    enum HueAlert {
        NONE, SELECT, LSELECT
    }

    private HueBridge parentBridge;
    private int id;
    private String type;
    private String name;
    private String modelId;
    private String softwareVersion;

    private boolean initialStateSyncComplete = false;
    private boolean isOn;
    private int brightness;
    private int hue;
    private int saturation;
    private int colorTemperature;
    private float colorX;
    private float colorY;
    private Color color;
    private HueEffect effect;
    private HueAlert alertEffect;

    private JsonObject outputBuffer = new JsonObject();

    HueLight(HueBridge parentBridge, int id) {
        this.parentBridge = parentBridge;
        this.id = id;
    }

    public void show() {
        if (outputBuffer.size() > 0) {
            try {
                List<JsonObject> response = HueBridgeComm.request(
                        HueBridgeComm.requestMethod.PUT,
                        HueBridge.formatPath(
                                parentBridge.getIpAddress(),
                                parentBridge.getUsername(),
                                String.format("lights/%d/state", getId())),
                        outputBuffer);
                if (response.size() > 0) {
                    updateStateVariables(response);
                    outputBuffer = new JsonObject();
                } else {
                    // No response was given
                }
                Thread.sleep(100); // Recommended distance between commands
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    void initializeStateVariables(boolean isOn, int brightness, int hue, int saturation, float x, float y, int colorTemperature, String alert, String effect, String lightType, String lightName, String lightModelId, String lightSoftwareVersion) {
        if (!initialStateSyncComplete) {
            this.isOn = isOn;
            this.brightness = brightness;
            this.hue = hue;
            this.saturation = saturation;
            this.colorX = x;
            this.colorY = y;
            this.colorTemperature = colorTemperature;
            if (alert == HueAlert.LSELECT.name()) {
                this.alertEffect = HueAlert.LSELECT;
            } else if (alert == HueAlert.SELECT.name()) {
                this.alertEffect = HueAlert.SELECT;
            } else {
                this.alertEffect = HueAlert.NONE;
            }
            if (effect == HueEffect.COLORLOOP.name()) {
                this.effect = HueEffect.COLORLOOP;
            } else {
                this.effect = HueEffect.NONE;
            }
            this.type = lightType;
            this.name = lightName;
            this.modelId = lightModelId;
            this.softwareVersion = lightSoftwareVersion;
            this.initialStateSyncComplete = true;
        }
    }

    private void updateStateVariables(List<JsonObject> response) {
        response.stream().filter(object -> object.has("success")).forEach(object -> {
            JsonObject stateInfo = object.get("success").getAsJsonObject();
            if (stateInfo.has(String.format("/lights/%d/state/bri", getId()))) {
                this.brightness = stateInfo.get(String.format("/lights/%d/state/bri", getId())).getAsInt();
            }
            if (stateInfo.has(String.format("/lights/%d/state/on", getId()))) {
                this.isOn = stateInfo.get(String.format("/lights/%d/state/on", getId())).getAsBoolean();
            }
            if (stateInfo.has(String.format("/lights/%d/state/hue", getId()))) {
                this.hue = stateInfo.get(String.format("/lights/%d/state/hue", getId())).getAsInt();
            }
            if (stateInfo.has(String.format("/lights/%d/state/sat", getId()))) {
                this.saturation = stateInfo.get(String.format("/lights/%d/state/sat", getId())).getAsInt();
            }
            if (stateInfo.has(String.format("/lights/%d/state/xy", getId()))) {
                colorX = stateInfo.get(String.format("/lights/%d/state/xy", getId())).getAsJsonArray().get(0).getAsFloat();
                colorY = stateInfo.get(String.format("/lights/%d/state/xy", getId())).getAsJsonArray().get(1).getAsFloat();
            }
            if (stateInfo.has(String.format("/lights/%d/state/ct", getId()))) {
                this.colorTemperature = stateInfo.get(String.format("/lights/%d/state/ct", getId())).getAsInt();
            }
            if (stateInfo.has(String.format("/lights/%d/state/alert", getId()))) {
                String alertEffect = stateInfo.get(String.format("/lights/%d/state/alert", getId())).getAsString();
                if (alertEffect.equals(HueAlert.SELECT.name())) {
                    this.alertEffect = HueAlert.SELECT;
                } else if (alertEffect.equals(HueAlert.LSELECT.name())) {
                    this.alertEffect = HueAlert.LSELECT;
                } else {
                    this.alertEffect = HueAlert.NONE;
                }
            }
            if (stateInfo.has(String.format("/lights/%d/state/effect", getId()))) {
                String effect = stateInfo.get(String.format("/lights/%d/state/effect", getId())).getAsString();
                if (effect.equals(HueEffect.COLORLOOP.name())) {
                    this.effect = HueEffect.COLORLOOP;
                } else {
                    this.effect = HueEffect.NONE;
                }
            }
        });
    }

    private double[] RGBtoXY(double r, double g, double b) {
        double[] color = new double[2];
        double redRatio = (r / 255f);
        double greenRatio = (g / 255f);
        double blueRatio = (b / 255f);
        double red = ((redRatio > 0.04045f) ? Math.pow((redRatio + 0.055f) / (1.0f + 0.055f), 2.4f) : (redRatio / 12.92f));
        double green = ((greenRatio > 0.04045f) ? Math.pow((greenRatio + 0.055f) / (1.0f + 0.055f), 2.4f) : (greenRatio / 12.92f));
        double blue = ((blueRatio > 0.04045f) ? Math.pow((blueRatio + 0.055f) / (1.0f + 0.055f), 2.4f) : (blueRatio / 12.92f));
        double x = (red * 0.664511f + green * 0.154324f + blue * 0.162028f);
        double y = (red * 0.283881f + green * 0.668433f + blue * 0.047685f);
        double z = (red * 0.000088f + green * 0.072310f + blue * 0.986039f);
        color[0] = x / (x + y + z);
        color[1] = y / (x + y + z);
        return color;
    }

    public void setName(String name) {
        JsonObject nameToSend = new JsonObject();
        nameToSend.addProperty("name", name);
        List<JsonObject> response = HueBridgeComm.request(
                HueBridgeComm.requestMethod.PUT,
                HueBridge.formatPath(parentBridge.getIpAddress(), parentBridge.getUsername(), "lights/" + getId()),
                nameToSend
        );
        if (response.size() > 0 && response.get(0).getAsJsonObject().has("success")) this.name = name;
    }

    public HueLight setOn(boolean state) {
        if (state != isOn()) outputBuffer.addProperty("on", state);
        return this;
    }

    public HueLight setHue(int hue) {
        if (hue >= MIN_HUE && hue <= MAX_HUE) outputBuffer.addProperty("hue", hue);
        return this;
    }

    public HueLight setColor(Color c) {
        color = c;
        double[] color = RGBtoXY(c.getRed(), c.getGreen(), c.getBlue());
        JsonArray array = new JsonArray();
        array.add(color[0]);
        array.add(color[1]);
        outputBuffer.add("xy", array);
        return this;
    }

    public HueLight setSaturation(int saturation) {
        if (saturation >= MIN_SATURATION && saturation <= MAX_SATURATION) outputBuffer.addProperty("sat", saturation);
        return this;
    }

    public HueLight setBrightness(int brightness) {
        if (brightness >= MIN_BRIGHTNESS && brightness <= MAX_BRIGHTNESS) outputBuffer.addProperty("bri", brightness);
        return this;
    }

    public HueLight setTransitionTime(int milliseconds) {
        outputBuffer.addProperty("transitiontime", milliseconds / 100);
        return this;
    }

    public HueLight setEffect(HueEffect effect) {
        outputBuffer.addProperty("effect", effect.name());
        return this;
    }

    public int getId() {
        return id;
    }

    public boolean isOn() {
        return isOn;
    }

    public Color getColor() {
        return color;
    }

    public int getBrightness() {
        return brightness;
    }

    public int getHue() {
        return hue;
    }

    public int getSaturation() {
        return saturation;
    }

    public int getColorTemperature() {
        return colorTemperature;
    }

    public HueAlert getAlertEffect() {
        return alertEffect;
    }

    public HueEffect getEffect() {
        return effect;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getModelId() {
        return modelId;
    }

    public String getSoftwareVersion() {
        return softwareVersion;
    }
}
