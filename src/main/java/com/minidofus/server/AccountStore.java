package com.minidofus.server;

import com.minidofus.model.progression.CharacterBuild;
import com.minidofus.util.Json;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Petite base de données des comptes, sans dépendance : un fichier JSON par compte dans {@code <dossier>/accounts},
 * chargés en mémoire au démarrage et réécrits à chaque modification. L'écriture passe par un fichier temporaire
 * renommé d'un coup : un fichier n'est jamais à moitié écrit, même si le serveur s'arrête au mauvais moment.
 * Sauvegarder la base = copier le dossier.
 *
 * <p>Mots de passe : le client n'envoie jamais le mot de passe, mais une clé dérivée (PBKDF2, voir
 * {@link #clientKey}) ; le serveur stocke un second hachage salé de cette clé.
 *
 * <p>Utilisée uniquement par le fil logique du serveur (pas de synchronisation nécessaire).
 */
public final class AccountStore {

    public static final int MAX_CHARACTERS = 6;

    /** Personnage d'un compte : déblocages (niveau, XP) et choix configurables (talents, sorts équipés) séparés. */
    public static final class Character {
        public String id;
        public String name;
        public String classId;
        public int level = 1;
        public int xp;
        public Map<String, Integer> talents = new TreeMap<>();
        public List<String> spells = new ArrayList<>();
        public int wins;
        public int losses;

        public CharacterBuild build() {
            return new CharacterBuild(name, classId, level, talents, spells);
        }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("classId", classId);
            m.put("level", level);
            m.put("xp", xp);
            m.put("talents", talents);
            m.put("spells", spells);
            m.put("wins", wins);
            m.put("losses", losses);
            return m;
        }

        @SuppressWarnings("unchecked")
        static Character fromJson(Map<String, Object> m) {
            Character c = new Character();
            c.id = Json.string(m.get("id"), "");
            c.name = Json.string(m.get("name"), "");
            c.classId = Json.string(m.get("classId"), "warrior");
            c.level = Json.integer(m.get("level"), 1);
            c.xp = Json.integer(m.get("xp"), 0);
            if (m.get("talents") instanceof Map<?, ?> t) {
                for (Map.Entry<?, ?> e : t.entrySet()) c.talents.put(String.valueOf(e.getKey()), Json.integer(e.getValue(), 0));
            }
            if (m.get("spells") instanceof List<?> l) for (Object o : l) c.spells.add(String.valueOf(o));
            c.wins = Json.integer(m.get("wins"), 0);
            c.losses = Json.integer(m.get("losses"), 0);
            return c;
        }
    }

    public static final class Account {
        public final String name;
        final String salt;
        final String hash;
        public final List<Character> characters = new ArrayList<>();

        Account(String name, String salt, String hash) {
            this.name = name;
            this.salt = salt;
            this.hash = hash;
        }

        public Character character(String id) {
            for (Character c : characters) if (c.id.equals(id)) return c;
            return null;
        }

        /** Vue envoyée au client (sans les données d'authentification). */
        public String toClientJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            List<Object> list = new ArrayList<>();
            for (Character c : characters) list.add(c.toJson());
            m.put("characters", list);
            return Json.write(m);
        }
    }

    private final Path dir;
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public AccountStore(Path dataDir) throws IOException {
        this.dir = dataDir.resolve("accounts");
        Files.createDirectories(dir);
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                try {
                    Account a = read(Files.readString(p, StandardCharsets.UTF_8));
                    accounts.put(key(a.name), a);
                } catch (RuntimeException e) {
                    System.err.println("[comptes] fichier illisible ignoré : " + p + " (" + e.getMessage() + ")");
                }
            }
        }
    }

    public int size() { return accounts.size(); }

    public Account get(String name) {
        return accounts.get(key(name));
    }

    /** Crée un compte ; {@code null} si le nom est pris. */
    public Account create(String name, String clientKey) throws IOException {
        if (accounts.containsKey(key(name))) return null;
        String salt = HexFormat.of().formatHex(random.generateSeed(16));
        Account a = new Account(name, salt, hash(clientKey, salt));
        accounts.put(key(name), a);
        save(a);
        return a;
    }

    public boolean verify(Account a, String clientKey) {
        String h = hash(clientKey, a.salt);
        return MessageDigest.isEqual(h.getBytes(StandardCharsets.US_ASCII), a.hash.getBytes(StandardCharsets.US_ASCII));
    }

    public String newCharacterId() {
        return HexFormat.of().formatHex(random.generateSeed(5));
    }

    /** Réécrit le fichier du compte (atomiquement si le système le permet). */
    public void save(Account a) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", a.name);
        m.put("salt", a.salt);
        m.put("hash", a.hash);
        List<Object> list = new ArrayList<>();
        for (Character c : a.characters) list.add(c.toJson());
        m.put("characters", list);
        Path target = dir.resolve(fileName(a.name));
        Path tmp = dir.resolve(fileName(a.name) + ".tmp");
        Files.writeString(tmp, Json.write(m), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @SuppressWarnings("unchecked")
    private static Account read(String text) {
        Map<String, Object> m = Json.object(text);
        Account a = new Account(Json.string(m.get("name"), ""), Json.string(m.get("salt"), ""), Json.string(m.get("hash"), ""));
        if (m.get("characters") instanceof List<?> l) {
            for (Object o : l) a.characters.add(Character.fromJson((Map<String, Object>) o));
        }
        return a;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /** Nom de fichier sûr : hexadécimal du nom (les pseudos peuvent contenir des accents, des espaces…). */
    private static String fileName(String name) {
        return HexFormat.of().formatHex(key(name).getBytes(StandardCharsets.UTF_8)) + ".json";
    }

    // ------------------------------------------------------------------ hachage

    /** Clé dérivée côté client : le mot de passe lui-même ne quitte jamais le PC du joueur. */
    public static String clientKey(String accountName, String password) {
        return pbkdf2(password, "spells-and-swords|" + key(accountName), 60_000);
    }

    private static String hash(String clientKey, String salt) {
        return pbkdf2(clientKey, salt, 20_000);
    }

    private static String pbkdf2(String secret, String salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), iterations, 256);
            byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
