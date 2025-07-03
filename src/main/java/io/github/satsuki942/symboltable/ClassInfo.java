package io.github.satsuki942.symboltable;

import java.util.List;
import java.util.Map;

public class ClassInfo {
    private final String baseName;
    private final boolean isVersioned;
    private final Map<String, List<MethodInfo>> methods;

    public ClassInfo(String baseName, boolean isVersioned, Map<String, List<MethodInfo>> methods) {
        this.baseName = baseName;
        this.isVersioned = isVersioned;
        this.methods = methods;
    }

    public String getBaseName() {
        return baseName;
    }

    public boolean isVersioned() {
        return isVersioned;
    }

    public Map<String, List<MethodInfo>> getMethods() {
        return methods;
    }
}
