package com.AccidentalTeleportBlocker;

import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
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

/**
 * 30-08-2025 - Nobodycalled - Initial release
 * 31-08-2025 - Nobodycalled - Added support for custom trigger spells
 *
 * RuneLite plugin that prevents accidental teleport usage by requiring a modifier key
 * or by only blocking teleports for a limited time after casting specific trigger spells.
 * Features:
 * - Block teleport spells unless a modifier key (CTRL/SHIFT) is held
 * - Allow users to manage which teleports are blocked per spellbook
 * - Optionally only block teleports for X seconds after casting custom trigger spells
 * - Case-insensitive, comma-separated list of trigger spells configurable by user
 */
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
    private volatile Instant lastCustomSpellCastAt = null;

    private volatile String pendingSpellName = null;

    private static final String BLOCKED_TELEPORTS_KEY_PREFIX = "blockedTeleports_";

    private final Map<String, Set<String>> blockedTeleportsPerSpellbook = new HashMap<>();
    private final Set<String> customTriggerSpells = new HashSet<>();

    private static final String STANDARD_SPELLBOOK = "standard";
    private static final String ANCIENT_SPELLBOOK = "ancient";
    private static final String LUNAR_SPELLBOOK = "lunar";
    private static final String ARCEUUS_SPELLBOOK = "arceuus";

    /**
     * Maps base teleport names to their alternative names/locations
     */
    private static final Map<String, Set<String>> TELEPORT_GROUPS = Map.of(
            "varrock teleport", Set.of("grand exchange"),
            "teleport to house", Set.of("outside"),
            "camelot teleport", Set.of("seers"),
            "watchtower teleport", Set.of("yanille")
    );

    private String getBaseTeleportName(String teleportName) {
        String lower = teleportName.toLowerCase();

        // Check if this teleport name matches any of the alternative names
        for (Map.Entry<String, Set<String>> entry : TELEPORT_GROUPS.entrySet()) {
            if (entry.getValue().stream().anyMatch(lower::contains)) {
                return entry.getKey(); // Return the base name instead of the alternative
            }
        }

        return lower; // No grouping needed, return as-is
    }

    @Override
    protected void startUp() {
        keyManager.registerKeyListener(this);
        loadBlockedTeleports();
        loadCustomTriggerSpells();
    }

    @Override
    protected void shutDown() {
        keyManager.unregisterKeyListener(this);

        ctrlDown = false;
        shiftDown = false;
        lastCustomSpellCastAt = null;

        blockedTeleportsPerSpellbook.clear();
        customTriggerSpells.clear();
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

    private void loadCustomTriggerSpells() {
        customTriggerSpells.clear();
        String spellList = config.customTriggerSpells();

        if (spellList != null && !spellList.trim().isEmpty()) {
            String[] spells = spellList.split(",");
            for (String spell : spells) {
                customTriggerSpells.add(spell.trim().toLowerCase());
            }
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
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (!shiftDown) return;

        String option = event.getOption();
        String target = event.getTarget();
        String spellName = getSpellNameFromTarget(target);

        if (isTeleportSpellOption(target)) {
            // Handle teleport spells - existing functionality
            String baseTeleport = getBaseTeleportName(spellName);
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
        } else if (option.equals("Cast")) {
            loadCustomTriggerSpells();

            boolean isAlreadyTrigger = customTriggerSpells.contains(spellName);
            String menuText = isAlreadyTrigger ? "Remove block trigger" : "Add block trigger";

            client.createMenuEntry(-1)
                    .setOption(menuText)
                    .setTarget(target)
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> {
                        if (isAlreadyTrigger) {
                            removeFromCustomTriggerSpells(spellName);
                        } else {
                            addToCustomTriggerSpells(spellName);
                        }
                    });

        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        String option = event.getMenuOption();
        String target = event.getMenuTarget();

        if (config.enableCustomTriggerSpells()) {
            if (option.equals("Cast")) {
                pendingSpellName = getSpellNameFromTarget(target);
            } else {
                pendingSpellName = null;
            }
        }

        // Only process teleport-related menu clicks from here
        if (!isTeleportSpellOption(target)) {
            return;
        }

        // Handle our custom "Enable/Disable block" menu options
        if (option.equals("Enable block") || option.equals("Disable block")) {
            String teleport = getSpellNameFromTarget(target);
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

        // Don't process un-blocked teleports
        if (!isBlockedTeleportTarget(target)) {
            return;
        }

        // Allow to right-click menu casting if configured
        if (config.allowRightClickWithoutModifier() && client.isMenuOpen()) {
            return;
        }

        boolean shouldBlock = false;

        if (config.enableCustomTriggerSpells()) {
            if (lastCustomSpellCastAt != null) {
                long sinceCustomSpell = Duration.between(lastCustomSpellCastAt, Instant.now()).getSeconds();
                shouldBlock = sinceCustomSpell <= config.activationDelaySeconds();
            }
        } else {
            shouldBlock = true;
        }

        if (!shouldBlock) {
            return;
        }

        // Early return if modifier key bypass is disabled
        if (!config.enableModifierKey()) {
            event.consume();
            String blockedMessage = "ATB: This teleport is being blocked";

            if (config.enableCustomTriggerSpells() && lastCustomSpellCastAt != null) {
                long sinceCustomSpell = Duration.between(lastCustomSpellCastAt, Instant.now()).getSeconds();
                long remainingCustomSpell = config.activationDelaySeconds() - sinceCustomSpell;

                if (remainingCustomSpell >= 0) {
                    blockedMessage += getSecondsMessage((int) remainingCustomSpell, "please wait");
                }
            }

            blockedMessage += "!";
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", blockedMessage, null);

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

        // Block the teleport if modifier key is not held
        if (!modifierHeld) {
            event.consume();
            String blockedMessage = "ATB: Hold " + keyName + " to use this teleport";

            if (config.enableCustomTriggerSpells() && lastCustomSpellCastAt != null) {
                long sinceCustomSpell = Duration.between(lastCustomSpellCastAt, Instant.now()).getSeconds();
                long remainingCustomSpell = config.activationDelaySeconds() - sinceCustomSpell;

                if (remainingCustomSpell >= 0) {
                    blockedMessage += getSecondsMessage((int) remainingCustomSpell, "or wait");
                }
            }

            blockedMessage += "!";
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", blockedMessage, null);
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        // Check if a custom trigger spell was just cast and if any magic xp was gained (not 100% accurate but close enough)
        if (pendingSpellName != null && event.getSkill() == Skill.MAGIC) {
            if (isCustomTriggerSpell(pendingSpellName)) {
                lastCustomSpellCastAt = Instant.now();
            }

            pendingSpellName = null;
        }
    }

    private String getSecondsMessage(Integer remaining, String prefix) {
        String secondsString = " seconds";

        if (remaining <= 0) {
            remaining = 1;
        }

        if (remaining == 1) secondsString = " second";

        return " " + prefix + " " + remaining + secondsString;
    }

    private boolean isTeleportSpellOption(String target) {
        String tgt = target.toLowerCase().replaceAll("<.*?>", "").replaceAll("[^a-z ]", "").trim();

        return tgt.contains("teleport") || tgt.contains("tele group");
    }

    private String getSpellNameFromTarget(String target) {
        return target.toLowerCase().replaceAll("<.*?>", "").replaceAll("[^a-z ]", "").trim();
    }

    private boolean isCustomTriggerSpell(String spellName) {
        loadCustomTriggerSpells();

        return customTriggerSpells.stream().anyMatch(triggerSpell ->
                spellName.contains(triggerSpell) || triggerSpell.contains(spellName)
        );
    }


    private String getCurrentSpellbook() {
        int spellbookVar = client.getVarbitValue(4070); // Spellbook varbit from the game

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

        // Direct match
        if (spellbookBlockedTeleports.contains(baseTeleport)) {
            return true;
        }

        // Grouped teleports
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

        // Check direct match
        if (spellbookBlockedTeleports.contains(baseTeleport)) {
            return true;
        }

        // Check grouped teleports
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
    public void keyTyped(KeyEvent e) {
        // Not used
    }

    @Provides
    AccidentalTeleportBlockerPluginConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AccidentalTeleportBlockerPluginConfig.class);
    }

    private void addToCustomTriggerSpells(String spellName) {
        // Ignore empty or duplicate entries
        if (spellName == null || spellName.trim().isEmpty() || customTriggerSpells.contains(spellName)) {
            return;
        }

        customTriggerSpells.add(spellName.trim().toLowerCase());

        // Sort the spells alphabetically and update the configuration
        String updatedSpellList = customTriggerSpells.stream()
                .sorted()
                .collect(java.util.stream.Collectors.joining(", "));
        configManager.setConfiguration("AccidentalTeleportBlocker", "customTriggerSpells", updatedSpellList);
    }

    private void removeFromCustomTriggerSpells(String spellName) {
        // Ignore empty entries
        if (spellName == null || spellName.trim().isEmpty()) {
            return;
        }

        customTriggerSpells.remove(spellName.trim().toLowerCase());

        // Sort the spells alphabetically and update the configuration
        String updatedSpellList = customTriggerSpells.stream()
                .sorted()
                .collect(java.util.stream.Collectors.joining(", "));
        configManager.setConfiguration("AccidentalTeleportBlocker", "customTriggerSpells", updatedSpellList);
    }
}
