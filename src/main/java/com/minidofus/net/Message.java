package com.minidofus.net;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Message du protocole : un type suivi d'arguments texte, transmis sur une ligne. Les arguments sont
 * séparés par des tabulations ; tabulations, retours à la ligne et barres obliques inverses sont échappés.
 */
public record Message(String type, List<String> args) {

    /** Taille maximale d'une ligne reçue (protection contre les envois abusifs). */
    public static final int MAX_LINE = 64_000;

    public static Message of(String type, Object... args) {
        List<String> list = new ArrayList<>(args.length);
        for (Object a : args) list.add(String.valueOf(a));
        return new Message(type, List.copyOf(list));
    }

    public String arg(int i) {
        return i < args.size() ? args.get(i) : "";
    }

    public int intArg(int i, int fallback) {
        try {
            return Integer.parseInt(arg(i));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public long longArg(int i, long fallback) {
        try {
            return Long.parseLong(arg(i));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public String encode() {
        StringBuilder sb = new StringBuilder(escape(type));
        for (String a : args) sb.append('\t').append(escape(a));
        return sb.toString();
    }

    public static Message decode(String line) {
        String[] parts = line.split("\t", -1);
        List<String> args = new ArrayList<>();
        for (String p : Arrays.asList(parts).subList(1, parts.length)) args.add(unescape(p));
        return new Message(unescape(parts[0]), List.copyOf(args));
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\t' -> sb.append("\\t");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(switch (n) {
                    case 't' -> '\t';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    default -> n;
                });
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return encode().replace('\t', ' ');
    }
}
