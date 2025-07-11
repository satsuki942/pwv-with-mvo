package io.github.satsuki942.symboltable;

import java.util.List;
import java.util.Map;

public class ClassInfo {
    private final String baseName;
    private final boolean isVersioned;
    private final Map<String, List<MethodInfo>> methods;
    private final Map<String, List<FieldInfo>> fields;

    public ClassInfo(String baseName, boolean isVersioned, Map<String, List<MethodInfo>> methods, Map<String, List<FieldInfo>> fields) {
        this.baseName = baseName;
        this.isVersioned = isVersioned;
        this.methods = methods;
        this.fields = fields;
    }

    public String getBaseName() {
        return baseName;
    }

    public Map<String, List<FieldInfo>> getFields() {
        return fields;
    }

    public boolean isVersioned() {
        return isVersioned;
    }

    public Map<String, List<MethodInfo>> getMethods() {
        return methods;
    }
}
