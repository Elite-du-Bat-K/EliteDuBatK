package fr.umontpellier.iut.discordbot.config;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class ConfigStructure {
	public enum SystemChannel {
		@SerializedName("voice_channel")
		VOICE_CHANNEL("voice_channel"),

		@SerializedName("message_delete_channel")
		MESSAGE_DELETE_CHANNEL("message_delete_channel"),

		@SerializedName("message_edit_channel")
		MESSAGE_EDIT_CHANNEL("message_edit_channel"),

		@SerializedName("lock_channel")
		LOCK_CHANNEL("lock_channel"),

		@SerializedName("channel_log_channel")
		CHANNEL_LOG_CHANNEL("channel_log_channel"),

		@SerializedName("role_log_channel")
		ROLE_LOG_CHANNEL("role_log_channel"),

		@SerializedName("member_log_channel")
		MEMBER_LOG_CHANNEL("member_log_channel"),

		@SerializedName("moderation_channel")
		MODERATION_CHANNEL("moderation_channel"),

		@SerializedName("honeypot_channel")
		HONEYPOT_CHANNEL("honeypot_channel");

		private final String chanType;

		SystemChannel(String chanType) {
			this.chanType = chanType;
		}

		public String toString() {
			return this.chanType;
		}

		@Nullable
		public static SystemChannel fromString(String str) {
			SystemChannel result = null;
			boolean found = false;
			SystemChannel[] values = SystemChannel.values();
			int i = 0;
			int length = values.length;

			while (!found && i < length) {
				boolean matches = values[i].chanType.equals(str);
				if (matches) {
					result = values[i];
					found = true;
				}
				i++;
			}

			return result;
		}
	}

	public static class StudySuiteConfig {
		private String baseUrl;
		private String apiKey;

		public StudySuiteConfig() {
		}

		public StudySuiteConfig(String baseUrl, String apiKey) {
			this.baseUrl = baseUrl;
			this.apiKey = apiKey;
		}

		@Nullable
		public String getBaseUrl() {
			return baseUrl;
		}

		@Nullable
		public String getApiKey() {
			return apiKey;
		}
	}

	/** Un service dont {@code /status} vérifie l'accès. Sans {@code pingUrl}, c'est {@code publicUrl} qui est testée. */
	public static class StatusTarget {
		private String displayName;
		private String publicUrl;
		private String pingUrl;

		public StatusTarget() {
		}

		public StatusTarget(String displayName, String publicUrl, @Nullable String pingUrl) {
			this.displayName = displayName;
			this.publicUrl = publicUrl;
			this.pingUrl = pingUrl;
		}

		public String getDisplayName() {
			return displayName;
		}

		public String getPublicUrl() {
			return publicUrl;
		}

		public String getCheckedUrl() {
			String result = publicUrl;
			boolean hasPingUrl = pingUrl != null && !pingUrl.isBlank();

			if (hasPingUrl) {
				result = pingUrl;
			}

			return result;
		}
	}

	private static final int DEFAULT_STATUS_INTERVAL_SECONDS = 60;
	private static final int MIN_STATUS_INTERVAL_SECONDS = 30;

	private String token;
	private String databasePath;
	private List<StatusTarget> statusTargets;
	private Integer statusIntervalSeconds;

	@SerializedName(value = "studySuite", alternate = { "studysuite" })
	private StudySuiteConfig studySuite;

	@SerializedName("allowed_guild_id")
	private String allowedGuildId;

	private String adminRole;
	private Map<String, List<String>> groups;
	private Map<String, String> channels;

	public String getToken() {
		return token;
	}

	public String getJDBCUrl() {
		return "jdbc:sqlite:" + databasePath;
	}

	public List<String> getRolesIdForGroup(String group) {
		List<String> result = List.of();
		boolean hasGroups = groups != null;

		if (hasGroups) {
			List<String> rolesId = groups.get(group);
			boolean hasRoles = rolesId != null;
			if (hasRoles) {
				result = rolesId;
			}
		}

		return result;
	}

	public List<String> getRoles() {
		List<String> result = List.of();
		boolean hasGroups = groups != null;

		if (hasGroups) {
			result = groups.keySet().stream().toList();
		}

		return result;
	}

	public List<StatusTarget> getStatusTargets() {
		List<StatusTarget> result = List.of();
		boolean hasTargets = statusTargets != null;

		if (hasTargets) {
			result = statusTargets;
		}

		return result;
	}

	/**
	 * {@value #DEFAULT_STATUS_INTERVAL_SECONDS} s par défaut, et jamais moins de {@value #MIN_STATUS_INTERVAL_SECONDS} s :
	 * une vérification qui échoue peut durer une trentaine de secondes avec ses essais.
	 */
	public Duration getStatusInterval() {
		long seconds = DEFAULT_STATUS_INTERVAL_SECONDS;
		boolean isConfigured = statusIntervalSeconds != null;

		if (isConfigured) {
			seconds = Math.max(statusIntervalSeconds, MIN_STATUS_INTERVAL_SECONDS);
		}

		return Duration.ofSeconds(seconds);
	}

	@Nullable
	public StudySuiteConfig getStudySuite() {
		return studySuite;
	}

	public String getAdminRole() {
		return adminRole;
	}

	public Map<String, String> getChannels() {
		return channels;
	}

	@Nullable
	public String getChannelId(SystemChannel channel) {
		String result = null;
		boolean hasChannels = channels != null;

		if (hasChannels) {
			String channelId = channels.get(channel.toString());
			boolean foundByToString = channelId != null;

			if (foundByToString) {
				result = channelId;
			}

			if (!foundByToString) {
				result = channels.get(channel.name());
			}
		}

		return result;
	}

	@Nullable
	public String getVoiceChannelId() {
		return getChannelId(SystemChannel.VOICE_CHANNEL);
	}

	@Nullable
	public String getMessageDeleteChannelId() {
		return getChannelId(SystemChannel.MESSAGE_DELETE_CHANNEL);
	}

	@Nullable
	public String getLockChannelId() {
		return getChannelId(SystemChannel.LOCK_CHANNEL);
	}

	public String getAllowedGuildId() {
		return allowedGuildId;
	}
}