package fr.umontpellier.iut.discordbot.database.dataobjects;

public class GdprDeletionRequest extends AbstractDataObject {
	public static final String STATUS_PENDING = "PENDING";
	public static final String STATUS_COMPLETED = "COMPLETED";
	public static final String STATUS_CANCELLED = "CANCELLED";

	private final long id;
	private final String userId;
	private final long requestedAt;
	private final long executeAt;
	private final String status;
	private final String reviewedBy;
	private final String refusalReason;

	public GdprDeletionRequest(long id, String userId, long requestedAt, long executeAt, String status,
			String reviewedBy, String refusalReason) {
		this.id = id;
		this.userId = userId;
		this.requestedAt = requestedAt;
		this.executeAt = executeAt;
		this.status = status;
		this.reviewedBy = reviewedBy;
		this.refusalReason = refusalReason;
	}

	public long getId() {
		return this.id;
	}

	public String getUserId() {
		return this.userId;
	}

	public long getRequestedAt() {
		return this.requestedAt;
	}

	public long getExecuteAt() {
		return this.executeAt;
	}

	public String getStatus() {
		return this.status;
	}

	public String getReviewedBy() {
		return this.reviewedBy;
	}

	public String getRefusalReason() {
		return this.refusalReason;
	}
}