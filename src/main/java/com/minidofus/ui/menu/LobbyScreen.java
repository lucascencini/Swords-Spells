package com.minidofus.ui.menu;

import com.minidofus.engine.Assets;
import com.minidofus.model.FighterClass;
import com.minidofus.model.Team;
import com.minidofus.model.ai.Difficulty;
import com.minidofus.model.catalog.FighterCatalog;
import com.minidofus.model.map.MapTheme;
import com.minidofus.net.LobbyState;
import com.minidofus.net.Message;
import com.minidofus.net.Protocol;
import com.minidofus.ui.ChatLog;
import com.minidofus.ui.UiTheme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Salon en ligne : réglages (modifiables par le chef), places des deux équipes, joueurs et chat.
 * Toutes les actions sont envoyées au serveur ; l'affichage suit l'état qu'il renvoie.
 */
public final class LobbyScreen implements Screen {

    private static final int SLOT_W = 128;
    private static final int SLOT_H = 150;
    private static final String[] TIMER_LABELS = {"Sans", "30 s", "60 s", "90 s"};

    private final Assets assets;
    private final Consumer<Message> send;
    private final Runnable onLeave;
    private final Consumer<Graphics2D> background;
    private final ChatLog chat;
    private final TextField chatInput = new TextField("Écrire un message… (Entrée pour envoyer)", Protocol.MAX_CHAT, false);
    private final MenuButton leave;
    private final MenuButton start;
    private LobbyState state = new LobbyState();
    private int myId = -1;
    private String serverLabel = "";
    private String info = "";

    // zones cliquables recalculées à chaque image
    private final List<Rectangle> slotBoxes = new ArrayList<>();
    private final List<Rectangle> slotButtons = new ArrayList<>();
    private final List<Rectangle> classPrev = new ArrayList<>();
    private final List<Rectangle> classNext = new ArrayList<>();
    private final Rectangle[] sizeBoxes = new Rectangle[3];
    private final Rectangle[] difficultyBoxes = new Rectangle[3];
    private final Rectangle[] timerBoxes = new Rectangle[4];
    private Rectangle mapPrev = new Rectangle();
    private Rectangle mapNext = new Rectangle();
    private Rectangle panel = new Rectangle();
    private int width;
    private int height;
    private int mouseX = -1;
    private int mouseY = -1;

    public LobbyScreen(Assets assets, ChatLog chat, Consumer<Graphics2D> background, Consumer<Message> send, Runnable onLeave) {
        this.assets = assets;
        this.chat = chat;
        this.background = background;
        this.send = send;
        this.onLeave = onLeave;
        leave = new MenuButton("Quitter le salon", null, false, onLeave);
        start = new MenuButton("Lancer le combat", null, true, () -> send.accept(Message.of(Protocol.START)));
    }

    public void setState(LobbyState state, int myId) {
        this.state = state;
        this.myId = myId;
    }

