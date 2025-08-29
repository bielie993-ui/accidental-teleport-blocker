package com.AccidentalTeleportBlocker;

import net.runelite.client.config.*;

@ConfigGroup("AccidentalTeleportBlocker")
public interface AccidentalTeleportBlockerPluginConfig extends Config
{
    enum ModifierKey { CTRL, SHIFT }

    // ─────────────────────── General Settings ───────────────────────
    @ConfigSection(
            name = "General settings",
            description = "General settings",
            position = 0
    )
    String generalSettingsSection = "generalSettings";

    @ConfigItem(
            keyName = "modifierKey",
            name = "Modifier key",
            description = "Hold this key while left clicking to teleport",
            section = generalSettingsSection,
            position = 0
    )
    default ModifierKey modifierKey() { return ModifierKey.CTRL; }

    @ConfigItem(
            keyName = "allowRightClickWithoutModifier",
            name = "Allow right-click casting",
            description = "If enabled, casting from the right-click menu is always allowed",
            section = generalSettingsSection,
            position = 1
    )
    default boolean allowRightClickWithoutModifier() { return true; }

    // ─────────────────────── Block window after alch ───────────────────────
    @ConfigSection(
            name = "Block window",
            description = "Enabled: Only block teleports for X seconds after casting any alchemy spell",
            position = 1
    )
    String activationSection = "activationDelay";

    @ConfigItem(
            keyName = "enableAfterAlchDelay",
            name = "Enable block window",
            description = "When enabled, teleports are only blocked if you cast an alchemy spell within the last X seconds.",
            section = activationSection,
            position = 0
    )
    default boolean enableAfterAlchDelay() { return false; }

    @Range(min = 1, max = 600)
    @Units(Units.SECONDS)
    @ConfigItem(
            keyName = "activationDelaySeconds",
            name = "Block window length",
            description = "How long should teleports be blocked for after an alchemy cast?",
            section = activationSection,
            position = 1
    )
    default int activationDelaySeconds() { return 5; }


    // ─────────────────────── Teleports Section ───────────────────────

    @ConfigSection(
            name = "Teleports to block",
            description = "Select which regular spellbook teleports require the modifier key.",
            position = 1
    )
    String teleportSection = "teleports";
    String defaultTPDescription = "Enable modifier for this teleport";

    @ConfigItem(
            keyName = "blockKourend",
            name = "Kourend Castle",
            description = defaultTPDescription,
            section = teleportSection,
            position = 10
    )
    default boolean blockKourend() { return true; } // default ON


    @ConfigItem(
            keyName = "blockFalador",
            name = "Falador",
            description = defaultTPDescription,
            section = teleportSection,
            position = 11
    )
    default boolean blockFalador() { return true; } // default ON

    @ConfigItem(
            keyName = "blockVarrock",
            name = "Varrock / GE",
            description = defaultTPDescription,
            section = teleportSection,
            position = 12
    )
    default boolean blockVarrock() { return false; }

    @ConfigItem(
            keyName = "blockLumbridge",
            name = "Lumbridge",
            description = defaultTPDescription,
            section = teleportSection,
            position = 13
    )
    default boolean blockLumbridge() { return false; }

    @ConfigItem(
            keyName = "blockCamelotSeers",
            name = "Camelot / Seers' Village",
            description = defaultTPDescription,
            section = teleportSection,
            position = 14
    )
    default boolean blockCamelotSeers() { return false; }

    @ConfigItem(
            keyName = "blockArdougne",
            name = "Ardougne",
            description = defaultTPDescription,
            section = teleportSection,
            position = 15
    )
    default boolean blockArdougne() { return false; }

    @ConfigItem(
            keyName = "blockWatchtower",
            name = "Watchtower / Yanille",
            description = defaultTPDescription,
            section = teleportSection,
            position = 16
    )
    default boolean blockWatchtower() { return false; }

    @ConfigItem(
            keyName = "blockTrollheim",
            name = "Trollheim",
            description = defaultTPDescription,
            section = teleportSection,
            position = 17
    )
    default boolean blockTrollheim() { return false; }

    @ConfigItem(
            keyName = "blockApeAtoll",
            name = "Ape Atoll",
            description = defaultTPDescription,
            section = teleportSection,
            position = 18
    )
    default boolean blockApeAtoll() { return false; }

    @ConfigItem(
            keyName = "blockHouse",
            name = "House",
            description = defaultTPDescription,
            section = teleportSection,
            position = 19
    )
    default boolean blockHouse() { return false; }

    @ConfigItem(
            keyName = "blockCivitas",
            name = "Civitas illa Fortis",
            description = defaultTPDescription,
            section = teleportSection,
            position = 20
    )
    default boolean blockCivitas() { return false; }
}
