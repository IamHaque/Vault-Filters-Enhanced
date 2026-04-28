package net.joseph.vaultfilters.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight YAML parser optimized for filter export/import.
 * Supports:
 * - Maps and lists via indentation
 * - Quoted and unquoted strings
 * - Boolean and numeric values
 * - Comments (lines starting with #)
 * - Nested structures
 *
 * This is a minimal implementation focused on the v3 filter format,
 * not a full YAML specification parser.
 */
public class YamlParser {
    private static final Pattern COMMENT_PATTERN = Pattern.compile("\\s*#.*$");
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("^([a-zA-Z_][a-zA-Z0-9_]*)\\s*:\\s*(.*)$");
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^\\s*-\\s+(.*)$");
    private static final Pattern QUOTED_STRING_PATTERN = Pattern.compile("^(['\"])(.*)\\1$");

    /**
     * Parse YAML text into a Map structure.
     * @param yaml YAML text to parse
     * @return Map representing the parsed YAML
     * @throws YamlParseException if parsing fails
     */
    public static Map<String, Object> parseYaml(String yaml) throws YamlParseException {
        if (yaml == null || yaml.trim().isEmpty()) {
            throw new YamlParseException("Empty YAML input");
        }

        String[] lines = yaml.split("\n");
        List<String> trimmedLines = new ArrayList<>();

        for (String line : lines) {
            String trimmed = removeComments(line);
            if (!trimmed.isEmpty()) {
                trimmedLines.add(line);
            }
        }

        if (trimmedLines.isEmpty()) {
            throw new YamlParseException("No content in YAML after removing comments");
        }

        try {
            ParseResult result = parseBlock(trimmedLines, 0, -1);
            if (!(result.value instanceof Map)) {
                throw new YamlParseException("Root YAML element must be a map");
            }
            return (Map<String, Object>) result.value;
        } catch (YamlParseException e) {
            throw e;
        } catch (Exception e) {
            throw new YamlParseException("YAML parsing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Dump a Map to YAML string format.
     * @param data Map to dump
     * @return YAML string
     */
    public static String dumpYaml(Map<String, Object> data) {
        return dumpValue(data, 0);
    }

    private static String dumpValue(Object value, int indent) {
        if (value == null) {
            return "null";
        }

        if (value instanceof Boolean) {
            return value.toString();
        }

        if (value instanceof Number) {
            return value.toString();
        }

        if (value instanceof String) {
            String str = (String) value;
            if (needsQuotes(str)) {
                return "\"" + escapeString(str) + "\"";
            }
            return str;
        }

        if (value instanceof Map) {
            return dumpMap((Map<String, Object>) value, indent);
        }

        if (value instanceof List) {
            return dumpList((List<Object>) value, indent);
        }

        return value.toString();
    }

    private static String dumpMap(Map<String, Object> map, int indent) {
        StringBuilder sb = new StringBuilder();
        String indentStr = "  ".repeat(indent);
        String childIndentStr = "  ".repeat(indent + 1);

        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append("\n");
            }
            first = false;

            sb.append(indentStr).append(entry.getKey()).append(": ");
            Object val = entry.getValue();

            if (val instanceof Map || val instanceof List) {
                sb.append("\n").append(childIndentStr).append(dumpValue(val, indent + 1));
            } else {
                sb.append(dumpValue(val, indent));
            }
        }

        return sb.toString();
    }

    private static String dumpList(List<Object> list, int indent) {
        StringBuilder sb = new StringBuilder();
        String indentStr = "  ".repeat(indent);
        String itemIndentStr = "  ".repeat(indent);

        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }

            Object item = list.get(i);
            sb.append(itemIndentStr).append("- ");

            if (item instanceof Map || item instanceof List) {
                sb.append("\n").append("  ".repeat(indent + 1)).append(dumpValue(item, indent + 1));
            } else {
                sb.append(dumpValue(item, indent));
            }
        }

        return sb.toString();
    }

    private static boolean needsQuotes(String str) {
        if (str.isEmpty()) return true;
        if (str.equalsIgnoreCase("true") || str.equalsIgnoreCase("false") || str.equalsIgnoreCase("null")) {
            return true;
        }
        if (str.contains(":") || str.contains("#") || str.contains("[") || str.contains("]") ||
            str.contains("{") || str.contains("}") || str.contains(",")) {
            return true;
        }
        if (Character.isDigit(str.charAt(0)) || str.charAt(0) == '-') {
            return true;
        }
        return false;
    }

    private static String escapeString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private static ParseResult parseBlock(List<String> lines, int startIdx, int parentIndent) throws YamlParseException {
        Map<String, Object> map = new LinkedHashMap<>();
        int idx = startIdx;

        while (idx < lines.size()) {
            String line = lines.get(idx);
            int indent = getIndentation(line);
            String content = line.substring(indent);

            if (content.isEmpty()) {
                idx++;
                continue;
            }

            if (parentIndent >= 0 && indent <= parentIndent) {
                break;
            }

            if (parentIndent < 0) {
                parentIndent = indent;
            }

            if (indent != parentIndent) {
                break;
            }

            Matcher keyValueMatcher = KEY_VALUE_PATTERN.matcher(content);
            if (keyValueMatcher.matches()) {
                String key = keyValueMatcher.group(1);
                String valueStr = keyValueMatcher.group(2).trim();

                idx++;
                Object value = parseValue(valueStr, lines, idx, indent);

                if (value instanceof ParseResult) {
                    ParseResult pr = (ParseResult) value;
                    map.put(key, pr.value);
                    idx = pr.nextLineIdx;
                } else {
                    map.put(key, value);
                }
                continue;
            }

            Matcher listMatcher = LIST_ITEM_PATTERN.matcher(content);
            if (listMatcher.matches()) {
                String itemStr = listMatcher.group(1).trim();
                List<Object> list = new ArrayList<>();

                Object itemValue = parseValue(itemStr, lines, idx + 1, indent + 2);
                if (itemValue instanceof ParseResult) {
                    ParseResult pr = (ParseResult) itemValue;
                    list.add(pr.value);
                    idx = pr.nextLineIdx;
                } else {
                    list.add(itemValue);
                    idx++;
                }

                while (idx < lines.size()) {
                    String nextLine = lines.get(idx);
                    int nextIndent = getIndentation(nextLine);
                    String nextContent = nextLine.substring(nextIndent);

                    Matcher nextListMatcher = LIST_ITEM_PATTERN.matcher(nextContent);
                    if (nextIndent == indent && nextListMatcher.matches()) {
                        String nextItemStr = nextListMatcher.group(1).trim();
                        Object nextItemValue = parseValue(nextItemStr, lines, idx + 1, indent + 2);
                        if (nextItemValue instanceof ParseResult) {
                            ParseResult pr = (ParseResult) nextItemValue;
                            list.add(pr.value);
                            idx = pr.nextLineIdx;
                        } else {
                            list.add(nextItemValue);
                            idx++;
                        }
                    } else {
                        break;
                    }
                }

                if (map.isEmpty()) {
                    return new ParseResult(list, idx);
                } else {
                    throw new YamlParseException("Cannot mix map and list at same level");
                }
            }

            idx++;
        }

        return new ParseResult(map, idx);
    }

    private static Object parseValue(String valueStr, List<String> lines, int nextIdx, int expectedIndent) throws YamlParseException {
        if (valueStr.isEmpty()) {
            if (nextIdx < lines.size()) {
                String nextLine = lines.get(nextIdx);
                int nextIndent = getIndentation(nextLine);
                if (nextIndent > expectedIndent - 2) {
                    return parseBlock(lines, nextIdx, expectedIndent);
                }
            }
            return null;
        }

        if (valueStr.equalsIgnoreCase("true")) {
            return true;
        }
        if (valueStr.equalsIgnoreCase("false")) {
            return false;
        }
        if (valueStr.equalsIgnoreCase("null")) {
            return null;
        }

        Matcher quotedMatcher = QUOTED_STRING_PATTERN.matcher(valueStr);
        if (quotedMatcher.matches()) {
            return unescapeString(quotedMatcher.group(2));
        }

        try {
            if (valueStr.contains(".")) {
                return Double.parseDouble(valueStr);
            } else {
                return Integer.parseInt(valueStr);
            }
        } catch (NumberFormatException e) {
            return valueStr;
        }
    }

    private static String unescapeString(String str) {
        return str.replace("\\n", "\n")
                  .replace("\\r", "\r")
                  .replace("\\t", "\t")
                  .replace("\\\"", "\"")
                  .replace("\\\\", "\\");
    }

    private static String removeComments(String line) {
        Matcher matcher = COMMENT_PATTERN.matcher(line);
        if (matcher.find()) {
            return line.substring(0, matcher.start());
        }
        return line;
    }

    private static int getIndentation(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') {
                count++;
            } else if (c == '\t') {
                count += 2;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * Result of parsing a block, including the value and next line index
     */
    private static class ParseResult {
        Object value;
        int nextLineIdx;

        ParseResult(Object value, int nextLineIdx) {
            this.value = value;
            this.nextLineIdx = nextLineIdx;
        }
    }

    /**
     * Exception thrown during YAML parsing
     */
    public static class YamlParseException extends Exception {
        public YamlParseException(String message) {
            super(message);
        }

        public YamlParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
