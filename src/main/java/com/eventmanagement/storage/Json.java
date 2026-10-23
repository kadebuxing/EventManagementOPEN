package com.eventmanagement.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON writer/parser used by {@link JsonStorage}.
 *
 * <p>Only the JSON subset needed by the store is supported: objects, arrays,
 * strings, numbers, booleans and null. Keeping this in the project avoids any
 * third-party dependency.</p>
 */
final class Json {
    private Json() {
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------
    static String stringify(Object value) {
        StringBuilder builder = new StringBuilder();
        write(value, builder, 0);
        return builder.toString();
    }

    private static void write(Object value, StringBuilder builder, int level) {
        if (value == null) {
            builder.append("null");
            return;
        }
        if (value instanceof String text) {
            writeString(text, builder);
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            builder.append(value);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            builder.append("{\n");
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                indent(builder, level + 1);
                writeString(String.valueOf(entry.getKey()), builder);
                builder.append(": ");
                write(entry.getValue(), builder, level + 1);
                if (++index < map.size()) {
                    builder.append(',');
                }
                builder.append('\n');
            }
            indent(builder, level);
            builder.append('}');
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> items = new ArrayList<>();
            iterable.forEach(items::add);
            builder.append("[\n");
            for (int index = 0; index < items.size(); index++) {
                indent(builder, level + 1);
                write(items.get(index), builder, level + 1);
                if (index < items.size() - 1) {
                    builder.append(',');
                }
                builder.append('\n');
            }
            indent(builder, level);
            builder.append(']');
            return;
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
    }

    private static void writeString(String value, StringBuilder builder) {
        builder.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        builder.append('"');
    }

    private static void indent(StringBuilder builder, int level) {
        builder.append("  ".repeat(level));
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------
    static Object parse(String text) {
        return new Parser(text).parseDocument();
    }

    private static final class Parser {
        private final String text;
        private int position;

        private Parser(String text) {
            this.text = text;
        }

        private Object parseDocument() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (position != text.length()) {
                throw error("Unexpected trailing content");
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (position >= text.length()) {
                throw error("Unexpected end of input");
            }
            char character = text.charAt(position);
            if (character == '{') {
                return parseObject();
            }
            if (character == '[') {
                return parseArray();
            }
            if (character == '"') {
                return parseString();
            }
            if (character == 't') {
                return parseLiteral("true", Boolean.TRUE);
            }
            if (character == 'f') {
                return parseLiteral("false", Boolean.FALSE);
            }
            if (character == 'n') {
                return parseLiteral("null", null);
            }
            if (character == '-' || (character >= '0' && character <= '9')) {
                return parseNumber();
            }
            throw error("Unexpected character '" + character + "'");
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                position++;
                return result;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw error("Expected a string key");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                result.put(key, parseValue());
                skipWhitespace();
                if (peek(',')) {
                    position++;
                    continue;
                }
                expect('}');
                return result;
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                position++;
                return result;
            }
            while (true) {
                result.add(parseValue());
                skipWhitespace();
                if (peek(',')) {
                    position++;
                    continue;
                }
                expect(']');
                return result;
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (position < text.length()) {
                char character = text.charAt(position++);
                if (character == '"') {
                    return builder.toString();
                }
                if (character == '\\') {
                    if (position >= text.length()) {
                        throw error("Unterminated escape sequence");
                    }
                    char escape = text.charAt(position++);
                    switch (escape) {
                        case '"' -> builder.append('"');
                        case '\\' -> builder.append('\\');
                        case '/' -> builder.append('/');
                        case 'b' -> builder.append('\b');
                        case 'f' -> builder.append('\f');
                        case 'n' -> builder.append('\n');
                        case 'r' -> builder.append('\r');
                        case 't' -> builder.append('\t');
                        case 'u' -> {
                            if (position + 4 > text.length()) {
                                throw error("Invalid unicode escape");
                            }
                            String hex = text.substring(position, position + 4);
                            try {
                                builder.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException ex) {
                                throw error("Invalid unicode escape");
                            }
                            position += 4;
                        }
                        default -> throw error("Invalid escape sequence \\" + escape);
                    }
                } else {
                    if (character < 0x20) {
                        throw error("Unescaped control character");
                    }
                    builder.append(character);
                }
            }
            throw error("Unterminated string");
        }

        private Object parseNumber() {
            int start = position;
            while (position < text.length()) {
                char character = text.charAt(position);
                if ((character >= '0' && character <= '9')
                        || character == '-'
                        || character == '+'
                        || character == '.'
                        || character == 'e'
                        || character == 'E') {
                    position++;
                } else {
                    break;
                }
            }
            String number = text.substring(start, position);
            try {
                if (number.indexOf('.') >= 0
                        || number.indexOf('e') >= 0
                        || number.indexOf('E') >= 0) {
                    return Double.valueOf(number);
                }
                return Long.valueOf(number);
            } catch (NumberFormatException ex) {
                throw error("Invalid number '" + number + "'");
            }
        }

        private Object parseLiteral(String literal, Object value) {
            if (!text.regionMatches(position, literal, 0, literal.length())) {
                throw error("Invalid literal");
            }
            position += literal.length();
            return value;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (position >= text.length() || text.charAt(position) != expected) {
                throw error("Expected '" + expected + "'");
            }
            position++;
        }

        private boolean peek(char expected) {
            return position < text.length() && text.charAt(position) == expected;
        }

        private void skipWhitespace() {
            while (position < text.length()) {
                char character = text.charAt(position);
                if (character == ' ' || character == '\n'
                        || character == '\r' || character == '\t') {
                    position++;
                } else {
                    break;
                }
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + position);
        }
    }
}
