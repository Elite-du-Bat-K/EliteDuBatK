package fr.umontpellier.iut.discordbot.database.dataobjects;

public class PreservedMessage extends AbstractDataObject {
	private final long messageId;
	private final long channelId;
	private final long authorId;
	private final String content;
	private final long timestamp;
	private final String preservedBy;
	private final long preservedAt;
	private final String reason;

	public PreservedMessage(long messageId, long channelId, long authorId, String content, long timestamp,
			String preservedBy, long preservedAt, String reason) {
		this.messageId = messageId;
		this.channelId = channelId;
		this.authorId = authorId;
		this.content = content != null ? content : "";
		this.timestamp = timestamp;
		this.preservedBy = preservedBy;
		this.preservedAt = preservedAt;
		this.reason = reason != null ? reason : "";
	}

	public long getMessageId() {
		return this.messageId;
	}

	public long getChannelId() {
		return this.channelId;
	}

	public long getAuthorId() {
		return this.authorId;
	}

	public String getContent() {
		return this.content;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public String getPreservedBy() {
		return this.preservedBy;
	}

	public long getPreservedAt() {
		return this.preservedAt;
	}

	public String getReason() {
		return this.reason;
	}
}