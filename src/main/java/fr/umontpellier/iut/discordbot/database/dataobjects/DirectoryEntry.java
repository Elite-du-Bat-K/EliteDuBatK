package fr.umontpellier.iut.discordbot.database.dataobjects;

public class DirectoryEntry extends AbstractDataObject {
	private long id;
	private String lastName;
	private String firstName;
	private String email;
	private String category;
	private String description;
	private Long discordUserId;

	public DirectoryEntry() {
		this.id = 0L;
		this.lastName = "";
		this.firstName = null;
		this.email = "";
		this.category = "UNKNOWN";
		this.description = null;
		this.discordUserId = null;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Long getDiscordUserId() {
		return discordUserId;
	}

	public void setDiscordUserId(Long discordUserId) {
		this.discordUserId = discordUserId;
	}
}