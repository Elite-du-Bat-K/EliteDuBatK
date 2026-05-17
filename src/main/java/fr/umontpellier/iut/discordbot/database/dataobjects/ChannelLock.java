package fr.umontpellier.iut.discordbot.database.dataobjects;

/**
 * Represents a locked channel state stored in the database.
 * Contains the channel ID, who locked it, when it was locked, and the serialized channel permissions/state.
 */
public class ChannelLock extends AbstractDataObject {
    private final long id;
    private final String channelId;
    private final String guildId;
    private final String lockedById;
    private final long lockedAt;
    private final String channelStateJson;

    public ChannelLock(long id, String channelId, String guildId, String lockedById, long lockedAt, String channelStateJson) {
        this.id = id;
        this.channelId = channelId;
        this.guildId = guildId;
        this.lockedById = lockedById;
        this.lockedAt = lockedAt;
        this.channelStateJson = channelStateJson;
    }

    public long getId() {
        return id;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getLockedById() {
        return lockedById;
    }

    public long getLockedAt() {
        return lockedAt;
    }

    public String getChannelStateJson() {
        return channelStateJson;
    }
}


