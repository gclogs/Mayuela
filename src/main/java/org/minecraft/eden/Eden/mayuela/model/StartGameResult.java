package org.minecraft.eden.Eden.mayuela.model;

import java.util.List;

public record StartGameResult(boolean started, List<String> reasons) {

    public static StartGameResult success() {
        return new StartGameResult(true, List.of());
    }

    public static StartGameResult failed(List<String> reasons) {
        return new StartGameResult(false, List.copyOf(reasons));
    }
}
