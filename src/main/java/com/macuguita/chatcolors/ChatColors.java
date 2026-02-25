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

import dev.terminalmc.chatnotify.util.text.FormatUtil;
import folk.sisby.kaleido.api.WrappedConfig;
import folk.sisby.kaleido.lib.quiltconfig.api.annotations.Comment;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.contextualbar.LocatorBarRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import net.minecraft.server.players.NameAndId;

import net.minecraft.util.ARGB;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	}

	public static void initClient() {
		LOGGER.info("Initializing {} on {}", MOD_ID, Platform.INSTANCE.loader());
	}

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
		// Step 1: generate ARGB color from UUID hash and set brightness
		int colorInt = ARGB.setBrightness(ARGB.color(255, uuid.hashCode()), 0.9f);

		// Step 2: extract RGB components
		int r = (colorInt >> 16) & 0xFF;
		int g = (colorInt >> 8) & 0xFF;
		int b = colorInt & 0xFF;

		// Step 3: convert RGB to HSV and return hue (0-1)
		float[] hsv = Color.RGBtoHSB(r, g, b, null);
		return hsv[0];
	}

	public static Component applyPlayerColor(Component component) {
		MutableComponent mutableComponent = MutableComponent.create(component.getContents());
		mutableComponent = FormatUtil.convertToStyledLiteral(mutableComponent);
		List<Component> flat = mutableComponent.toFlatList();

		String playerName = null;
		for (int i = 0; i < flat.size() - 1; i++) {
			String current = flat.get(i).getString().trim();
			String next = flat.get(i + 1).getString().trim();
			if (current.equals("<") && !next.isEmpty() && flat.size() > i + 2 && flat.get(i + 2).getString().trim().startsWith(">")) {
				playerName = next;
				break;
			}
		}

		if (playerName == null) {
			return applyColorToEmpty(mutableComponent, null, false);
		}
		UUID playerUUID = null;
		for (AbstractClientPlayer player : Minecraft.getInstance().level.players()) {
			NameAndId nameAndId = player.nameAndId();
			if (nameAndId.name().equals(playerName)) {
				playerUUID = nameAndId.id();
				break;
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

	private static MutableComponent reconstructWithColor(Component component, TextColor color, String playerName) {
		String full = component.getString();

		if (!full.contains("<" + playerName + ">")) {
			return applyColorToEmpty(component, color, false);
		}

		List<Component> flat = component.toFlatList();
		MutableComponent result = Component.empty().withStyle(component.getStyle());

		for (Component part : flat) {
			String text = part.getString().trim();
			Style style = part.getStyle();
			boolean hasColor = style.getColor() != null;

			boolean isName = text.equals(playerName);

			if (isName && !ChatColors.CONFIG.colorPlayerNames) {
				result.append(Component.literal(part.getString()).withStyle(style));
			} else if (!hasColor) {
				result.append(Component.literal(part.getString()).withStyle(style.withColor(color)));
			} else {
				result.append(Component.literal(part.getString()).withStyle(style));
			}
		}

		return result;
	}

	private static MutableComponent applyColorToEmpty(Component component, @Nullable TextColor color, boolean parentHasColor) {
		MutableComponent result = component.plainCopy();
		boolean thisHasColor = component.getStyle().getColor() != null;

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
