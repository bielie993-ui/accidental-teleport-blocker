package com.AccidentalTeleportBlocker;

import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
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

/**
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

    /**
     * Loads the blocked teleports configuration from persistent storage
     * Initializes the blocked teleports map for each spellbook
     */
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

    /**
     * Loads the list of custom trigger spells from the plugin configuration
     * These spells will trigger the block delay window when cast
     */
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

    /**
     * Saves the current blocked teleports configuration to persistent storage
     * This ensures the user's preferences are preserved between sessions
     */
    private void saveBlockedTeleports() {
        for (Map.Entry<String, Set<String>> entry : blockedTeleportsPerSpellbook.entrySet()) {
            String spellbook = entry.getKey();
            Set<String> teleports = entry.getValue();

            configManager.setConfiguration("AccidentalTeleportBlocker", BLOCKED_TELEPORTS_KEY_PREFIX + spellbook, String.join(",", teleports));
        }
    }

    /**
     * Handles menu entry additions - adds "Enable/Disable block" options to teleport spells
     * and "Block Trigger" options to non-teleport spells
     * This runs when right-click menus are being built, allowing users to toggle blocking per teleport
     * and manually add non-teleport spells to the trigger list
     * Only shows these options when SHIFT is held down to avoid cluttering the menu
     */
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

            // Add our custom menu entry for teleports
            client.createMenuEntry(-1)
                    .setOption(menuText)
                    .setTarget(target)
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> {
                        // Toggle the blocked status when clicked
                        if (blocked) {
                            unblockTeleport(baseTeleport);
                        } else {
                            blockTeleport(baseTeleport);
                        }
                        saveBlockedTeleports(); // Persist the change
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

    /**
     * Main event handler for menu option clicks
     * Handles both tracking custom spell casting and blocking teleport usage
     */
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        String option = event.getMenuOption();
        String target = event.getMenuTarget();

        // Track when custom trigger spells are cast to start the block delay window
        if (config.enableCustomTriggerSpells() && option.equals("Cast")) {
            String spellName = getSpellNameFromTarget(target);
            if (isCustomTriggerSpell(spellName)) {
                lastCustomSpellCastAt = Instant.now();
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

        // Only block actual spell casting (left-click on spells)
        if (event.getMenuAction() != MenuAction.CC_OP && event.getMenuAction() != MenuAction.CC_OP_LOW_PRIORITY) {
            return;
        }

        // Only process blocked teleports
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

        // Check direct match first
        if (spellbookBlockedTeleports.contains(baseTeleport)) {
            return true;
        }

        // Check if any grouped teleports are blocked
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

    /**
     * Adds a spell to the custom trigger spells list if it's not already present
     * Automatically saves the updated list to the configuration in alphabetical order
     *
     * @param spellName The name of the spell to add
     */
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

    /**
     * Removes a spell from the custom trigger spells list if it exists
     * Automatically saves the updated list to the configuration in alphabetical order
     *
     * @param spellName The name of the spell to remove
     */
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
