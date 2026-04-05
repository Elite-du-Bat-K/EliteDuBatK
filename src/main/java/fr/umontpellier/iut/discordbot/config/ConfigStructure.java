package fr.umontpellier.iut.discordbot.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConfigStructure {
    private String token;

    private Map<String, List<String>> groups;

    public String getToken() {
        return token;
    }

    public List<String> getRolesIdForGroup(String group) {
        List<String> rolesId = groups.get(group);

        return rolesId == null ? List.of() : rolesId;
    }

    public List<String> getRoles() {
        return groups.keySet().stream().toList();
    }
}
