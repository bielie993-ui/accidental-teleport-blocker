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
            keyName = "",
            name = "<html><div style='width:171px; text-align:center; color: white'>"
                    + "Use SHIFT + RIGHT-CLICK on any teleport spell in any spellbook to start blocking specific teleports!<br>"
                    + "</div></html>",
            description = "",
            position = 0,
            section = generalSettingsSection
    )
    default void generalExplanationLabel() {
    }

    @ConfigItem(
            keyName = "enableModifierKey",
            name = "Require modifier key",
            description = "Allow pressing a modifier key to bypass the block",
            section = generalSettingsSection,
            position = 1
    )
    default boolean enableModifierKey() { return true; }

    @ConfigItem(
            keyName = "modifierKey",
            name = "Modifier key",
            description = "Hold this key while left clicking to teleport",
            section = generalSettingsSection,
            position = 2
    )
    default ModifierKey modifierKey() { return ModifierKey.CTRL; }

    @ConfigItem(
            keyName = "allowRightClickWithoutModifier",
            name = "Allow right-click casting",
            description = "If enabled, casting from the right-click menu is always allowed",
            section = generalSettingsSection,
            position = 3
    )
    default boolean allowRightClickWithoutModifier() { return true; }

    // ─────────────────────── Spell activation settings ───────────────────────
    @ConfigSection(
            name = "Spell activation settings",
            description = "Settings to only block teleports after casting certain spells",
            position = 1
    )
    String activationSection = "activationDelay";

    @ConfigItem(
            keyName = "",
            name = "<html><div style='width:171px; text-align:center; color: white'>"
                    + "Use SHIFT + RIGHT-CLICK on any non-teleport spell in any spellbook to add a trigger!<br>"
                    + "</div></html>",
            description = "",
            position = 0,
            section = activationSection
    )
    default void spellActivationExplanationLabel() {
    }

    @ConfigItem(
            keyName = "enableSpellTriggerList",
            name = "Enable spell list",
            description = "When enabled, teleports are only blocked if you cast any spell from the custom list within the last X seconds.",
            section = activationSection,
            position = 1
    )
    default boolean enableCustomTriggerSpells() { return false; }

    @ConfigItem(
            keyName = "customTriggerSpells",
            name = "Spell list",
            description = "Comma-separated list of spell names that will trigger the block delay window. Can also be modified through the spellbook shift + right-click menu.",
            section = activationSection,
            position = 2
    )
    default String customTriggerSpells() { return "high level alchemy, low level alchemy"; }

    @Range(min = 1, max = 600)
    @Units(Units.SECONDS)
    @ConfigItem(
            keyName = "activationDelaySeconds",
            name = "Unblock delay",
            description = "How long should teleports be blocked for after a cast?",
            section = activationSection,
            position = 3
    )
    default int activationDelaySeconds() { return 5; }
}
