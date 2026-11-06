package com.minidofus.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lecture et écriture JSON minimales, sans dépendance : objets ({@link Map}), tableaux ({@link List}), chaînes,
 * nombres ({@link Long} ou {@link Double}), booléens et {@code null}. Sert aux contenus (talents) et aux
 * fichiers de la base de comptes.
 */
public final class Json {

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.skip();
        Object v = p.value();
        p.skip();
        if (p.pos != text.length()) throw p.error("fin de texte attendue");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String text) {
        return (Map<String, Object>) parse(text);
    }

    private Object value() {
        if (pos >= text.length()) throw error("valeur attendue");
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> obj();
            case '[' -> arr();
            case '"' -> str();
            case 't' -> word("true", Boolean.TRUE);
            case 'f' -> word("false", Boolean.FALSE);
            case 'n' -> word("null", null);
            default -> num();
        };
    }

    private Map<String, Object> obj() {
        Map<String, Object> m = new LinkedHashMap<>();
        pos++;
        skip();
        if (peek('}')) return m;
        while (true) {
            skip();
            String k = str();
            skip();
            expect(':');
            skip();
            m.put(k, value());
            skip();
            if (peek('}')) return m;
            expect(',');
        }
    }

    private List<Object> arr() {
        List<Object> l = new ArrayList<>();
        pos++;
        skip();
        if (peek(']')) return l;
        while (true) {
            skip();
            l.add(value());
            skip();
            if (peek(']')) return l;
            expect(',');
        }
    }

    private String str() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = text.charAt(pos++);
            switch (e) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> sb.append(e);
            }
        }
        throw error("chaîne non terminée");
    }

    private Object num() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) pos++;
        String n = text.substring(start, pos);
        if (n.isEmpty()) throw error("caractère inattendu");
        if (n.contains(".") || n.contains("e") || n.contains("E")) return Double.parseDouble(n);
        return Long.parseLong(n);
    }

    private Object word(String w, Object v) {
        if (!text.startsWith(w, pos)) throw error("mot inattendu");
        pos += w.length();
        return v;
    }

    private void skip() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
    }

    private boolean peek(char c) {
        if (pos < text.length() && text.charAt(pos) == c) {
            pos++;
            return true;
        }
        return false;
    }

    private void expect(char c) {
        if (!peek(c)) throw error("« " + c + " » attendu");
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("JSON invalide (" + what + ") à la position " + pos);
    }

    // ------------------------------------------------------------------ écriture

    public static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        write(sb, v);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            sb.append('"');
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                        else sb.append(c);
                    }
                }
            }
            sb.append('"');
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                write(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> l) {
            sb.append('[');
            boolean first = true;
            for (Object o : l) {
                if (!first) sb.append(',');
                first = false;
                write(sb, o);
            }
            sb.append(']');
        } else {
            sb.append(v);
        }
    }

    // ------------------------------------------------------------------ lecture typée

    public static int integer(Object v, int def) {
        return v instanceof Number n ? n.intValue() : def;
    }

    public static String string(Object v, String def) {
        return v instanceof String s ? s : def;
    }
}
