package org.minecraft.eden.Eden.mayuela.message;

public enum MayuelaColor {

    TEXT("text", "#FFFFFF"),
    HIGHLIGHT("highlight", "#FFE08A"),
    VALUE("value", "#E879F9"),
    SUCCESS("success", "#22C55E"),
    INFO("info", "#38BDF8"),
    DANGER("danger", "#EF4444");

    private final String key;
    private final String hex;

    MayuelaColor(String key, String hex) {
        this.key = key;
        this.hex = hex;
    }

    public String key() {
        return key;
    }

    public String hex() {
        return hex;
    }

    public String tag() {
        return "<" + hex + ">";
    }

    public String paint(String text) {
        return tag() + text;
    }
}
