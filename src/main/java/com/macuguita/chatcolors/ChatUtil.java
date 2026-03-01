/*
 * Copyright (c) 2026 macuguita
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.macuguita.chatcolors;

import java.awt.*;
import java.util.List;
import java.util.UUID;

import dev.terminalmc.chatnotify.util.text.FormatUtil;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ARGB;

public final class ChatUtil {

	private ChatUtil() {}

	private static final Minecraft MINECRAFT = Minecraft.getInstance();

	/**
	 * @param name a player's username
	 * @return hue in the range {@code 0.0f <= hue <= 1.0f}
	 */
	public static float nameToHue(String name) {
		int hash = name.hashCode();
		return (float) Math.floorMod(hash, 256) / 255f;
	}

	/**
	 * @param uuid A player's UUID
	 * @return hue in the range {@code 0.0f <= hue <= 1.0f}.
	 * Uses the same implementation as minecraft waypoints
	 */
	public static float UUIDToHue(UUID uuid) {
		int colorInt = ARGB.setBrightness(ARGB.color(255, uuid.hashCode()), 0.9f);

		int r = (colorInt >> 16) & 0xFF;
		int g = (colorInt >> 8) & 0xFF;
		int b = colorInt & 0xFF;

		float[] hsv = Color.RGBtoHSB(r, g, b, null);
		return hsv[0];
	}

	public static Component applyPlayerColor(Component component) {
		MutableComponent mutableComponent = component.copy();
		mutableComponent = FormatUtil.convertToStyledLiteral(mutableComponent);

		String playerName = extractPlayerName(mutableComponent);

		if (playerName == null) {
			return applyColorToEmpty(mutableComponent, null, false);
		}

		LocalPlayer player = MINECRAFT.player;
		if (!ChatColors.CONFIG.colorSelf && player != null && playerName.equals(player.nameAndId().name()))
			return component;

		UUID playerUUID = null;
		ClientLevel level = MINECRAFT.level;
		if (level != null) {
			for (AbstractClientPlayer cPlayer : level.players()) {
				NameAndId nameAndId = cPlayer.nameAndId();
				if (nameAndId.name().equals(playerName)) {
					playerUUID = nameAndId.id();
					break;
				}
			}
		}

		float hue;
		if (ChatColors.CONFIG.useLocatorBarColors && playerUUID != null) {
			hue = UUIDToHue(playerUUID);
		} else {
			hue = nameToHue(playerName);
		}
		TextColor color = TextColor.fromRgb(Color.getHSBColor(hue, 0.60f, 1.0f).getRGB());

		return reconstructWithColor(mutableComponent, color, playerName);
	}

	/**
	 * Attempts to extract the sending player's name from the component tree using
	 * multiple strategies in order of reliability.
	 */
	private static @Nullable String extractPlayerName(Component component) {
		// insertion is set by Minecraft directly on the player name component
		String fromInsertion = findByInsertion(component);
		if (fromInsertion != null) return fromInsertion;

		// click event command usually ends with the player name
		String fromClickEvent = findByClickEvent(component);
		if (fromClickEvent != null) return fromClickEvent;

		// ShowEntity hover carries the UUID which we can match against the player list
		String fromHoverEvent = findByShowEntity(component);
		if (fromHoverEvent != null) return fromHoverEvent;

		// Last resort: look for <name> bracket pattern in flat text
		return findByBrackets(component);
	}

	private static @Nullable String findByInsertion(Component component) {
		String insertion = component.getStyle().getInsertion();
		if (insertion != null && !insertion.isBlank() && isPlayerName(insertion)) {
			return insertion;
		}
		for (Component sibling : component.getSiblings()) {
			String found = findByInsertion(sibling);
			if (found != null) return found;
		}
		return null;
	}

	private static @Nullable String findByClickEvent(Component component) {
		ClickEvent click = component.getStyle().getClickEvent();
		String cmd = null;
		if (click instanceof ClickEvent.SuggestCommand(String command)) {
			cmd = command;
		} else if (click instanceof ClickEvent.RunCommand(String command)) {
			cmd = command;
		}
		if (cmd != null) {
			// Most player-targeting commands end with the player name
			// e.g. /tell Player, /msg Player, /viewprofile Player
			String lastToken = cmd.substring(cmd.lastIndexOf(' ') + 1).trim();
			if (isPlayerName(lastToken)) return lastToken;
		}
		for (Component sibling : component.getSiblings()) {
			String found = findByClickEvent(sibling);
			if (found != null) return found;
		}
		return null;
	}

	private static @Nullable String findByShowEntity(Component component) {
		HoverEvent hover = component.getStyle().getHoverEvent();
		if (hover instanceof HoverEvent.ShowEntity(HoverEvent.EntityTooltipInfo info)) {
			// Try display name first
			if (info.name.isPresent()) {
				String name = info.name.get().getString();
				if (isPlayerName(name)) return name;
			}
			// Fall back to UUID lookup against the player list
			ClientLevel level = MINECRAFT.level;
			if (level != null) {
				for (AbstractClientPlayer player : level.players()) {
					if (player.nameAndId().id().equals(info.uuid)) {
						return player.nameAndId().name();
					}
				}
			}
		}
		for (Component sibling : component.getSiblings()) {
			String found = findByShowEntity(sibling);
			if (found != null) return found;
		}
		return null;
	}

	private static @Nullable String findByBrackets(Component component) {
		List<Component> flat = component.toFlatList();
		for (int i = 0; i < flat.size() - 2; i++) {
			String current = flat.get(i).getString().trim();
			String next = flat.get(i + 1).getString().trim();
			if (current.equals("<") && !next.isEmpty()
					&& flat.get(i + 2).getString().trim().startsWith(">")) {
				return next;
			}
		}
		return null;
	}

	/**
	 * Returns true if the given string matches a currently online player's name.
	 */
	private static boolean isPlayerName(String name) {
		ClientLevel level = MINECRAFT.level;
		if (level != null) {
			for (AbstractClientPlayer player : level.players()) {
				if (player.nameAndId().name().equals(name)) {
					return true;
				}
			}
		}
		return false;
	}

	private static MutableComponent reconstructWithColor(Component component, TextColor color, String playerName) {
		List<Component> flat = component.toFlatList();
		MutableComponent result = Component.empty().withStyle(component.getStyle());

		for (Component part : flat) {
			Style style = part.getStyle();
			String raw = part.getString();
			String trimmed = raw.trim();

			boolean isBracket = trimmed.equals("<") || trimmed.equals(">");
			boolean isName = !isBracket && (playerName.equals(style.getInsertion()) || trimmed.equals(playerName));
			boolean isTimestamp = Platform.INSTANCE.isModLoaded("chatpatches")
					&& style.getClickEvent() instanceof ClickEvent.SuggestCommand(String command)
					&& command.matches("\\d{2}/\\d{2}/\\d{4}");
			boolean shouldApply = shouldApplyColor(style);

			if (isTimestamp) {
				// Always preserve ChatPatches timestamp as-is
				result.append(part);
			} else if (isName && !ChatColors.CONFIG.colorPlayerNames) {
				// Respect colorPlayerNames config — skip coloring the name itself
				result.append(part);
			} else if (isBracket) {
				// Always color brackets
				result.append(Component.literal(raw).withStyle(style.withColor(color)));
			} else if (isName || shouldApply) {
				// Color the name and any uncolored (or override-all) parts
				result.append(part.copy().withStyle(style.withColor(color)));
			} else {
				// Leave intentionally colored parts alone
				result.append(part);
			}
		}

		return result;
	}

	/**
	 * Returns true if the player color should be applied to this component,
	 * i.e. it has no color, has the default white color, or overrideAllColor is enabled.
	 */
	private static boolean shouldApplyColor(Style style) {
		if (ChatColors.CONFIG.overrideAllColor) return true;
		TextColor color = style.getColor();
		if (color == null) return true;
		return color.getValue() == 0xFFFFFF;
	}

	/**
	 * Returns true if this component has a meaningful non-default color set.
	 * Used by {@link #applyColorToEmpty} to avoid overwriting intentional colors.
	 */
	private static boolean hasSignificantColor(Style style) {
		TextColor color = style.getColor();
		if (color == null) return false;
		return color.getValue() != 0xFFFFFF;
	}

	private static MutableComponent applyColorToEmpty(Component component, @Nullable TextColor color, boolean parentHasColor) {
		MutableComponent result = component.plainCopy();
		boolean thisHasColor = hasSignificantColor(component.getStyle());

		if (color != null && !thisHasColor && !parentHasColor) {
			result.withStyle(component.getStyle().withColor(color));
		} else {
			result.withStyle(component.getStyle());
		}

		for (Component sibling : component.getSiblings()) {
			result.append(applyColorToEmpty(sibling, color, parentHasColor || thisHasColor));
		}

		return result;
	}
}
