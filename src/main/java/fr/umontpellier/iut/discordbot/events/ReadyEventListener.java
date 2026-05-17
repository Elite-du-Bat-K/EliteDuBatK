package fr.umontpellier.iut.discordbot.events;

import fr.umontpellier.iut.discordbot.Bot;
import fr.umontpellier.iut.discordbot.lib.AbstractEventListener;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.session.ReadyEvent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ReadyEventListener extends AbstractEventListener {
	public ReadyEventListener(Bot bot) {
		super(bot);
	}

	@Override
	public void onReady(ReadyEvent event) {
		logger.info("Bot is ready !");

		getBot().getCommandManager().registerCommands();

		Runnable updateActivity = () -> {
			this.logger.info("Updating activity...");
			event.getJDA().getPresence().setActivity(Activity.of(Activity.ActivityType.CUSTOM_STATUS, String.format("Regarde %d serveurs", event.getJDA().getGuilds().size())));
		};

		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		executor.scheduleAtFixedRate(updateActivity, 0, 5, TimeUnit.MINUTES);
	}
}
