package fr.umontpellier.iut.discordbot.commands.migration;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
//import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.ArrayList;

public class MigrateChannelsCommand extends AbstractCommand {

	private ArrayList<String> rolesA1 = new ArrayList<>(
		Arrays.asList("S1", "S2", "S3", "S4", "S5", "S6")
	);

	private ArrayList<String> rolesA2 = new ArrayList<>(
		Arrays.asList("Q1", "Q2", "Q3", "Q4", "Q5")
	);

	private ArrayList<String> rolesA3 = new ArrayList<>(
		Arrays.asList("G1", "G2", "G3", "G4", "G5")
	);

	public MigrateChannelsCommand(Bot bot) {
		super(bot);
	}

	@NotNull
	@Override
	public SlashCommandData getCommandInformation() {
		return Commands.slash("migrate_channels", "Lance la migration des salons de classe");
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		return;
	}
}
