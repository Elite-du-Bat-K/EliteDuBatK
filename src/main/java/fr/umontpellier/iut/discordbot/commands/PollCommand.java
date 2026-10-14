package fr.umontpellier.iut.discordbot.commands;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.database.repositories.PollChoiceRepository;
import fr.umontpellier.iut.discordbot.database.repositories.PollCommentRepository;
import fr.umontpellier.iut.discordbot.database.repositories.PollRepository;
import fr.umontpellier.iut.discordbot.database.repositories.PollVoterRepository;
import fr.umontpellier.iut.discordbot.lib.AbstractCommandWithAutocomplete;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
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
				.addOption(OptionType.STRING, "choices", "Choices separated by | (e.g. Yes|No)", true);

		SubcommandData closeData = new SubcommandData("close", "Close a poll")
				.addOption(OptionType.INTEGER, "id", "Poll ID", true, true);

		SubcommandData voteData = new SubcommandData("vote", "Vote on a poll")
				.addOption(OptionType.INTEGER, "poll_id", "Poll ID", true, true)
				.addOption(OptionType.INTEGER, "choice_index", "Select your choice", true, true);

		SubcommandData resultsData = new SubcommandData("results", "View poll results")
				.addOption(OptionType.INTEGER, "id", "Poll ID", true, true)
				.addOption(OptionType.BOOLEAN, "public", "Make the results visible to everyone", false);

		SubcommandData exportResultsData = new SubcommandData("export", "Export poll results")
				.addOption(OptionType.INTEGER, "id", "Poll ID", true, true);

		SubcommandData commentData = new SubcommandData("comment", "Add a comment")
				.addOption(OptionType.INTEGER, "id", "Poll ID", true, true)
				.addOption(OptionType.STRING, "text", "Comment text", true)
				.addOption(OptionType.BOOLEAN, "anonymous", "Hide your identity", true);

		SubcommandData exportCommentsData = new SubcommandData("export_comments", "Export all comments")
				.addOption(OptionType.INTEGER, "id", "Poll ID", true, true);

		return Commands.slash("poll", "Manage polls")
				.addSubcommands(createData, closeData, voteData, resultsData, exportResultsData, commentData,
						exportCommentsData)
				.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		String subName = event.getSubcommandName();
		boolean hasSubName = subName != null;

		if (hasSubName) {
			boolean isCreate = subName.equals("create");
			boolean isClose = subName.equals("close");
			boolean isVote = subName.equals("vote");
			boolean isResults = subName.equals("results");
			boolean isExport = subName.equals("export");
			boolean isComment = subName.equals("comment");
			boolean isExportComments = subName.equals("export_comments");

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
				boolean isEligible = p.isOpen();
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

	private void handleCreate(SlashCommandInteractionEvent event) {
		String title = Objects.requireNonNull(event.getOption("title")).getAsString();
		String description = Objects.requireNonNull(event.getOption("description")).getAsString();
		String choicesRaw = Objects.requireNonNull(event.getOption("choices")).getAsString();
		String responseMessage = "Poll creation failed.";

		String[] choices = choicesRaw.split("\\|");
		boolean hasValidChoices = choices.length >= 2;

		if (hasValidChoices) {
			PollRepository pollRepo = this.getBot().getRepositories().getRepository(PollRepository.class);
			PollChoiceRepository choiceRepo = this.getBot().getRepositories().getRepository(PollChoiceRepository.class);

			PollRepository.Poll newPoll = new PollRepository.Poll(0, title, description, true, event.getUser().getId());
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
						false, p.getCreatorId());
				pollRepo.update(updatedPoll);
				responseMessage = "Poll " + pollId + " has been closed.";
			} else {
				responseMessage = "You do not have permission to close this poll.";
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
			boolean isOpen = poll.isOpen();

			if (isOpen) {
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

					StringBuilder csvBuilder = new StringBuilder();
					csvBuilder.append("Total Votes: ").append(totalSum).append("\n");
					csvBuilder.append("Local Index,Choice Text,Votes\n");

					for (PollChoiceRepository.PollChoice c : choices) {
						csvBuilder.append(c.getLocalIndex()).append(",")
								.append("\"").append(c.getChoiceText().replace("\"", "\"\"")).append("\",")
								.append(c.getVotesCount()).append("\n");
					}

					csvBuilder.append("\nUsers who voted:\n");
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
						}
						boolean identityIsStillId = identity.equals(v.getUserId());
						if (identityIsStillId) {
							try {
								identity = event.getJDA().retrieveUserById(v.getUserId()).complete().getName();
							} catch (Exception ignored) {
							}
						}
						csvBuilder.append("\"").append(identity.replace("\"", "\"\"")).append("\"\n");
					}

					byte[] bytes = csvBuilder.toString().getBytes(StandardCharsets.UTF_8);
					uploadData = FileUpload.fromData(bytes, "poll_" + pollId + "_export.csv");
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
					StringBuilder csvBuilder = new StringBuilder();
					csvBuilder.append("User,Comment\n");

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
							}
							boolean identityIsStillId = identity.equals(c.getUserId());
							if (identityIsStillId) {
								try {
									identity = event.getJDA().retrieveUserById(c.getUserId()).complete().getName();
								} catch (Exception ignored) {
								}
							}
						}

						String safeComment = "\"" + c.getCommentText().replace("\"", "\"\"") + "\"";
						String safeIdentity = "\"" + identity.replace("\"", "\"\"") + "\"";
						csvBuilder.append(safeIdentity).append(",").append(safeComment).append("\n");
					}

					byte[] bytes = csvBuilder.toString().getBytes(StandardCharsets.UTF_8);
					uploadData = FileUpload.fromData(bytes, "poll_" + pollId + "_comments.csv");
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