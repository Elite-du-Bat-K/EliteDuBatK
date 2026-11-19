package fr.umontpellier.iut.discordbot.database.dataobjects;

public enum DirectoryCategory {
	PROF("Professeur", "🎓"),
	SERVICE("Service", "🏢");

	private final String displayName;
	private final String icon;

	DirectoryCategory(String displayName, String icon) {
		this.displayName = displayName;
		this.icon = icon;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public String getIcon() {
		return this.icon;
	}

	public static DirectoryCategory fromString(String value) {
		DirectoryCategory resolved = PROF;
		boolean hasValue = (value != null);

		if (hasValue) {
			for (DirectoryCategory category : values()) {
				boolean matchesName = category.name().equalsIgnoreCase(value);
				boolean matchesDisplay = category.displayName.equalsIgnoreCase(value);

				if (matchesName || matchesDisplay) {
					resolved = category;
				}
			}
		}

		return resolved;
	}
}