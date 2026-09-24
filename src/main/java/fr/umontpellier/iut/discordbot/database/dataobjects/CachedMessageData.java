package fr.umontpellier.iut.discordbot.database.dataobjects;

public class CachedMessageData extends AbstractDataObject {
	private final long messageId;
	private final long channelId;
	private final long authorId;
	private final String content;
	private final long timestamp;

	public CachedMessageData(long messageId, long channelId, long authorId, String content, long timestamp) {
		this.messageId = messageId;
		this.channelId = channelId;
		this.authorId = authorId;
		this.content = content != null ? content : "";
		this.timestamp = timestamp;
	}

	public long getMessageId() {
		return messageId;
	}

	public long getChannelId() {
		return channelId;
	}

	public long getAuthorId() {
		return authorId;
	}

	public String getContent() {
		return content;
	}

	public long getTimestamp() {
		return timestamp;
	}
}