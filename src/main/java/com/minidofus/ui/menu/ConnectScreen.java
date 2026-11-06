package com.minidofus.ui.menu;

import com.minidofus.net.Protocol;
import com.minidofus.ui.UiTheme;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Connexion à un serveur : avec un compte (personnages et progression), ou en invité ; ou hébergement d'un
 * serveur sur ce PC. L'adresse, le compte et le mot de passe du serveur sont mémorisés ; jamais celui du compte.
 */
public final class ConnectScreen implements Screen {

    /** Ce que le joueur a saisi. {@code host} = adresse sans le port. */
    public record Request(String host, int port, String name, String password, boolean hostHere, Mode mode,
                          String accountPassword) { }

    /** Connexion à un compte, création d'un compte, invité sans compte. */
    public enum Mode { LOGIN, REGISTER, GUEST }

    private final Preferences prefs = prefs();
    private final TextField address = new TextField("adresse du serveur (ex. 82.165.10.20)", 80, false);
    private final TextField name = new TextField("nom du compte (ou pseudo d'invité)", Protocol.MAX_NAME, false);
    private final TextField accountPassword = new TextField("mot de passe du compte", 64, true);
    private final TextField password = new TextField("mot de passe du serveur (s'il en a un)", 40, true);
    private final List<TextField> fields = List.of(address, name, accountPassword, password);
    private final MenuButton connect;
    private final MenuButton register;
    private final MenuButton guest;
    private final MenuButton host;
    private final MenuButton back;
    private final Consumer<Graphics2D> background;
    private String status = "";
    private boolean statusIsError;
    private boolean busy;
    private int width;
    private int height;
    private int mouseX = -1;
    private int mouseY = -1;

    public ConnectScreen(Consumer<Graphics2D> background, Consumer<Request> onConnect, Runnable onBack) {
        this.background = background;
        address.setText(get("address", ""));
        name.setText(get("name", ""));
        password.setText(get("password", ""));
        connect = new MenuButton("Se connecter", null, true, () -> submit(onConnect, false, Mode.LOGIN));
        register = new MenuButton("Créer un compte", null, false, () -> submit(onConnect, false, Mode.REGISTER));
        guest = new MenuButton("Jouer en invité", "Sans personnage", false, () -> submit(onConnect, false, Mode.GUEST));
        host = new MenuButton("Héberger sur ce PC", "Réseau local", false, () -> submit(onConnect, true, Mode.LOGIN));
        back = new MenuButton("Retour", null, false, onBack);
        (address.text().isEmpty() ? address : name.text().isEmpty() ? name : address).setFocused(true);
    }

    private static Preferences prefs() {
        try {
            return Preferences.userRoot().node("swords-and-spells");
        } catch (RuntimeException | Error e) {
            return null; // stockage des préférences indisponible : rien n'est mémorisé
        }
    }

