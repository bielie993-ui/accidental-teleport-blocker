package com.AccidentalTeleportBlocker;

import com.google.inject.Provides;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
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
    @Inject
    private ConfigManager configManager;
    @Inject
    private MenuManager menuManager;

    private volatile boolean ctrlDown = false;
    private volatile boolean shiftDown = false;
    private volatile Instant lastAlchemyCastAt = null;
    private volatile Instant lastOffensiveCastAt = null;

    private static final int ANIM_LOW_ALCH = 712;
    private static final int ANIM_HIGH_ALCH = 713;
//    private static final int[] ANIM_OFFENSIVE = {711, 9144, 11429, 9145, 10091, 10092};
    private static final String BLOCKED_TELEPORTS_KEY_PREFIX = "blockedTeleports_";

    private final Map<String, Set<String>> blockedTeleportsPerSpellbook = new HashMap<>();

    private static final String STANDARD_SPELLBOOK = "standard";
    private static final String ANCIENT_SPELLBOOK = "ancient";
    private static final String LUNAR_SPELLBOOK = "lunar";
    private static final String ARCEUUS_SPELLBOOK = "arceuus";

    /* Exceptions to the base teleport names */
    private static final Map<String, Set<String>> TELEPORT_GROUPS = Map.of(
            "varrock teleport", Set.of("grand exchange"),
            "teleport to house", Set.of("outside"),
            "camelot teleport", Set.of("seers"),
            "watchtower teleport", Set.of("yanille")
    );

    private String getBaseTeleportName(String teleportName) {
        String lower = teleportName.toLowerCase();

        for (Map.Entry<String, Set<String>> entry : TELEPORT_GROUPS.entrySet()) {
            if (entry.getValue().stream().anyMatch(lower::contains)) {
                return entry.getKey();
            }
        }

        return lower;
    }

    @Override
    protected void startUp() {
        keyManager.registerKeyListener(this);
        loadBlockedTeleports();
    }

    @Override
    protected void shutDown() {
        keyManager.unregisterKeyListener(this);
        ctrlDown = false;
        shiftDown = false;
        lastAlchemyCastAt = null;
//        lastOffensiveCastAt = null;
        blockedTeleportsPerSpellbook.clear();
    }

    private void loadBlockedTeleports() {
        for (String spellbook : new String[]{STANDARD_SPELLBOOK, ANCIENT_SPELLBOOK, LUNAR_SPELLBOOK, ARCEUUS_SPELLBOOK}) {
            String blocked = configManager.getConfiguration("AccidentalTeleportBlocker", BLOCKED_TELEPORTS_KEY_PREFIX + spellbook);
            Set<String> blockedSet = new HashSet<>();

            if (blocked != null && !blocked.isEmpty()) {
                for (String s : blocked.split(",")) {
                    blockedSet.add(s.trim().toLowerCase());
                }
            }

            blockedTeleportsPerSpellbook.put(spellbook, blockedSet);
        }
    }

    private void saveBlockedTeleports() {
        for (Map.Entry<String, Set<String>> entry : blockedTeleportsPerSpellbook.entrySet()) {
            String spellbook = entry.getKey();
            Set<String> teleports = entry.getValue();
            configManager.setConfiguration("AccidentalTeleportBlocker", BLOCKED_TELEPORTS_KEY_PREFIX + spellbook, String.join(",", teleports));
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged e) {
//        if (!config.enableAfterAlchDelay() && !config.enableAfterOffensiveDelay()) {
        if (!config.enableAfterAlchDelay()) {
            return;
        }

        final Actor a = e.getActor();

        if (a == null || a != client.getLocalPlayer()) {
            return;
        }

        final int anim = a.getAnimation();

        if (config.enableAfterAlchDelay() && (anim == ANIM_LOW_ALCH || anim == ANIM_HIGH_ALCH)) {
            lastAlchemyCastAt = Instant.now();
        }

//        for (int offensiveAnim : ANIM_OFFENSIVE) {
//            if (config.enableAfterOffensiveDelay() && anim == offensiveAnim) {
//                lastOffensiveCastAt = Instant.now();
//                break;
//            }
//        }
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (!shiftDown) return;
        String option = event.getOption();
        String target = event.getTarget();

        if (isTeleportSpellOption(target) && (option.equals("Cast"))) {
            String teleport = getTeleportNameFromTarget(target);
            String baseTeleport = getBaseTeleportName(teleport);
            boolean blocked = isBlockedTeleport(baseTeleport);
            String menuText = blocked ? "Disable block" : "Enable block";

            client.createMenuEntry(-1)
                    .setOption(menuText)
                    .setTarget(target)
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> {
                        if (blocked) {
                            unblockTeleport(baseTeleport);
                        } else {
                            blockTeleport(baseTeleport);
                        }
                        saveBlockedTeleports();
                    });
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        String option = event.getMenuOption();
        String target = event.getMenuTarget();

        if (!isTeleportSpellOption(target)) {
            return;
        }

        if (option.equals("Enable block") || option.equals("Disable block")) {
            String teleport = getTeleportNameFromTarget(target);
            String baseTeleport = getBaseTeleportName(teleport);

            if (option.equals("Enable block")) {
                blockTeleport(baseTeleport);
            } else {
                unblockTeleport(baseTeleport);
            }

            saveBlockedTeleports();
            event.consume();

            return;
        }

        if (event.getMenuAction() != MenuAction.CC_OP && event.getMenuAction() != MenuAction.CC_OP_LOW_PRIORITY) {
            return;
        }

        if (!isBlockedTeleportTarget(target)) {
            return;
        }

        if (config.allowRightClickWithoutModifier() && client.isMenuOpen()) {
            return;
        }

        boolean shouldBlockAfterAlch = false;
        if (config.enableAfterAlchDelay() && lastAlchemyCastAt != null) {
            long sinceAlch = Duration.between(lastAlchemyCastAt, Instant.now()).getSeconds();
            shouldBlockAfterAlch = sinceAlch <= config.activationDelaySeconds();
        }

//        boolean shouldBlockAfterOffensive = false;
//        if (config.enableAfterOffensiveDelay() && lastOffensiveCastAt != null) {
//            long sinceOffensiveSpell = Duration.between(lastOffensiveCastAt, Instant.now()).getSeconds();
//            System.out.println("im here: " + sinceOffensiveSpell + " " + config.activationDelaySeconds());
//            shouldBlockAfterOffensive = sinceOffensiveSpell <= config.activationDelaySeconds();
//        }

//        if (!shouldBlockAfterAlch && !shouldBlockAfterOffensive) {
        if (!shouldBlockAfterAlch) {
            return;
        }

        if (!config.enableModifierKey()) {
            event.consume();
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "This teleport is being blocked by the Accidental Teleport Blocker plugin!", null);
            return;
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

            if (shouldBlockAfterAlch) {
                long sinceAlch = Duration.between(lastAlchemyCastAt, Instant.now()).getSeconds();
                long remainingAlch = config.activationDelaySeconds() - sinceAlch;
                if (remainingAlch >= 0) {
                    blockedMessage += getSecondsMessage((int) remainingAlch);
                }
            }
//
//            if (shouldBlockAfterOffensive) {
//                long sinceOffensive = Duration.between(lastOffensiveCastAt, Instant.now()).getSeconds();
//                long remainingOffensive = config.activationDelaySeconds() - sinceOffensive;
//                if (remainingOffensive >= 0) {
//                    blockedMessage += getSecondsMessage((int) remainingOffensive);
//                }
//            }

            blockedMessage += "!";
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", blockedMessage, null);
        }
    }

    private String getSecondsMessage(Integer remaining) {
        String secondsString = " seconds";
        if (remaining <= 0) {
            remaining = 1;
        }
        if (remaining == 1) secondsString = " second";
        return " or wait " + remaining + secondsString;
    }

    private boolean isTeleportSpellOption(String target) {
        String tgt = target.toLowerCase().replaceAll("<.*?>", "").replaceAll("[^a-z ]", "").trim();

        return tgt.contains("teleport") || tgt.contains("tele group");
    }

    private String getTeleportNameFromTarget(String target) {
        return target.toLowerCase().replaceAll("<.*?>", "").replaceAll("[^a-z ]", "").trim();
    }

    private String getCurrentSpellbook() {
        int spellbookVar = client.getVarbitValue(4070); // Spellbook varbit
        switch (spellbookVar) {
            case 1:
                return ANCIENT_SPELLBOOK;
            case 2:
                return LUNAR_SPELLBOOK;
            case 3:
                return ARCEUUS_SPELLBOOK;
            default:
                return STANDARD_SPELLBOOK;
        }
    }

    private boolean isBlockedTeleport(String baseTeleport) {
        String currentSpellbook = getCurrentSpellbook();
        Set<String> spellbookBlockedTeleports = blockedTeleportsPerSpellbook.get(currentSpellbook);

        if (spellbookBlockedTeleports == null) {
            return false;
        }

        if (spellbookBlockedTeleports.contains(baseTeleport)) {
            return true;
        }

        return TELEPORT_GROUPS.entrySet().stream()
                .anyMatch(entry -> {
                    boolean keyBlocked = spellbookBlockedTeleports.contains(entry.getKey());
                    boolean containsTarget = entry.getValue().stream().anyMatch(baseTeleport::contains);
                    return keyBlocked && containsTarget;
                });
    }

    private void blockTeleport(String baseTeleport) {
        String currentSpellbook = getCurrentSpellbook();
        Set<String> spellbookBlockedTeleports = blockedTeleportsPerSpellbook.get(currentSpellbook);
        if (spellbookBlockedTeleports != null) {
            spellbookBlockedTeleports.add(baseTeleport);
        }
    }

    private void unblockTeleport(String baseTeleport) {
        String currentSpellbook = getCurrentSpellbook();
        Set<String> spellbookBlockedTeleports = blockedTeleportsPerSpellbook.get(currentSpellbook);
        if (spellbookBlockedTeleports != null) {
            spellbookBlockedTeleports.remove(baseTeleport);
        }
    }

    private boolean isBlockedTeleportTarget(String target) {
        String lowerTarget = target.toLowerCase().replaceAll("<.*?>", "").replaceAll("[^a-z ]", "").trim();
        String baseTeleport = getBaseTeleportName(lowerTarget);
        String currentSpellbook = getCurrentSpellbook();
        Set<String> spellbookBlockedTeleports = blockedTeleportsPerSpellbook.get(currentSpellbook);

        if (spellbookBlockedTeleports == null) {
            return false;
        }

        if (spellbookBlockedTeleports.contains(baseTeleport)) {
            return true;
        }

        return TELEPORT_GROUPS.entrySet().stream()
                .anyMatch(entry -> {
                    boolean keyBlocked = spellbookBlockedTeleports.contains(entry.getKey());
                    boolean containsTarget = entry.getValue().stream().anyMatch(lowerTarget::contains);
                    return keyBlocked && containsTarget;
                });
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
