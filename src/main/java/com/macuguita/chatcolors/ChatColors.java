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
import folk.sisby.kaleido.api.WrappedConfig;
import folk.sisby.kaleido.lib.quiltconfig.api.annotations.Comment;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ARGB;

public class ChatColors {

	public static final String MOD_ID = "chatcolors";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Config CONFIG = WrappedConfig.createToml(Platform.INSTANCE.getConfigDir(), "", MOD_ID, Config.class);

	public static class Config extends WrappedConfig {
		@Comment("Whether all of the mods features should be enabled or not.")
		public boolean enableMod = true;

		@Comment("Whether player names should be recolored of not.")
		public boolean colorPlayerNames = true;

		@Comment("Whether the chat colors should use the same colors as the locator bar.")
		public boolean useLocatorBarColors = true;

		@Comment("Whether the chat colors should apply to your own messages.")
		public boolean colorSelf = true;
	}

	public static void initClient() {
		LOGGER.info("Initializing {} on {}", MOD_ID, Platform.INSTANCE.loader());
	}

	private static final Minecraft MINECRAFT = Minecraft.getInstance();

	/**
	 * @param name
	 * @return hue in the range {@code 0.0f <= hue <= 1.0f}
	 */
	public static float nameToHue(String name) {
		int hash = name.hashCode();
		return (float) Math.floorMod(hash, 256) / 255f;
	}

	/**
	 * @param uuid
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
		if (!CONFIG.colorSelf && player != null && playerName.equals(player.nameAndId().name()))
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
		if (CONFIG.useLocatorBarColors && playerUUID != null) {
			hue = UUIDToHue(playerUUID);
		} else {
			hue = nameToHue(playerName);
		}
		TextColor color = TextColor.fromRgb(Color.getHSBColor(hue, 0.60f, 1.0f).getRGB());

		return reconstructWithColor(mutableComponent, color, playerName);
	}

	private static @Nullable String extractPlayerName(Component component) {
		String fromInsertion = findByInsertion(component);
		if (fromInsertion != null) return fromInsertion;

		List<Component> flat = component.toFlatList();
		for (int i = 0; i < flat.size() - 1; i++) {
			String current = flat.get(i).getString().trim();
			String next = flat.get(i + 1).getString().trim();
			if (current.equals("<") && !next.isEmpty()
					&& flat.size() > i + 2
					&& flat.get(i + 2).getString().trim().startsWith(">")) {
				return next;
			}
		}
		return null;
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

	private static boolean isPlayerName(String insertion) {
		ClientLevel level = MINECRAFT.level;
		if (level != null) {
			for (AbstractClientPlayer player : level.players()) {
				if (player.nameAndId().name().equals(insertion)) {
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
			boolean hasColor = hasSignificantColor(style);
			String raw = part.getString();
			String trimmed = raw.trim();
			boolean isBracket = trimmed.equals("<") || trimmed.equals(">");
			boolean isName = !isBracket && (playerName.equals(style.getInsertion()) || trimmed.equals(playerName));

			if (isName && !CONFIG.colorPlayerNames) {
				result.append(part);
			} else if (isBracket) {
				result.append(Component.literal(raw).withStyle(style.withColor(color)));
			} else if (isName) {
				result.append(part.copy().withStyle(style.withColor(color)));
			} else if (!hasColor) {
				result.append(Component.literal(trimmed).withStyle(style.withColor(color)));
			} else {
				result.append(part);
			}
		}

		return result;
	}

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
