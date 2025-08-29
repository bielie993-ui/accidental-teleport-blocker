package com.AccidentalTeleportBlocker;

import com.google.inject.Provides;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@PluginDescriptor(
        name = "Accidental Teleport Blocker"
)
public class AccidentalTeleportBlockerPlugin extends Plugin implements KeyListener {
    @Inject
    private Client client;
    @Inject
    private KeyManager keyManager;
    @Inject
    private AccidentalTeleportBlockerPluginConfig config;

    private volatile boolean ctrlDown = false;
    private volatile boolean shiftDown = false;
    private volatile Instant lastAlchemyCastAt = null;

    private static final int ANIM_LOW_ALCH = 712;
    private static final int ANIM_HIGH_ALCH = 713;

    @Override
    protected void startUp() {
        keyManager.registerKeyListener(this);
    }

    @Override
    protected void shutDown() {
        keyManager.unregisterKeyListener(this);
        ctrlDown = false;
        shiftDown = false;
        lastAlchemyCastAt = null;
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged e) {
        if (!config.enableAfterAlchDelay()) {
            return;
        }

        final Actor a = e.getActor();

        if (a == null || a != client.getLocalPlayer()) {
            return;
        }

        final int anim = a.getAnimation();

        if (anim == ANIM_LOW_ALCH || anim == ANIM_HIGH_ALCH) {
            lastAlchemyCastAt = Instant.now();
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (event.getMenuAction() != MenuAction.CC_OP && event.getMenuAction() != MenuAction.CC_OP_LOW_PRIORITY) return;

        final String option = event.getMenuOption().toLowerCase();
        final String target = event.getMenuTarget().toLowerCase();

        Set<String> allowedOptions = Set.of("cast", "seers", "grand", "yanille", "outside");
        if (!allowedOptions.contains(option)) {
            return;
        }

        if (!isBlockedTeleportTarget(target)) {
            return;
        }

        if (config.allowRightClickWithoutModifier() && client.isMenuOpen()) {
            return;
        }

        if (config.enableAfterAlchDelay()) {
            if (lastAlchemyCastAt == null) {
                return;
            }

            long sinceAlch = Duration.between(lastAlchemyCastAt, Instant.now()).getSeconds();
            if (sinceAlch > config.activationDelaySeconds()) {
                return;
            }
        }

        boolean modifierHeld;
        String keyName;
        switch (config.modifierKey()) {
            case SHIFT:
                modifierHeld = shiftDown;
                keyName = "SHIFT";
                break;
            case CTRL:
            default:
                modifierHeld = ctrlDown;
                keyName = "CTRL";
                break;
        }

        if (!modifierHeld) {
            event.consume();
            String blockedMessage = "Hold " + keyName + " to use this teleport";

            if (config.enableAfterAlchDelay() && lastAlchemyCastAt != null) {
                long sinceAlch = java.time.Duration.between(lastAlchemyCastAt, java.time.Instant.now()).getSeconds();
                long remaining = config.activationDelaySeconds() - sinceAlch;
                if (remaining >= 0) {
                    String secondsString = " seconds";
                    if (remaining == 0) remaining = 1;
                    if (remaining == 1) secondsString = " second";
                    blockedMessage += " or wait " + remaining + secondsString;
                }
            }

            blockedMessage += "!";
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", blockedMessage, null);
        }
    }

    private boolean isAlchemyName(String name) {
        return name.contains("high level alchemy") || name.contains("low level alchemy");
    }

    private boolean isAlchemyCastTarget(String target) {
        if (isAlchemyName(target)) return true;
        return target.contains(" alch");
    }

    private boolean isBlockedTeleportTarget(String target) {
        String lowerTarget = target.toLowerCase();

        Map<String, Boolean> blocks = Map.ofEntries(
                Map.entry("varrock", config.blockVarrock()),
                Map.entry("grand exchange", config.blockVarrock()),
                Map.entry("lumbridge", config.blockLumbridge()),
                Map.entry("falador", config.blockFalador()),
                Map.entry("camelot", config.blockCamelotSeers()),
                Map.entry("seers", config.blockCamelotSeers()),
                Map.entry("ardougne", config.blockArdougne()),
                Map.entry("watchtower", config.blockWatchtower()),
                Map.entry("yanille", config.blockWatchtower()),
                Map.entry("trollheim", config.blockTrollheim()),
                Map.entry("ape atoll", config.blockApeAtoll()),
                Map.entry("kourend", config.blockKourend()),
                Map.entry("house", config.blockHouse()),
                Map.entry("outside", config.blockHouse()),
                Map.entry("civitas", config.blockCivitas())
        );

        return blocks.entrySet().stream()
                .anyMatch(e -> e.getValue() && lowerTarget.contains(e.getKey()));
    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_CONTROL:
                ctrlDown = true;
                break;
            case KeyEvent.VK_SHIFT:
                shiftDown = true;
                break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_CONTROL:
                ctrlDown = false;
                break;
            case KeyEvent.VK_SHIFT:
                shiftDown = false;
                break;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) { /* unused */ }

    @Provides
    AccidentalTeleportBlockerPluginConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AccidentalTeleportBlockerPluginConfig.class);
    }
}