    private String get(String key, String fallback) {
        try {
            return prefs == null ? fallback : prefs.get(key, fallback);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private void put(String key, String value) {
        try {
            if (prefs != null) prefs.put(key, value);
        } catch (RuntimeException ignored) {
            // préférences non enregistrées
        }
    }

    private void submit(Consumer<Request> onConnect, boolean hostHere, Mode mode) {
        if (busy) return;
        String n = name.text().trim();
        if (n.isEmpty()) {
            setStatus(mode == Mode.GUEST ? "Choisissez un pseudo" : "Indiquez le nom du compte", true);
            focus(name);
            return;
        }
        if (mode != Mode.GUEST && accountPassword.text().isEmpty() && !hostHere) {
            setStatus("Indiquez le mot de passe du compte", true);
            focus(accountPassword);
            return;
        }
        if (mode == Mode.REGISTER && accountPassword.text().length() < 4) {
            setStatus("Mot de passe trop court (4 caractères au moins)", true);
            focus(accountPassword);
            return;
        }
        String addr = address.text().trim();
        String h = hostHere ? "127.0.0.1" : addr;
        int port = Protocol.DEFAULT_PORT;
        if (!hostHere) {
            if (addr.isEmpty()) {
                setStatus("Indiquez l'adresse du serveur", true);
                focus(address);
                return;
            }
            int colon = addr.lastIndexOf(':');
            if (colon > 0 && addr.indexOf(':') == colon) { // « hôte:port » (une seule fois « : », pas une adresse IPv6)
                try {
                    port = Integer.parseInt(addr.substring(colon + 1));
                    h = addr.substring(0, colon);
                } catch (NumberFormatException e) {
                    setStatus("Port invalide après « : »", true);
                    return;
                }
            }
        }
        put("address", addr);
        put("name", n);
        put("password", password.text());
        setStatus(hostHere ? "Démarrage du serveur…" : "Connexion à " + h + ":" + port + "…", false);
        busy = true;
        // en hébergeant sans mot de passe de compte : invité sur son propre serveur
        Mode m = hostHere && accountPassword.text().isEmpty() ? Mode.GUEST : mode;
        onConnect.accept(new Request(h, port, n, password.text(), hostHere, m, accountPassword.text()));
    }

    /** Message d'état ; une erreur réactive les boutons. */
    public void setStatus(String text, boolean error) {
        status = text;
        statusIsError = error;
        if (error) busy = false;
    }

    private void focus(TextField f) {
        for (TextField x : fields) x.setFocused(x == f);
    }

    @Override
    public void layout(int w, int h) {
        width = w;
        height = h;
        int fw = 440;
        int x = (w - fw) / 2;
        int y = h / 2 - 236;
        address.setBounds(x, y + 26, fw, 40);
        name.setBounds(x, y + 96, fw, 40);
        accountPassword.setBounds(x, y + 166, fw, 40);
        password.setBounds(x, y + 236, fw, 40);
        int half = (fw - 12) / 2;
        connect.setBounds(x, y + 296, half, 52);
        register.setBounds(x + half + 12, y + 296, half, 52);
        guest.setBounds(x, y + 360, half, 54);
        host.setBounds(x + half + 12, y + 360, half, 54);
        back.setBounds(x + (fw - 160) / 2, y + 426, 160, 44);
    }

    @Override
    public void update(double dt) {
        for (TextField f : fields) f.update(dt);
    }

    @Override
    public void draw(Graphics2D g) {
        background.accept(g);
        g.setColor(new Color(5, 4, 2, 185));
        g.fillRect(0, 0, width, height);
        int pw = 520;
        int ph = 592;
        int px = (width - pw) / 2;
        int py = height / 2 - 300;
        UiTheme.panel(g, px, py, pw, ph, 22, true);
        UiTheme.drawCentered(g, "Jouer en ligne", UiTheme.TITLE.deriveFont(26f), width / 2.0, py + 46, UiTheme.GOLD_LIGHT);
        label(g, "Serveur", address);
        label(g, "Compte", name);
        label(g, "Mot de passe du compte", accountPassword);
        label(g, "Mot de passe du serveur (facultatif)", password);
        for (TextField f : fields) f.draw(g, mouseX, mouseY);
        connect.draw(g, mouseX, mouseY);
        register.draw(g, mouseX, mouseY);
        guest.draw(g, mouseX, mouseY);
        host.draw(g, mouseX, mouseY);
        back.draw(g, mouseX, mouseY);
        if (!status.isEmpty()) {
            UiTheme.drawCentered(g, status, UiTheme.BODY_BOLD.deriveFont(13f), width / 2.0, py + ph - 18,
                    statusIsError ? UiTheme.DAMAGE : UiTheme.PARCHMENT);
        }
        UiTheme.drawCentered(g, "Port par défaut : " + Protocol.DEFAULT_PORT + " · autre port : adresse:port",
                UiTheme.BODY.deriveFont(11.5f), width / 2.0, py + ph + 22, UiTheme.TEXT_DIM);
    }

    private static void label(Graphics2D g, String text, TextField f) {
        g.setFont(UiTheme.BODY_BOLD.deriveFont(13f));
        g.setColor(UiTheme.PARCHMENT);
        g.drawString(text, f.bounds().x + 2, f.bounds().y - 8);
    }

    @Override
    public void mouseMoved(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    @Override
    public void mousePressed(int x, int y) {
        for (TextField f : fields) {
            if (f.bounds().contains(x, y)) {
                focus(f);
                return;
            }
        }
        if (connect.press(x, y) || register.press(x, y) || guest.press(x, y) || host.press(x, y)) return;
        back.press(x, y);
    }

    @Override
    public void key(String key) {
        TextField current = fields.stream().filter(TextField::isFocused).findFirst().orElse(null);
        switch (key) {
            case "ENTER" -> connect.press(connect.bounds().x + 1, connect.bounds().y + 1);
            case "ESCAPE" -> back.press(back.bounds().x + 1, back.bounds().y + 1);
            case "TAB", "DOWN" -> focus(fields.get((fields.indexOf(current) + 1) % fields.size()));
            case "shift TAB", "UP" -> focus(fields.get((fields.indexOf(current) + fields.size() - 1) % fields.size()));
            case "BACK_SPACE" -> {
                if (current != null) current.backspace();
            }
            default -> { }
        }
    }

    @Override
    public boolean wantsText() {
        return true;
    }

    @Override
    public void text(String typed) {
        for (TextField f : fields) if (f.isFocused()) f.insert(typed);
    }
}
