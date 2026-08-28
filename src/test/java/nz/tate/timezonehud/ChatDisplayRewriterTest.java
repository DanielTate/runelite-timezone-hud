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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;
import net.runelite.api.Node;
import net.runelite.api.events.ChatMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * The rule this plugin is most at risk of breaking is "no modifying outgoing chat messages
 * after the user sends them". These tests hold the line: the message object that carries
 * what was sent must come out of the rewrite exactly as it went in, with only the received
 * message's displayed value changed.
 */
public class ChatDisplayRewriterTest
{
	private static final Clock FIXED =
		Clock.fixed(Instant.parse("2026-08-28T22:57:00Z"), ZoneId.of("Pacific/Auckland"));

	private static final String TYPED = "!TZ EST 20:30 > NZST";
	private static final String ANSWER = "20:30 EDT (Fri 28 Aug) = 12:30 NZST (Sat 29 Aug)";

	private final CommandResponder responder = new CommandResponder(FIXED);

	private RecordingMessageNode node;
	private ChatMessage chatMessage;

	@Before
	public void setUp()
	{
		node = new RecordingMessageNode(TYPED);
		chatMessage = new ChatMessage(node, ChatMessageType.PUBLICCHAT, "Dan", TYPED, null, 1234);
	}

	@Test
	public void onlyTheDisplayedValueChanges()
	{
		assertTrue(ChatDisplayRewriter.rewrite(chatMessage, responder.respondToMessage(TYPED)));

		assertEquals(ANSWER, node.getValue());
		assertEquals("nothing but setValue may be called on the node", 0, node.getOtherWrites());
	}

	@Test
	public void whatTheUserSentIsNeverTouched()
	{
		ChatDisplayRewriter.rewrite(chatMessage, responder.respondToMessage(TYPED));

		// This is the message as it left the client. Others see exactly this.
		assertEquals(TYPED, chatMessage.getMessage());
		assertEquals("Dan", chatMessage.getName());
		assertEquals(ChatMessageType.PUBLICCHAT, chatMessage.getType());
		assertEquals(1234, chatMessage.getTimestamp());
	}

	@Test
	public void theAnswerIsTheSameOneTheLocalCommandGives()
	{
		// D8: the sender only ever sees the rewritten line, so a divergence between the
		// two paths would be invisible to them.
		ChatDisplayRewriter.rewrite(chatMessage, responder.respondToMessage(TYPED));

		assertEquals(responder.respondToArguments(new String[]{"EST", "20:30", ">", "NZST"}).getText(),
			node.getValue());
	}

	@Test
	public void anAmbiguousCommandLeavesTheLineAlone()
	{
		String typed = "!TZ IST 20:30 > NZST";
		RecordingMessageNode ambiguous = new RecordingMessageNode(typed);
		ChatMessage message = new ChatMessage(ambiguous, ChatMessageType.PUBLICCHAT, "Dan", typed, null, 0);

		assertFalse(ChatDisplayRewriter.rewrite(message, responder.respondToMessage(typed)));

		assertEquals(typed, ambiguous.getValue());
		assertEquals(0, ambiguous.getOtherWrites());
	}

	@Test
	public void aMalformedCommandLeavesTheLineAlone()
	{
		String typed = "!TZ";
		RecordingMessageNode malformed = new RecordingMessageNode(typed);
		ChatMessage message = new ChatMessage(malformed, ChatMessageType.PUBLICCHAT, "Dan", typed, null, 0);

		assertFalse(ChatDisplayRewriter.rewrite(message, responder.respondToMessage(typed)));

		assertEquals(typed, malformed.getValue());
	}

	/**
	 * A message node that remembers what was done to it. Anything other than setValue is
	 * counted as a write we do not want to be making.
	 */
	private static class RecordingMessageNode implements MessageNode
	{
		private String value;
		private String runeLiteFormatMessage;
		private String name = "Dan";
		private String sender;
		private int timestamp;
		private int otherWrites;

		RecordingMessageNode(String value)
		{
			this.value = value;
		}

		int getOtherWrites()
		{
			return otherWrites;
		}

		@Override
		public String getValue()
		{
			return value;
		}

		@Override
		public void setValue(String value)
		{
			this.value = value;
		}

		@Override
		public String getRuneLiteFormatMessage()
		{
			return runeLiteFormatMessage;
		}

		@Override
		public void setRuneLiteFormatMessage(String runeLiteFormatMessage)
		{
			this.runeLiteFormatMessage = runeLiteFormatMessage;
			otherWrites++;
		}

		@Override
		public String getName()
		{
			return name;
		}

		@Override
		public void setName(String name)
		{
			this.name = name;
			otherWrites++;
		}

		@Override
		public String getSender()
		{
			return sender;
		}

		@Override
		public void setSender(String sender)
		{
			this.sender = sender;
			otherWrites++;
		}

		@Override
		public int getTimestamp()
		{
			return timestamp;
		}

		@Override
		public void setTimestamp(int timestamp)
		{
			this.timestamp = timestamp;
			otherWrites++;
		}

		@Override
		public int getId()
		{
			return 0;
		}

		@Override
		public ChatMessageType getType()
		{
			return ChatMessageType.PUBLICCHAT;
		}

		@Override
		public Node getNext()
		{
			return null;
		}

		@Override
		public Node getPrevious()
		{
			return null;
		}

		@Override
		public long getHash()
		{
			return 0;
		}
	}
}
