package io.github.satsuki942.symboltable;

import java.util.List;
import java.util.Map;

public class MethodInfo {
    private final String name;
    private final String returnType;
    private final List<String> parameterTypes;
    private final String version;
    private final Map<String, String> variables;

    public MethodInfo(String name, String returnType, List<String> parameterTypes, String version, Map<String, String> variables) {
        this.name = name;
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
        this.version = version;
        this.variables = variables;
    }

    public String getName() {
        return name;
    }

    public String getReturnType() {
        return returnType;
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    public String getVersion() {
        return version;
    }
}
