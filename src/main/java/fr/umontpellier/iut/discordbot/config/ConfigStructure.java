package fr.umontpellier.iut.discordbot.config;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.List;

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
    private Map<LogChannel, String> channels;
    private Map<String, List<String>> groups;

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

    @Nullable
    public String getChannelId(LogChannel channel) {
        return channels == null ? null : channels.get(channel);
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
