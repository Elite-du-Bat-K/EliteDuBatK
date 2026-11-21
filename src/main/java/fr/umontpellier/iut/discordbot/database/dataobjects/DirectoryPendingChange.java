package fr.umontpellier.iut.discordbot.database.dataobjects;

public class DirectoryPendingChange extends AbstractDataObject {
	public static final String OP_ADD = "ADD";
	public static final String OP_EDIT = "EDIT";
	public static final String OP_DELETE = "DELETE";

	private final long id;
	private final String operation;
	private final Long targetEntryId;
	private final String lastName;
	private final String firstName;
	private final String email;
	private final String category;
	private final String description;
	private final Long discordUserId;
	private final String requesterId;
	private final long requestedAt;

	public DirectoryPendingChange(long id, String operation, Long targetEntryId, String lastName, String firstName,
			String email, String category, String description, Long discordUserId, String requesterId,
			long requestedAt) {
		this.id = id;
		this.operation = operation;
		this.targetEntryId = targetEntryId;
		this.lastName = lastName;
		this.firstName = firstName;
		this.email = email;
		this.category = category;
		this.description = description;
		this.discordUserId = discordUserId;
		this.requesterId = requesterId;
		this.requestedAt = requestedAt;
	}

	public long getId() {
		return this.id;
	}

	public String getOperation() {
		return this.operation;
	}

	public Long getTargetEntryId() {
		return this.targetEntryId;
	}

	public String getLastName() {
		return this.lastName;
	}

	public String getFirstName() {
		return this.firstName;
	}

	public String getEmail() {
		return this.email;
	}

	public String getCategory() {
		return this.category;
	}

	public String getDescription() {
		return this.description;
	}

	public Long getDiscordUserId() {
		return this.discordUserId;
	}

	public String getRequesterId() {
		return this.requesterId;
	}

	public long getRequestedAt() {
		return this.requestedAt;
	}
}