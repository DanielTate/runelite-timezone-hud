/*
 * Copyright (c) 2026, dan <dan@tate.nz>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package nz.tate.timezonehud;

import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;

/**
 * Replaces the text of a chat message that has <em>already arrived</em>, so that a
 * {@code !TZ} line reads as its answer in this client.
 *
 * <h2>Why it is done this way, and why it must stay this way</h2>
 *
 * Jagex's third-party client guidelines, which RuneLite enforces, forbid two things that a
 * "!TZ converter" could very easily be built out of:
 *
 * <ul>
 * <li><b>No modifying outgoing chat messages after the user sends them.</b></li>
 * <li><b>No autotyping</b> -- a plugin must never put text into the chatbox input.</li>
 * </ul>
 *
 * <p>This class does neither. By the time it runs, the message has already been sent to
 * Jagex's servers, exactly as the user typed it, and has come back as an ordinary received
 * chat message. What is edited is the {@link MessageNode} of that received message -- the
 * client-side record of what to draw. The bytes on the wire were never touched, other
 * players see the raw {@code !TZ} line, and only people running this plugin see the answer.
 * This is the same mechanism core RuneLite's {@code !kc} and {@code !pb} use.
 *
 * <p><b>Do not "simplify" this into the ChatInput path.</b> {@code ChatCommandManager} also
 * accepts a {@code BiPredicate<ChatInput, String>} that runs <em>before</em> the message is
 * sent and can call {@code chatboxInput.consume()} to swallow or alter it. That would be
 * shorter and it would be modifying an outgoing message. D4 rejects it explicitly. Equally,
 * never write the answer into the chatbox input: that is autotyping, whatever the intent.
 */
final class ChatDisplayRewriter
{
	private ChatDisplayRewriter()
	{
	}

	/**
	 * Swaps the displayed text of an already-received message for the response.
	 *
	 * <p>Only a successful conversion is worth replacing a public line with. If the command
	 * was malformed the message is left exactly as everyone else sees it, because silently
	 * turning someone's public message into an error message in their own client is worse
	 * than saying nothing.
	 *
	 * @return true if the display was changed, so the caller knows to refresh the chat
	 */
	static boolean rewrite(ChatMessage chatMessage, CommandResponse response)
	{
		if (!response.isConversion())
		{
			return false;
		}

		MessageNode node = chatMessage.getMessageNode();
		if (node == null)
		{
			return false;
		}

		node.setValue(response.getText());
		return true;
	}
}
