package net.nextfur.fwb.generator;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum BuildingType {
    AUTO("auto"),
    STANDARD("standard"),
    HOUSE("house"),
    TOWER("tower"),
    SHOP("shop"),
    L_SHAPE("l_shape"),
    COURTYARD("courtyard"),
    HALL("hall"),
    MANOR("manor"),
    APARTMENT("apartment"),
    CLUSTER("cluster"),
    TEMPLATE("template");

    private final String id;

    BuildingType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static BuildingType byName(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        for (BuildingType type : values()) {
            if (type.id.equals(lower) || type.name().toLowerCase(Locale.ROOT).equals(lower)) {
                return type;
            }
        }
        return null;
    }

    public static List<String> allNames() {
        return Arrays.stream(values()).map(BuildingType::getId).toList();
    }
}
