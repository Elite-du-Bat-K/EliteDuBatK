package fr.umontpellier.iut.discordbot.lib;

import java.time.ZonedDateTime;

import org.jetbrains.annotations.NotNull;

public class CachedMessage {
	@NotNull
	private final String senderId, content;
	@NotNull
	private final ZonedDateTime receptionTime;

	public CachedMessage(String senderId, String messageContent, ZonedDateTime reception) {
		this.senderId = senderId;
		content = messageContent;
		receptionTime = reception;
	}

	@NotNull
	public String getSenderId() {
		return senderId;
	}

	@NotNull
	public String getContent() {
		return content;
	}

	@NotNull
	public ZonedDateTime getReceptionTime() {
		return receptionTime;
	}
}
