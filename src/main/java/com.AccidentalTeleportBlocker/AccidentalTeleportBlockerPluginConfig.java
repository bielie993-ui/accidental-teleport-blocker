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
            keyName = "enableModifierKey",
            name = "Require modifier key",
            description = "Allow pressing a modifier key to bypass the block",
            section = generalSettingsSection,
            position = 0
    )
    default boolean enableModifierKey() { return true; }

    @ConfigItem(
            keyName = "modifierKey",
            name = "Modifier key",
            description = "Hold this key while left clicking to teleport",
            section = generalSettingsSection,
            position = 1
    )
    default ModifierKey modifierKey() { return ModifierKey.CTRL; }

    @ConfigItem(
            keyName = "allowRightClickWithoutModifier",
            name = "Allow right-click casting",
            description = "If enabled, casting from the right-click menu is always allowed",
            section = generalSettingsSection,
            position = 2
    )
    default boolean allowRightClickWithoutModifier() { return true; }

    // ─────────────────────── Block window after alch ───────────────────────
    @ConfigSection(
            name = "Block windows",
            description = "Only block teleports for X seconds after casting a spell from a specific spell group",
            position = 1
    )
    String activationSection = "activationDelay";

    @ConfigItem(
            keyName = "enableAfterAlchDelay",
            name = "Alchemy Spells",
            description = "When enabled, teleports are only blocked if you cast any alchemy spell within the last X seconds",
            section = activationSection,
            position = 0
    )
    default boolean enableAfterAlchDelay() { return false; }

//    @ConfigItem(
//            keyName = "enableAfterOffensiveDelay",
//            name = "Offensive Spells",
//            description = "When enabled, teleports are only blocked if you cast any offensive spell within the last X seconds.",
//            section = activationSection,
//            position = 1
//    )
//    default boolean enableAfterOffensiveDelay() { return false; }

    @Range(min = 1, max = 600)
    @Units(Units.SECONDS)
    @ConfigItem(
            keyName = "activationDelaySeconds",
            name = "Block window length",
            description = "How long should teleports be blocked for after a cast?",
            section = activationSection,
            position = 2
    )
    default int activationDelaySeconds() { return 5; }
}
