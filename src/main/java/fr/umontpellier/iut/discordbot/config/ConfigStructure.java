package fr.umontpellier.iut.discordbot.config;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class ConfigStructure {
    public enum LogChannel {
        @SerializedName(value = "voice_channel")
        VOICE_CHANNEL("voice_channel"),


        @SerializedName(value = "message_delete_channel")
        MESSAGE_DELETE_CHANNEL("message_delete_channel");

        private final String chanType;

        LogChannel(String chanType) {
            this.chanType = chanType;
        }

        public String toString() {
            return this.chanType;
        }

        @Nullable
        public static LogChannel fromString(String str) {
            return switch (str) {
                case "voice_channel" -> VOICE_CHANNEL;
                case "message_delete_channel" -> MESSAGE_DELETE_CHANNEL;
                default -> null;
            };
        }
    }

    private String token;
    private Map<String, List<String>> groups;
    private Map<String, String> channels;

    public String getToken() {
        return token;
    }

    public List<String> getRolesIdForGroup(String group) {
        List<String> rolesId = groups.get(group);

        return rolesId == null ? List.of() : rolesId;
    }

    public List<String> getRoles() {
        return groups.keySet().stream().toList();
    }

    public Map<String, String> getChannels() {
        return channels;
    }

    @Nullable
    public String getChannelId(LogChannel channel) {
        if (channels == null) {
            return null;
        }

        String channelId = channels.get(channel.toString());
        if (channelId != null) {
            return channelId;
        }

        return channels.get(channel.name());
    }

    @Nullable
    public String getVoiceChannelId() {
        return getChannelId(LogChannel.VOICE_CHANNEL);
    }

    @Nullable
    public String getMessageDeleteChannelId() {
        return getChannelId(LogChannel.MESSAGE_DELETE_CHANNEL);
    }
}
