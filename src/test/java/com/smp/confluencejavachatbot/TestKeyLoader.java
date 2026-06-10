package com.smp.confluencejavachatbot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple test helper to load API keys from classpath application-local.yaml.
 * It looks for keys under the YAML path: app -> keys -> {openai, anthropic}
 */
public final class TestKeyLoader {

    private TestKeyLoader() {}

    public static String loadKey(String propertyName) {
        // propertyName is expected to be "openai" or "anthropic"
        String yamlKey = readFromYaml(propertyName);
        return yamlKey == null ? null : yamlKey.trim();
    }

    public static String readProperty(String propertyPath) {
        if (propertyPath == null || propertyPath.isBlank()) {
            return null;
        }

        String content = readYamlContent();
        if (content == null || content.isBlank()) {
            return null;
        }

        String[] parts = propertyPath.split("\\.");
        if (parts.length == 1) {
            return readFromYaml(parts[0]);
        }

        StringBuilder patternBuilder = new StringBuilder("(?ms)");
        for (int i = 0; i < parts.length - 1; i++) {
            patternBuilder.append("^\\s*")
                    .append(Pattern.quote(parts[i]))
                    .append(":\\s*$.*?");
        }
        patternBuilder.append("^\\s*")
                .append(Pattern.quote(parts[parts.length - 1]))
                .append(":\\s*(\\S+)\\s*$");

        Pattern p = Pattern.compile(patternBuilder.toString());
        Matcher m = p.matcher(content);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String readFromYaml(String propertyName) {
        String content = readYamlContent();
        if (content == null || content.isBlank()) {
            return null;
        }

        // look for app: ... keys: ... <propertyName>: <value>
        // simple regex to find a line like 'openai: sk-xxxxx' (allow leading spaces)
        Pattern p = Pattern.compile("(?m)^\\s*" + Pattern.quote(propertyName) + ":\\s*(\\S+)\\s*$");
        Matcher m = p.matcher(content);
        if (m.find()) {
            return m.group(1);
        }

        // also try full path key: app.keys.openai style (no dots in YAML, but just in case)
        p = Pattern.compile("(?m)^\\s*app:\\s*\\n(?:(?:[ \\t].*\\n)*)?\\s*keys:\\s*\\n(?:(?:[ \\t].*\\n)*)?\\s*" + Pattern.quote(propertyName) + ":\\s*(\\S+)\\s*$");
        m = p.matcher(content);
        if (m.find()) return m.group(1);

        return null;
    }

    private static String readYamlContent() {
        InputStream is = TestKeyLoader.class.getClassLoader().getResourceAsStream("application-local.yaml");
        if (is == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }
}

