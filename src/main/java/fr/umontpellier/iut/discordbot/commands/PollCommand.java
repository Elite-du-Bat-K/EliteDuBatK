package fr.umontpellier.iut.discordbot.commands;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.database.repositories.PollChoiceRepository;
import fr.umontpellier.iut.discordbot.database.repositories.PollCommentRepository;
import fr.umontpellier.iut.discordbot.database.repositories.PollRepository;
import fr.umontpellier.iut.discordbot.database.repositories.PollRoleRepository;
import fr.umontpellier.iut.discordbot.database.repositories.PollVoterRepository;
import fr.umontpellier.iut.discordbot.lib.AbstractCommandWithAutocomplete;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PollCommand extends AbstractCommandWithAutocomplete {

	public PollCommand(Bot bot) {
		super(bot);
	}

	@NotNull
	@Override
	public SlashCommandData getCommandInformation() {
		SubcommandData createData = new SubcommandData("create", "Create a new poll")
				.addOption(OptionType.STRING, "title", "Poll title", true)
				.addOption(OptionType.STRING, "description", "Poll description", true)
				.addOption(OptionType.STRING, "choices", "Choices separated by | (e.g. Yes|No)", true)
				.addOption(OptionType.INTEGER, "duration_minutes", "Poll duration in minutes (optional)", false);

		SubcommandData closeData = new SubcommandData("close", "Close a poll")
				.addOption(OptionType.INTEGER, "id", "Poll ID", true, true);

		SubcommandData voteData = new SubcommandData("vote", "Vote on a poll")
				.addOption(OptionType.INTEGER, "poll_id", "Poll ID", true, true)
				.addOption(OptionType.INTEGER, "choice_index", "Select your choice", true, true);

		SubcommandData resultsData = new SubcommandData("results", "View poll results")
				.addOption(OptionType.INTEGER, "id", "Poll ID", true, true)
				.addOption(OptionType.BOOLEAN, "public", "Make the results visible to everyone", false);

		SubcommandData exportResultsData = new SubcommandData("export", "Export poll results to TXT")
				.addOption(OptionType.INTEGER, "id", "Poll ID", true, true);

		SubcommandData commentData = new SubcommandData("comment", "Add a comment")
				.addOption(OptionType.INTEGER, "id", "Poll ID", true, true)
				.addOption(OptionType.STRING, "text", "Comment text", true)
				.addOption(OptionType.BOOLEAN, "anonymous", "Hide your identity", true);

		SubcommandData exportCommentsData = new SubcommandData("export_comments", "Export all comments to TSV")
				.addOption(OptionType.INTEGER, "id", "Poll ID", true, true);

		SubcommandData deleteData = new SubcommandData("delete", "Delete all database entries for a poll")
				.addOption(OptionType.INTEGER, "id", "Poll ID", true, true);

		SubcommandData viewData = new SubcommandData("info", "View poll information and metadata")
				.addOption(OptionType.INTEGER, "id", "Poll ID", true, true);

		SubcommandGroupData restrictionGroup = new SubcommandGroupData("restriction", "Manage poll vote restrictions")
				.addSubcommands(
						new SubcommandData("mode", "Set restriction mode")
								.addOption(OptionType.INTEGER, "id", "Poll ID", true, true)
								.addOption(OptionType.STRING, "type", "Mode: NONE, WHITELIST, BLACKLIST", true)
								.addOption(OptionType.BOOLEAN, "force", "Force change (removes existing roles)", false),
						new SubcommandData("add_role", "Add role to restriction list")
								.addOption(OptionType.INTEGER, "id", "Poll ID", true, true)
								.addOption(OptionType.ROLE, "role", "Target role", true),
						new SubcommandData("remove_role", "Remove role from restriction list")
								.addOption(OptionType.INTEGER, "id", "Poll ID", true, true)
								.addOption(OptionType.ROLE, "role", "Target role", true));

		return Commands.slash("poll", "Manage polls")
				.addSubcommands(createData, closeData, voteData, resultsData, exportResultsData, commentData,
						exportCommentsData, deleteData, viewData)
				.addSubcommandGroups(restrictionGroup)
				.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		String subName = event.getSubcommandName();
		String groupName = event.getSubcommandGroup();
		boolean hasSubName = subName != null;
		boolean hasGroupName = groupName != null;

		if (hasGroupName) {
			boolean isRestrictionGroup = groupName.equals("restriction");
			if (isRestrictionGroup) {
				boolean isMode = subName.equals("mode");
				boolean isAddRole = subName.equals("add_role");
				boolean isRemoveRole = subName.equals("remove_role");
				if (isMode) {
					this.handleRestrictionMode(event);
				} else if (isAddRole) {
					this.handleRestrictionAddRole(event);
				} else if (isRemoveRole) {
					this.handleRestrictionRemoveRole(event);
				} else {
					event.reply("Unknown restriction subcommand.").setEphemeral(true).queue();
				}
			} else {
				event.reply("Unknown subcommand group.").setEphemeral(true).queue();
			}
		} else if (hasSubName) {
			boolean isCreate = subName.equals("create");
			boolean isClose = subName.equals("close");
			boolean isVote = subName.equals("vote");
			boolean isResults = subName.equals("results");
			boolean isExport = subName.equals("export");
			boolean isComment = subName.equals("comment");
			boolean isExportComments = subName.equals("export_comments");
			boolean isDelete = subName.equals("delete");
			boolean isView = subName.equals("info");

			if (isCreate) {
				this.handleCreate(event);
			} else if (isClose) {
				this.handleClose(event);
			} else if (isVote) {
				this.handleVote(event);
			} else if (isResults) {
				this.handleResults(event);
			} else if (isExport) {
				this.handleExport(event);
			} else if (isComment) {
				this.handleComment(event);
			} else if (isExportComments) {
				this.handleExportComments(event);
			} else if (isDelete) {
				this.handleDelete(event);
			} else if (isView) {
				this.handleView(event);
			} else {
				event.reply("Unknown subcommand.").setEphemeral(true).queue();
			}
		} else {
			event.reply("No subcommand provided.").setEphemeral(true).queue();
		}
	}

	@Override
	public void autocomplete(CommandAutoCompleteInteractionEvent event) {
		String focusedOption = event.getFocusedOption().getName();
		boolean isPollIdOption = focusedOption.equals("poll_id") || focusedOption.equals("id");
		boolean isChoiceIndexOption = focusedOption.equals("choice_index");
		List<Command.Choice> choices = new ArrayList<>();

		if (isPollIdOption) {
			PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
			List<PollRepository.Poll> polls = pollRepo.findAll();
			for (PollRepository.Poll p : polls) {
				boolean isEligible = this.isEffectivelyOpen(p) || event.getSubcommandName().equals("results")
						|| event.getSubcommandName().equals("export")
						|| event.getSubcommandName().equals("export_comments")
						|| event.getSubcommandName().equals("delete") || event.getSubcommandName().equals("info");
				if (isEligible) {
					choices.add(new Command.Choice(p.getTitle(), p.getId()));
				}
			}
		}

		if (isChoiceIndexOption) {
			OptionMapping pollIdMapping = event.getOption("poll_id");
			boolean hasPollId = pollIdMapping != null;
			if (hasPollId) {
				long pollId = pollIdMapping.getAsLong();
				PollChoiceRepository choiceRepo = this.getBot().getRepositories()
						.getRepository(PollChoiceRepository.class);
				List<PollChoiceRepository.PollChoice> pollChoices = choiceRepo.findByPollId(pollId);
				for (PollChoiceRepository.PollChoice c : pollChoices) {
					choices.add(new Command.Choice(c.getChoiceText(), c.getLocalIndex()));
				}
			}
		}

		List<Command.Choice> limitedChoices = new ArrayList<>();
		int count = 0;
		for (Command.Choice c : choices) {
			boolean underLimit = count < 25;
			if (underLimit) {
				limitedChoices.add(c);
				count = count + 1;
			}
		}
		event.replyChoices(limitedChoices).queue();
	}

	private boolean isEffectivelyOpen(PollRepository.Poll poll) {
		boolean isOpen = false;
		boolean isManuallyOpen = poll.isOpen();
		if (isManuallyOpen) {
			boolean hasClosesAt = poll.getClosesAt() != null;
			if (hasClosesAt) {
				long currentUtcTime = Instant.now().atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
				boolean isPast = currentUtcTime > poll.getClosesAt();
				if (!isPast) {
					isOpen = true;
				}
			} else {
				isOpen = true;
			}
		}
		return isOpen;
	}

	private void handleCreate(SlashCommandInteractionEvent event) {
		String title = Objects.requireNonNull(event.getOption("title")).getAsString();
		String description = Objects.requireNonNull(event.getOption("description")).getAsString();
		String choicesRaw = Objects.requireNonNull(event.getOption("choices")).getAsString();
		OptionMapping durationOpt = event.getOption("duration_minutes");
		Long closesAt = null;
		boolean hasDuration = durationOpt != null;
		if (hasDuration) {
			long durationMs = durationOpt.getAsLong() * 60L * 1000L;
			closesAt = Instant.now().atZone(ZoneOffset.UTC).toInstant().toEpochMilli() + durationMs;
		}

		String responseMessage = "Poll creation failed.";
		String[] choices = choicesRaw.split("\\|");
		boolean hasValidChoices = choices.length >= 2;

		if (hasValidChoices) {
			PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
			PollChoiceRepository choiceRepo = this.getBot().getRepositories().getRepository(PollChoiceRepository.class);
			PollRepository.Poll newPoll = new PollRepository.Poll(0, title, description, true, event.getUser().getId(),
					closesAt, "NONE");
			pollRepo.insert(newPoll);

			long pollId = 0L;
			List<PollRepository.Poll> allPolls = pollRepo.findAll();
			long latestId = 0L;
			for (PollRepository.Poll p : allPolls) {
				boolean isLater = p.getId() > latestId;
				if (isLater) {
					latestId = p.getId();
				}
			}
			pollId = latestId;

			long localIndex = 1L;
			for (String c : choices) {
				String cleanChoice = c.trim();
				boolean isNotEmpty = !cleanChoice.isEmpty();
				if (isNotEmpty) {
					choiceRepo.insert(new PollChoiceRepository.PollChoice(0, pollId, localIndex, cleanChoice, 0));
					localIndex = localIndex + 1L;
				}
			}
			responseMessage = "Poll created successfully with ID " + pollId;
		} else {
			responseMessage = "You must provide at least two choices separated by |";
		}
		event.reply(responseMessage).queue();
	}

	private void handleClose(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		String responseMessage = "Close operation failed.";
		PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
		Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
		boolean isPresent = pollOpt.isPresent();

		if (isPresent) {
			PollRepository.Poll p = pollOpt.get();
			boolean isCreator = p.getCreatorId().equals(event.getUser().getId());
			boolean isAdmin = false;
			Member eventMember = event.getMember();
			boolean hasMember = eventMember != null;
			if (hasMember) {
				isAdmin = this.isAdmin(eventMember);
			}

			boolean canClose = isCreator || isAdmin;
			if (canClose) {
				PollRepository.Poll updatedPoll = new PollRepository.Poll(p.getId(), p.getTitle(), p.getDescription(),
						false, p.getCreatorId(), p.getClosesAt(), p.getRestrictionMode());
				pollRepo.update(updatedPoll);
				responseMessage = "Poll " + pollId + " has been marked as closed.";
			} else {
				responseMessage = "You do not have permission to close this poll.";
			}
		} else {
			responseMessage = "Poll not found.";
		}
		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleDelete(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		String responseMessage = "Delete operation failed.";
		PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
		Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
		boolean isPresent = pollOpt.isPresent();

		if (isPresent) {
			PollRepository.Poll p = pollOpt.get();
			boolean isCreator = p.getCreatorId().equals(event.getUser().getId());
			boolean isAdmin = false;
			Member eventMember = event.getMember();
			boolean hasMember = eventMember != null;
			if (hasMember) {
				isAdmin = this.isAdmin(eventMember);
			}

			boolean canDelete = isCreator || isAdmin;
			if (canDelete) {
				pollRepo.deleteByPrimaryKey(pollId);
				this.getBot().getRepositories().getRepository(PollChoiceRepository.class).deleteByPollId(pollId);
				this.getBot().getRepositories().getRepository(PollCommentRepository.class).deleteByPollId(pollId);
				this.getBot().getRepositories().getRepository(PollVoterRepository.class).deleteByPollId(pollId);
				this.getBot().getRepositories().getRepository(PollRoleRepository.class).deleteByPollId(pollId);
				responseMessage = "Poll " + pollId + " and all related data have been completely deleted.";
			} else {
				responseMessage = "You do not have permission to delete this poll.";
			}
		} else {
			responseMessage = "Poll not found.";
		}
		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleView(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		String responseMessage = "View operation failed.";
		PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
		Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
		boolean isPresent = pollOpt.isPresent();

		if (isPresent) {
			PollRepository.Poll p = pollOpt.get();
			StringBuilder sb = new StringBuilder();
			sb.append("**Title**: ").append(p.getTitle()).append("\n");
			sb.append("**Description**: ").append(p.getDescription()).append("\n");
			sb.append("**Status**: ").append(this.isEffectivelyOpen(p) ? "Open" : "Closed").append("\n");

			boolean hasClosesAt = p.getClosesAt() != null;
			if (hasClosesAt) {
				long unixSeconds = p.getClosesAt() / 1000L;
				sb.append("**Closes At (UTC)**: <t:").append(unixSeconds).append(":F>\n");
			}

			sb.append("**Restriction Mode**: ").append(p.getRestrictionMode()).append("\n");

			PollRoleRepository roleRepo = this.getBot().getRepositories().getRepository(PollRoleRepository.class);
			List<PollRoleRepository.PollRole> roles = roleRepo.findByPollId(pollId);
			boolean hasRoles = !roles.isEmpty();
			if (hasRoles) {
				sb.append("**Target Roles**: ");
				for (PollRoleRepository.PollRole pr : roles) {
					sb.append("<@&").append(pr.getRoleId()).append("> ");
				}
				sb.append("\n");
			}

			sb.append("\n**Choices**:\n");
			PollChoiceRepository choiceRepo = this.getBot().getRepositories().getRepository(PollChoiceRepository.class);
			List<PollChoiceRepository.PollChoice> choices = choiceRepo.findByPollId(pollId);
			for (PollChoiceRepository.PollChoice c : choices) {
				sb.append(c.getLocalIndex()).append(". ").append(c.getChoiceText()).append("\n");
			}
			responseMessage = sb.toString();
		} else {
			responseMessage = "Poll not found.";
		}
		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleRestrictionMode(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		String modeInput = Objects.requireNonNull(event.getOption("type")).getAsString().toUpperCase();
		OptionMapping forceOpt = event.getOption("force");
		boolean force = forceOpt != null && forceOpt.getAsBoolean();

		String responseMessage = "Restriction update failed.";
		PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
		Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
		boolean isPresent = pollOpt.isPresent();

		if (isPresent) {
			PollRepository.Poll p = pollOpt.get();
			boolean isCreator = p.getCreatorId().equals(event.getUser().getId());
			boolean isAdmin = false;
			Member eventMember = event.getMember();
			boolean hasMember = eventMember != null;
			if (hasMember) {
				isAdmin = this.isAdmin(eventMember);
			}

			boolean canModify = isCreator || isAdmin;
			if (canModify) {
				boolean validMode = modeInput.equals("NONE") || modeInput.equals("WHITELIST")
						|| modeInput.equals("BLACKLIST");
				if (validMode) {
					boolean proceed = true;
					if (!p.getRestrictionMode().equals(modeInput) && !p.getRestrictionMode().equals("NONE")) {
						if (!force) {
							proceed = false;
							responseMessage = "Cannot change active restriction mode directly without passing 'force: True', as it will clear existing roles.";
						} else {
							this.getBot().getRepositories().getRepository(PollRoleRepository.class)
									.deleteByPollId(pollId);
						}
					}
					if (proceed) {
						PollRepository.Poll updatedPoll = new PollRepository.Poll(p.getId(), p.getTitle(),
								p.getDescription(), p.isOpen(), p.getCreatorId(), p.getClosesAt(), modeInput);
						pollRepo.update(updatedPoll);
						responseMessage = "Poll restriction mode updated to " + modeInput;
					}
				} else {
					responseMessage = "Invalid mode. Use NONE, WHITELIST, or BLACKLIST.";
				}
			} else {
				responseMessage = "You do not have permission to modify this poll.";
			}
		} else {
			responseMessage = "Poll not found.";
		}
		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleRestrictionAddRole(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		Role targetRole = Objects.requireNonNull(event.getOption("role")).getAsRole();

		String responseMessage = "Role addition failed.";
		PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
		Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
		boolean isPresent = pollOpt.isPresent();

		if (isPresent) {
			PollRepository.Poll p = pollOpt.get();
			boolean isCreator = p.getCreatorId().equals(event.getUser().getId());
			boolean isAdmin = false;
			Member eventMember = event.getMember();
			boolean hasMember = eventMember != null;
			if (hasMember) {
				isAdmin = this.isAdmin(eventMember);
			}

			boolean canModify = isCreator || isAdmin;
			if (canModify) {
				boolean isNone = p.getRestrictionMode().equals("NONE");
				if (isNone) {
					responseMessage = "Cannot add roles when restriction mode is NONE. Change the mode first.";
				} else {
					PollRoleRepository roleRepo = this.getBot().getRepositories()
							.getRepository(PollRoleRepository.class);
					roleRepo.insert(new PollRoleRepository.PollRole(0, pollId, targetRole.getId()));
					responseMessage = "Role successfully added to the poll's " + p.getRestrictionMode() + " list.";
				}
			} else {
				responseMessage = "You do not have permission to modify this poll.";
			}
		} else {
			responseMessage = "Poll not found.";
		}
		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleRestrictionRemoveRole(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		Role targetRole = Objects.requireNonNull(event.getOption("role")).getAsRole();

		String responseMessage = "Role removal failed.";
		PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
		Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
		boolean isPresent = pollOpt.isPresent();

		if (isPresent) {
			PollRepository.Poll p = pollOpt.get();
			boolean isCreator = p.getCreatorId().equals(event.getUser().getId());
			boolean isAdmin = false;
			Member eventMember = event.getMember();
			boolean hasMember = eventMember != null;
			if (hasMember) {
				isAdmin = this.isAdmin(eventMember);
			}

			boolean canModify = isCreator || isAdmin;
			if (canModify) {
				PollRoleRepository roleRepo = this.getBot().getRepositories().getRepository(PollRoleRepository.class);
				boolean deleted = roleRepo.deleteByPollIdAndRoleId(pollId, targetRole.getId());
				if (deleted) {
					responseMessage = "Role successfully removed from the poll restriction list.";
				} else {
					responseMessage = "Role was not found in the restriction list.";
				}
			} else {
				responseMessage = "You do not have permission to modify this poll.";
			}
		} else {
			responseMessage = "Poll not found.";
		}
		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleVote(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("poll_id")).getAsLong();
		long choiceIndex = Objects.requireNonNull(event.getOption("choice_index")).getAsLong();
		String userId = event.getUser().getId();
		String responseMessage = "Vote operation failed.";

		PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
		PollChoiceRepository choiceRepo = this.getBot().getRepositories().getRepository(PollChoiceRepository.class);
		PollVoterRepository voterRepo = this.getBot().getRepositories().getRepository(PollVoterRepository.class);

		Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
		boolean isPollPresent = pollOpt.isPresent();

		if (isPollPresent) {
			PollRepository.Poll poll = pollOpt.get();
			boolean isOpen = this.isEffectivelyOpen(poll);

			if (isOpen) {
				boolean canVote = false;
				String mode = poll.getRestrictionMode();
				boolean isNone = mode.equals("NONE");
				boolean isWhitelist = mode.equals("WHITELIST");
				boolean isBlacklist = mode.equals("BLACKLIST");

				Member member = event.getMember();
				boolean hasMember = member != null;

				if (isNone) {
					canVote = true;
				} else if (hasMember) {
					PollRoleRepository roleRepo = this.getBot().getRepositories()
							.getRepository(PollRoleRepository.class);
					List<PollRoleRepository.PollRole> restrictedRoles = roleRepo.findByPollId(pollId);

					if (isWhitelist) {
						boolean hasWhitelistedRole = false;
						for (PollRoleRepository.PollRole pr : restrictedRoles) {
							boolean hasRole = member.getRoles().stream()
									.anyMatch(r -> r.getId().equals(pr.getRoleId()));
							if (hasRole) {
								hasWhitelistedRole = true;
							}
						}
						if (hasWhitelistedRole) {
							canVote = true;
						} else {
							responseMessage = "You do not have a whitelisted role required to vote on this poll.";
						}
					} else if (isBlacklist) {
						boolean hasBlacklistedRole = false;
						for (PollRoleRepository.PollRole pr : restrictedRoles) {
							boolean hasRole = member.getRoles().stream()
									.anyMatch(r -> r.getId().equals(pr.getRoleId()));
							if (hasRole) {
								hasBlacklistedRole = true;
							}
						}
						if (!hasBlacklistedRole) {
							canVote = true;
						} else {
							responseMessage = "Your role is blacklisted from voting on this poll.";
						}
					}
				} else {
					responseMessage = "You must execute this command in a server to respect role restrictions.";
				}

				if (canVote) {
					Optional<PollChoiceRepository.PollChoice> choiceOpt = choiceRepo.findByPollIdAndLocalIndex(pollId,
							choiceIndex);
					boolean isChoicePresent = choiceOpt.isPresent();
					if (isChoicePresent) {
						boolean voteRegistered = voterRepo.recordVoteAttempt(pollId, userId);
						if (voteRegistered) {
							choiceRepo.incrementVote(pollId, choiceIndex);
							responseMessage = "Vote recorded.";
						} else {
							responseMessage = "You have already voted on this poll.";
						}
					} else {
						responseMessage = "Choice not found.";
					}
				}
			} else {
				responseMessage = "This poll is closed.";
			}
		} else {
			responseMessage = "Poll not found.";
		}
		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleResults(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		OptionMapping publicOpt = event.getOption("public");
		boolean isPublic = false;
		boolean hasPublicOpt = publicOpt != null;
		if (hasPublicOpt) {
			isPublic = publicOpt.getAsBoolean();
		}

		String responseMessage = "Result retrieval failed.";
		boolean ephemeralResponse = true;
		PollChoiceRepository choiceRepo = this.getBot().getRepositories().getRepository(PollChoiceRepository.class);
		PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
		Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
		boolean isPresent = pollOpt.isPresent();

		if (isPresent) {
			PollRepository.Poll poll = pollOpt.get();
			boolean isCreator = poll.getCreatorId().equals(event.getUser().getId());
			boolean isAdmin = false;
			Member eventMember = event.getMember();
			boolean hasMember = eventMember != null;
			if (hasMember) {
				isAdmin = this.isAdmin(eventMember);
			}

			boolean hasPermission = isCreator || isAdmin;
			if (hasPermission) {
				List<PollChoiceRepository.PollChoice> choices = choiceRepo.findByPollId(pollId);
				long totalSum = 0L;
				for (PollChoiceRepository.PollChoice c : choices) {
					totalSum = totalSum + c.getVotesCount();
				}

				StringBuilder sb = new StringBuilder();
				sb.append("**").append(poll.getTitle()).append("**\n");
				sb.append("Total votes: ").append(totalSum).append("\n\n");
				for (PollChoiceRepository.PollChoice c : choices) {
					sb.append(c.getLocalIndex()).append(" - ").append(c.getChoiceText()).append(": ")
							.append(c.getVotesCount()).append(" votes\n");
				}

				responseMessage = sb.toString();
				if (isPublic) {
					ephemeralResponse = false;
				}
			} else {
				responseMessage = "You do not have permission to view these results.";
			}
		} else {
			responseMessage = "Poll not found.";
		}
		event.reply(responseMessage).setEphemeral(ephemeralResponse).queue();
	}

	private void handleExport(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		event.deferReply(true).queue();

		java.util.concurrent.CompletableFuture.runAsync(() -> {
			String errorMessage = "Export failed.";
			boolean success = false;
			FileUpload uploadData = null;
			PollChoiceRepository choiceRepo = this.getBot().getRepositories().getRepository(PollChoiceRepository.class);
			PollVoterRepository voterRepo = this.getBot().getRepositories().getRepository(PollVoterRepository.class);
			PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
			Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
			boolean isPresent = pollOpt.isPresent();

			if (isPresent) {
				PollRepository.Poll poll = pollOpt.get();
				boolean isCreator = poll.getCreatorId().equals(event.getUser().getId());
				boolean isAdmin = false;
				Member eventMember = event.getMember();
				boolean hasMember = eventMember != null;
				if (hasMember) {
					isAdmin = this.isAdmin(eventMember);
				}

				boolean hasPermission = isCreator || isAdmin;
				if (hasPermission) {
					List<PollChoiceRepository.PollChoice> choices = choiceRepo.findByPollId(pollId);
					List<PollVoterRepository.PollVoter> voters = voterRepo.findByPollId(pollId);
					long totalSum = 0L;
					for (PollChoiceRepository.PollChoice c : choices) {
						totalSum = totalSum + c.getVotesCount();
					}

					StringBuilder txtBuilder = new StringBuilder();
					txtBuilder.append("Poll ID: ").append(pollId).append("\n");
					txtBuilder.append("Total Votes: ").append(totalSum).append("\n\n");
					txtBuilder.append("Results:\n");

					for (PollChoiceRepository.PollChoice c : choices) {
						txtBuilder.append("- ").append(c.getChoiceText()).append(": ").append(c.getVotesCount())
								.append(" votes\n");
					}

					txtBuilder.append("\nUsers who voted:\n");
					Guild guild = event.getGuild();
					boolean hasGuild = guild != null;
					for (PollVoterRepository.PollVoter v : voters) {
						String identity = v.getUserId();
						if (hasGuild) {
							Member m = guild.getMemberById(v.getUserId());
							boolean isMemberPresent = m != null;
							if (isMemberPresent) {
								identity = m.getEffectiveName();
							}
							boolean identityIsStillId = identity.equals(v.getUserId());
							if (identityIsStillId && hasGuild) {
								try {
									identity = guild.retrieveMemberById(v.getUserId()).complete().getEffectiveName();
								} catch (Exception ignored) {
								}
							}
						}
						boolean identityIsStillIdAfterMember = identity.equals(v.getUserId());
						if (identityIsStillIdAfterMember) {
							try {
								identity = event.getJDA().retrieveUserById(v.getUserId()).complete().getName();
							} catch (Exception ignored) {
							}
						}
						txtBuilder.append(identity).append("\n");
					}

					byte[] bytes = txtBuilder.toString().getBytes(StandardCharsets.UTF_8);
					uploadData = FileUpload.fromData(bytes, "poll_" + pollId + "_export.txt");
					success = true;
				} else {
					errorMessage = "You do not have permission to export this poll.";
				}
			} else {
				errorMessage = "Poll not found.";
			}

			if (success) {
				event.getHook().sendFiles(uploadData).queue();
			} else {
				event.getHook().sendMessage(errorMessage).queue();
			}
		});
	}

	private void handleComment(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		String text = Objects.requireNonNull(event.getOption("text")).getAsString();
		boolean anonymous = Objects.requireNonNull(event.getOption("anonymous")).getAsBoolean();
		String responseMessage = "Comment operation failed.";
		PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
		Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
		boolean isPresent = pollOpt.isPresent();

		if (isPresent) {
			String userIdParam = "";
			if (!anonymous) {
				userIdParam = event.getUser().getId();
			} else {
				userIdParam = null;
			}
			PollCommentRepository commentRepo = this.getBot().getRepositories()
					.getRepository(PollCommentRepository.class);
			commentRepo.insert(new PollCommentRepository.PollComment(0, pollId, userIdParam, text, anonymous));
			responseMessage = "Comment added successfully.";
		} else {
			responseMessage = "Poll not found.";
		}
		event.reply(responseMessage).setEphemeral(true).queue();
	}

	private void handleExportComments(SlashCommandInteractionEvent event) {
		long pollId = Objects.requireNonNull(event.getOption("id")).getAsLong();
		event.deferReply(true).queue();

		java.util.concurrent.CompletableFuture.runAsync(() -> {
			String errorMessage = "Comment export failed.";
			boolean success = false;
			FileUpload uploadData = null;
			PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
			Optional<PollRepository.Poll> pollOpt = pollRepo.findByPrimaryKey(pollId);
			boolean isPresent = pollOpt.isPresent();

			if (isPresent) {
				PollRepository.Poll poll = pollOpt.get();
				boolean isCreator = poll.getCreatorId().equals(event.getUser().getId());
				boolean isAdmin = false;
				Member eventMember = event.getMember();
				boolean hasMember = eventMember != null;
				if (hasMember) {
					isAdmin = this.isAdmin(eventMember);
				}

				boolean hasPermission = isCreator || isAdmin;
				if (hasPermission) {
					PollCommentRepository commentRepo = this.getBot().getRepositories()
							.getRepository(PollCommentRepository.class);
					List<PollCommentRepository.PollComment> comments = commentRepo.findByPollId(pollId);
					Guild guild = event.getGuild();
					boolean hasGuild = guild != null;

					StringBuilder tsvBuilder = new StringBuilder();
					tsvBuilder.append("User\tComment\n");

					for (PollCommentRepository.PollComment c : comments) {
						String identity = "Anonyme";
						boolean isNotAnonymous = !c.isAnonymous() && c.getUserId() != null;
						if (isNotAnonymous) {
							identity = c.getUserId();
							if (hasGuild) {
								Member m = guild.getMemberById(c.getUserId());
								boolean isMemberPresent = m != null;
								if (isMemberPresent) {
									identity = m.getEffectiveName();
								}
								boolean identityIsStillId = identity.equals(c.getUserId());
								if (identityIsStillId && hasGuild) {
									try {
										identity = guild.retrieveMemberById(c.getUserId()).complete()
												.getEffectiveName();
									} catch (Exception ignored) {
									}
								}
							}
							boolean identityIsStillIdAfterMember = identity.equals(c.getUserId());
							if (identityIsStillIdAfterMember) {
								try {
									identity = event.getJDA().retrieveUserById(c.getUserId()).complete().getName();
								} catch (Exception ignored) {
								}
							}
						}

						String safeComment = c.getCommentText().replace("\n", " ").replace("\t", " ");
						String safeIdentity = identity.replace("\n", " ").replace("\t", " ");
						tsvBuilder.append(safeIdentity).append("\t").append(safeComment).append("\n");
					}

					byte[] bytes = tsvBuilder.toString().getBytes(StandardCharsets.UTF_8);
					uploadData = FileUpload.fromData(bytes, "poll_" + pollId + "_comments.tsv");
					success = true;
				} else {
					errorMessage = "You do not have permission to export comments for this poll.";
				}
			} else {
				errorMessage = "Poll not found.";
			}

			if (success) {
				event.getHook().sendFiles(uploadData).queue();
			} else {
				event.getHook().sendMessage(errorMessage).queue();
			}
		});
	}
}