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

import net.minecraft.network.chat.ClickEvent;

import net.minecraft.network.chat.HoverEvent;

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

		@Comment("Override all color.")
		@Comment("This is the equivalent of a nuclear option when it comes to coloring names,")
		@Comment("this should be used when the server changes your chat color to something that is")
		@Comment("not the default one, e.g. Hypixel making your chat gray")
		public boolean overrideAllColor = false;
	}

	public static void initClient() {
		LOGGER.info("Initializing {} on {}", MOD_ID, Platform.INSTANCE.loader());
	}
}
