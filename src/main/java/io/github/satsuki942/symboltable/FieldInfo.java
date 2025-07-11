package io.github.satsuki942.symboltable;

public class FieldInfo {
    private final String name;
    private final String type;
    private final String version;

    public FieldInfo(String name, String type, String version) {
        this.name = name;
        this.type = type;
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getVersion() {
        return version;
    }
}