    /** Adresse à communiquer aux amis, affichée en haut du salon. */
    public void setServerLabel(String label) {
        this.serverLabel = label;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    private boolean isChef() {
        return state.chefId == myId;
    }

    // ------------------------------------------------------------------ mise en page

    @Override
    public void layout(int w, int h) {
        width = w;
        height = h;
        int pw = Math.min(w - 30, 1100);
        int ph = Math.min(h - 30, 720);
        panel = new Rectangle((w - pw) / 2, (h - ph) / 2, pw, ph);
        leave.setBounds(panel.x + 30, panel.y + ph - 62, 190, 44);
        int chatX = panel.x + pw - 330;
        start.setBounds(chatX - 260, panel.y + ph - 66, 230, 50);
        chatInput.setBounds(chatX, panel.y + ph - 62, 300, 44);
    }

    @Override
    public void update(double dt) {
        chatInput.update(dt);
    }

    // ------------------------------------------------------------------ dessin

    @Override
    public void draw(Graphics2D g) {
        background.accept(g);
        g.setColor(new Color(5, 4, 2, 185));
        g.fillRect(0, 0, width, height);
        UiTheme.panel(g, panel.x, panel.y, panel.width, panel.height, 22, true);
        UiTheme.drawCentered(g, "Salon en ligne", UiTheme.TITLE.deriveFont(26f), panel.x + (panel.width - 330) / 2.0,
                panel.y + 44, UiTheme.GOLD_LIGHT);
        if (!serverLabel.isEmpty()) {
            UiTheme.drawCentered(g, serverLabel, UiTheme.BODY.deriveFont(12.5f), panel.x + (panel.width - 330) / 2.0,
                    panel.y + 66, UiTheme.TEXT_DIM);
        }
        int left = panel.x + 30;
        int contentW = panel.width - 330 - 60;
        drawSettings(g, left, panel.y + 92, contentW);
        drawTeams(g, left, panel.y + 214, contentW);
        drawSide(g, panel.x + panel.width - 330, panel.y + 24, 300);
        leave.draw(g, mouseX, mouseY);
        start.setEnabled(isChef());
        if (isChef()) {
            start.draw(g, mouseX, mouseY);
        } else {
            UiTheme.drawCentered(g, "En attente du chef du salon…", UiTheme.BODY_BOLD.deriveFont(13.5f),
                    start.bounds().getCenterX(), start.bounds().getCenterY() + 5, UiTheme.TEXT_DIM);
        }
        if (!info.isEmpty()) {
            UiTheme.drawCentered(g, info, UiTheme.BODY_BOLD.deriveFont(13f), left + contentW / 2.0, panel.y + panel.height - 82,
                    UiTheme.DAMAGE_HEAVY);
        }
    }

    private void drawSettings(Graphics2D g, int x, int y, int w) {
        boolean chef = isChef();
        g.setFont(UiTheme.BODY_BOLD.deriveFont(13.5f));
        g.setColor(UiTheme.PARCHMENT);
        g.drawString("Carte", x, y + 22);
        g.drawString("Équipes", x, y + 66);
        g.drawString("Ordinateur", x + w / 2 + 10, y + 66);
        g.drawString("Chrono", x + w / 2 + 10, y + 22);

        // carte
        Rectangle box = new Rectangle(x + 90, y, w / 2 - 110, 34);
        mapPrev = new Rectangle(box.x, box.y, 34, 34);
        mapNext = new Rectangle(box.x + box.width - 34, box.y, 34, 34);
        frame(g, box);
        if (chef) {
            arrow(g, mapPrev, "‹");
            arrow(g, mapNext, "›");
        }
        UiTheme.drawCentered(g, state.map == null ? "Au hasard" : state.map.displayName(), UiTheme.BODY_BOLD.deriveFont(14f),
                box.getCenterX(), box.y + 23, UiTheme.PARCHMENT);

        // chrono, taille d'équipe, difficulté
        segments(g, timerBoxes, TIMER_LABELS, indexOf(LobbyState.TIMER_CHOICES, state.turnSeconds),
                x + w / 2 + 90, y, w / 2 - 90, chef);
        segments(g, sizeBoxes, new String[] {"1 c. 1", "2 c. 2", "3 c. 3"}, state.teamSize - 1, x + 90, y + 44, w / 2 - 110, chef);
        String[] diff = {Difficulty.EASY.displayName(), Difficulty.NORMAL.displayName(), Difficulty.HARD.displayName()};
        segments(g, difficultyBoxes, diff, state.difficulty.ordinal(), x + w / 2 + 90, y + 44, w / 2 - 90, chef);
        if (!chef) {
            g.setFont(UiTheme.BODY.deriveFont(11.5f));
            g.setColor(UiTheme.TEXT_DIM);
            g.drawString("Seul le chef du salon modifie les réglages.", x, y + 104);
        }
    }

    private void segments(Graphics2D g, Rectangle[] boxes, String[] labels, int selected, int x, int y, int w, boolean active) {
        frame(g, new Rectangle(x, y, w, 34));
        double cell = w / (double) labels.length;
        for (int i = 0; i < labels.length; i++) {
            Rectangle r = new Rectangle((int) (x + i * cell) + 3, y + 3, (int) cell - 6, 28);
            boxes[i] = r;
            if (i == selected) {
                g.setPaint(new GradientPaint(0, r.y, new Color(0xC9A227), 0, r.y + r.height, new Color(0x7A5E14)));
                g.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 10, 10));
            } else if (active && r.contains(mouseX, mouseY)) {
                g.setColor(new Color(255, 255, 255, 25));
                g.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 10, 10));
            }
            UiTheme.drawCentered(g, labels[i], UiTheme.BODY_BOLD.deriveFont(12.5f), r.getCenterX(), r.y + 19,
                    i == selected ? new Color(0x1A1208) : active ? UiTheme.PARCHMENT : UiTheme.TEXT_DIM);
        }
    }

    private void drawTeams(Graphics2D g, int x, int y, int w) {
        slotBoxes.clear();
        slotButtons.clear();
        classPrev.clear();
        classNext.clear();
        int half = w / 2;
        for (int t = 0; t < 2; t++) {
            Team team = t == 0 ? Team.RED : Team.BLUE;
            int colX = x + t * half;
            UiTheme.drawCentered(g, "Équipe " + team.displayName(), UiTheme.TITLE.deriveFont(17f), colX + half / 2.0, y + 14,
                    UiTheme.teamTextColor(team));
            int total = state.teamSize * SLOT_W + (state.teamSize - 1) * 10;
            int sx = colX + (half - total) / 2;
            for (int i = 0; i < 3; i++) {
                int slot = LobbyState.slotOf(team, i);
                Rectangle r = new Rectangle(sx + i * (SLOT_W + 10), y + 30, SLOT_W, SLOT_H);
                if (i >= state.teamSize) r = new Rectangle(-1000, -1000, 0, 0);
                slotBoxes.add(r);
                slotButtons.add(new Rectangle(r.x + 10, r.y + r.height + 8, r.width - 20, 30));
                classPrev.add(new Rectangle(r.x, r.y + 20, 26, 60));
                classNext.add(new Rectangle(r.x + r.width - 26, r.y + 20, 26, 60));
                if (i < state.teamSize) drawSlot(g, slot, r, team);
            }
        }
    }

    private void drawSlot(Graphics2D g, int slot, Rectangle r, Team team) {
        FighterClass c = FighterCatalog.byId(state.slotClass[slot]);
        int owner = state.slotOwner[slot];
        boolean mine = owner == myId;
        boolean editable = canEditClass(slot);
        RoundRectangle2D shape = new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 14, 14);
        g.setPaint(new GradientPaint(0, r.y, mine ? new Color(0x4A3C22) : new Color(0x3A3020), 0, r.y + r.height, new Color(0x1E1810)));
        g.fill(shape);
        g.setStroke(new BasicStroke(mine ? 3f : 2f));
        g.setColor(mine ? UiTheme.GOLD_LIGHT : UiTheme.teamColor(team));
        g.draw(shape);
        g.setStroke(new BasicStroke(1f));
        BufferedImage portrait = assets.image("ui/portrait_" + c.id());
        int ph = 64;
        int pw = (int) Math.round(portrait.getWidth() * (ph / (double) portrait.getHeight()));
        g.drawImage(portrait, r.x + (r.width - pw) / 2, r.y + 10, pw, ph, null);
        if (editable) {
            UiTheme.drawCentered(g, "‹", UiTheme.TITLE.deriveFont(22f), r.x + 13, r.y + 52, UiTheme.GOLD_LIGHT);
            UiTheme.drawCentered(g, "›", UiTheme.TITLE.deriveFont(22f), r.x + r.width - 13, r.y + 52, UiTheme.GOLD_LIGHT);
        }
        UiTheme.drawCentered(g, c.displayName(), UiTheme.TITLE.deriveFont(15f), r.getCenterX(), r.y + 96, UiTheme.PARCHMENT);
        String ownerName = state.ownerName(slot);
        LobbyState.Player p = state.player(owner);
        String who = ownerName == null ? "Ordinateur (" + state.difficulty.displayName().toLowerCase() + ")"
                : ownerName + (p != null && !p.connected() ? " (absent)" : "");
        fitted(g, who, r.getCenterX(), r.y + 122, r.width - 10,
                ownerName == null ? UiTheme.TEXT_DIM : mine ? UiTheme.GOLD_LIGHT : UiTheme.teamTextColor(team));
        // bouton prendre / libérer
        Rectangle b = new Rectangle(r.x + 10, r.y + r.height + 8, r.width - 20, 30);
        String label = mine ? "Libérer" : owner < 0 ? "Prendre" : null;
        if (label != null) {
            boolean hover = b.contains(mouseX, mouseY);
            g.setColor(mine ? new Color(0x4A3020) : new Color(0x2D6B3A));
            if (hover) g.setColor(g.getColor().brighter());
            g.fill(new RoundRectangle2D.Double(b.x, b.y, b.width, b.height, 10, 10));
            g.setColor(UiTheme.BRONZE);
            g.draw(new RoundRectangle2D.Double(b.x, b.y, b.width, b.height, 10, 10));
            UiTheme.drawCentered(g, label, UiTheme.BODY_BOLD.deriveFont(12.5f), b.getCenterX(), b.y + 20, UiTheme.PARCHMENT);
        }
    }

    private boolean canEditClass(int slot) {
        return state.slotOwner[slot] == myId || (isChef() && state.slotOwner[slot] < 0);
    }

    /** Colonne de droite : joueurs puis chat. */
    private void drawSide(Graphics2D g, int x, int y, int w) {
        g.setFont(UiTheme.TITLE.deriveFont(16f));
        g.setColor(UiTheme.GOLD_LIGHT);
        g.drawString("Joueurs (" + state.players.size() + "/" + Protocol.MAX_PLAYERS + ")", x, y + 20);
        int py = y + 34;
        for (LobbyState.Player p : state.players) {
            g.setColor(p.connected() ? UiTheme.HEAL : new Color(0x777777));
            g.fill(new Ellipse2D.Double(x + 2, py + 6, 9, 9));
            g.setFont(UiTheme.BODY_BOLD.deriveFont(13.5f));
            g.setColor(p.id() == myId ? UiTheme.GOLD_LIGHT : UiTheme.PARCHMENT);
            String suffix = (p.id() == state.chefId ? "  ♛ chef" : "") + (p.connected() ? "" : "  (déconnecté)");
            g.drawString(p.name() + suffix, x + 18, py + 15);
            py += 22;
        }
        int chatTop = y + 34 + Protocol.MAX_PLAYERS * 22 + 16;
        g.setFont(UiTheme.TITLE.deriveFont(16f));
        g.setColor(UiTheme.GOLD_LIGHT);
        g.drawString("Discussion", x, chatTop);
        Rectangle box = new Rectangle(x, chatTop + 10, w, chatInput.bounds().y - chatTop - 20);
        g.setColor(new Color(10, 8, 5, 170));
        g.fill(new RoundRectangle2D.Double(box.x, box.y, box.width, box.height, 12, 12));
        chat.draw(g, box.x + 10, box.y + box.height - 10, box.width - 20, (box.height - 14) / 17, 1.0);
        chatInput.draw(g, mouseX, mouseY);
    }

    private static void frame(Graphics2D g, Rectangle r) {
        g.setColor(new Color(10, 8, 5, 170));
        g.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 12, 12));
        g.setColor(UiTheme.BRONZE);
        g.draw(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 12, 12));
    }

    private void arrow(Graphics2D g, Rectangle r, String glyph) {
        g.setColor(r.contains(mouseX, mouseY) ? new Color(0x5A4A2A) : new Color(0x3A3020));
        g.fillRoundRect(r.x + 3, r.y + 3, r.width - 6, r.height - 6, 8, 8);
        UiTheme.drawCentered(g, glyph, UiTheme.TITLE.deriveFont(20f), r.getCenterX(), r.y + 25, UiTheme.GOLD_LIGHT);
    }

    private static void fitted(Graphics2D g, String text, double cx, double baseline, int maxW, Color color) {
        java.awt.Font f = UiTheme.BODY_BOLD.deriveFont(12f);
        FontMetrics fm = g.getFontMetrics(f);
        String t = text;
        while (fm.stringWidth(t) > maxW && t.length() > 3) t = t.substring(0, t.length() - 2) + "…";
        UiTheme.drawCentered(g, t, f, cx, baseline, color);
    }

    private static int indexOf(int[] values, int v) {
        for (int i = 0; i < values.length; i++) if (values[i] == v) return i;
        return -1;
    }

    // ------------------------------------------------------------------ entrées

    @Override
    public void mouseMoved(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    @Override
    public void mousePressed(int x, int y) {
        chatInput.setFocused(chatInput.bounds().contains(x, y));
        if (leave.press(x, y) || start.press(x, y)) return;
        for (int i = 0; i < slotBoxes.size(); i++) {
            int slot = i; // les boîtes sont dans l'ordre des places 0 à 5
            if (slotButtons.get(i).contains(x, y) && state.isUsed(slot)) {
                send.accept(Message.of(state.slotOwner[slot] == myId ? Protocol.RELEASE : Protocol.CLAIM, slot));
                return;
            }
            if (canEditClass(slot) && state.isUsed(slot)) {
                int step = classPrev.get(i).contains(x, y) ? -1 : classNext.get(i).contains(x, y) ? 1 : 0;
                if (step != 0) {
                    int idx = FighterCatalog.ALL.indexOf(FighterCatalog.byId(state.slotClass[slot]));
                    String next = FighterCatalog.ALL.get(Math.floorMod(idx + step, FighterCatalog.ALL.size())).id();
                    send.accept(Message.of(Protocol.CLASS, slot, next));
                    return;
                }
            }
        }
        if (!isChef()) return;
        MapTheme map = state.map;
        int size = state.teamSize;
        Difficulty diff = state.difficulty;
        int timer = state.turnSeconds;
        boolean changed = false;
        if (mapPrev.contains(x, y) || mapNext.contains(x, y)) {
            int count = MapTheme.values().length + 1;
            int idx = map == null ? 0 : map.ordinal() + 1;
            idx = Math.floorMod(idx + (mapPrev.contains(x, y) ? -1 : 1), count);
            map = idx == 0 ? null : MapTheme.values()[idx - 1];
            changed = true;
        }
        for (int i = 0; i < 3; i++) {
            if (sizeBoxes[i] != null && sizeBoxes[i].contains(x, y)) { size = i + 1; changed = true; }
            if (difficultyBoxes[i] != null && difficultyBoxes[i].contains(x, y)) { diff = Difficulty.values()[i]; changed = true; }
        }
        for (int i = 0; i < 4; i++) {
            if (timerBoxes[i] != null && timerBoxes[i].contains(x, y)) { timer = LobbyState.TIMER_CHOICES[i]; changed = true; }
        }
        if (changed) send.accept(Message.of(Protocol.SETTINGS, map == null ? "random" : map.name(), size, diff.name(), timer));
    }

    @Override
    public void key(String key) {
        switch (key) {
            case "ENTER" -> {
                if (chatInput.isFocused() && !chatInput.text().isBlank()) {
                    send.accept(Message.of(Protocol.CHAT, chatInput.text()));
                    chatInput.setText("");
                } else {
                    chatInput.setFocused(true);
                }
            }
            case "ESCAPE" -> {
                if (chatInput.isFocused()) chatInput.setFocused(false);
                else onLeave.run();
            }
            case "BACK_SPACE" -> chatInput.backspace();
            default -> { }
        }
    }

    @Override
    public boolean wantsText() {
        return chatInput.isFocused();
    }

    @Override
    public void text(String typed) {
        chatInput.insert(typed);
    }
}
