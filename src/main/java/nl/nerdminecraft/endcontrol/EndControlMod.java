package nl.nerdminecraft.endcontrol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EndControlMod implements ModInitializer {
    public static final String MOD_ID = "nerd_end_control";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("nerd-end-control.json");

    private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private static EndConfig config = new EndConfig();
    private static final Map<UUID, Long> LAST_BLOCK_MESSAGE = new HashMap<>();
    private static int tickCounter = 0;

    @Override
    public void onInitialize() {
        loadConfig();
        registerCommands();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter >= 20) {
                tickCounter = 0;
                refreshSchedule(server);
            }
        });

        LOGGER.info("Nerd End Control geladen. The End is {}.",
                config.open ? "OPEN" : "GESLOTEN");
    }

    public static boolean isOpen() {
        return config.open;
    }

    public static void refreshSchedule(MinecraftServer server) {
        if (config.scheduledOpenAt <= 0L || config.open) return;

        if (System.currentTimeMillis() >= config.scheduledOpenAt) {
            config.open = true;
            config.scheduledOpenAt = 0L;
            saveConfig();

            Component message = Component.literal("🐉 The End is nu geopend!");
            server.getPlayerList().broadcastSystemMessage(message, false);
            server.sendSystemMessage(message);
            LOGGER.info("De geplande End-opening is uitgevoerd.");
        }
    }

    public static void notifyBlocked(ServerPlayer player) {
        long now = System.currentTimeMillis();
        long previous = LAST_BLOCK_MESSAGE.getOrDefault(player.getUUID(), 0L);
        if (now - previous < 2500L) return;

        LAST_BLOCK_MESSAGE.put(player.getUUID(), now);

        if (config.scheduledOpenAt > 0L) {
            player.sendSystemMessage(Component.literal(
                    "🐉 The End is gesloten. Opening: " + formatEpoch(config.scheduledOpenAt) + "."
            ));
        } else {
            player.sendSystemMessage(Component.literal("🐉 The End is momenteel gesloten."));
        }
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
            dispatcher.register(
                    Commands.literal("end")
                            .requires(source ->
                                    source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                            .then(Commands.literal("status")
                                    .executes(context -> status(context.getSource())))
                            .then(Commands.literal("open")
                                    .executes(context -> open(context.getSource())))
                            .then(Commands.literal("close")
                                    .executes(context -> close(context.getSource())))
                            .then(Commands.literal("cancel")
                                    .executes(context -> cancel(context.getSource())))
                            .then(Commands.literal("schedule")
                                    .then(Commands.argument("date", StringArgumentType.word())
                                            .then(Commands.argument("time", StringArgumentType.word())
                                                    .executes(context -> schedule(
                                                            context.getSource(),
                                                            StringArgumentType.getString(context, "date"),
                                                            StringArgumentType.getString(context, "time")
                                                    ))
                                            )
                                    )
                            )
            );
        });
    }

    private static int status(CommandSourceStack source) {
        refreshSchedule(source.getServer());

        source.sendSuccess(
                () -> Component.literal(
                        "The End: " + (config.open ? "OPEN" : "GESLOTEN")
                                + "\nGeplande opening: "
                                + (config.scheduledOpenAt > 0L
                                ? formatEpoch(config.scheduledOpenAt) : "geen")
                                + "\nTijdzone: Europe/Amsterdam"
                ),
                false
        );
        return 1;
    }

    private static int open(CommandSourceStack source) {
        config.open = true;
        config.scheduledOpenAt = 0L;
        saveConfig();

        source.getServer().getPlayerList()
                .broadcastSystemMessage(Component.literal("🐉 The End is geopend!"), false);

        source.sendSuccess(
                () -> Component.literal("The End geopend. Een eventuele planning is geannuleerd."),
                false
        );
        return 1;
    }

    private static int close(CommandSourceStack source) {
        config.open = false;
        saveConfig();

        source.getServer().getPlayerList()
                .broadcastSystemMessage(Component.literal("🐉 The End is gesloten."), false);

        source.sendSuccess(
                () -> Component.literal(
                        "The End gesloten."
                                + (config.scheduledOpenAt > 0L
                                ? " Geplande opening blijft staan op "
                                + formatEpoch(config.scheduledOpenAt) + "."
                                : "")
                ),
                false
        );
        return 1;
    }

    private static int cancel(CommandSourceStack source) {
        if (config.scheduledOpenAt <= 0L) {
            source.sendFailure(Component.literal("Er staat geen geplande End-opening."));
            return 0;
        }

        config.scheduledOpenAt = 0L;
        saveConfig();

        source.sendSuccess(
                () -> Component.literal(
                        "Geplande End-opening geannuleerd. Huidige status: "
                                + (config.open ? "OPEN" : "GESLOTEN") + "."
                ),
                false
        );
        return 1;
    }

    private static int schedule(CommandSourceStack source, String dateText, String timeText) {
        try {
            LocalDate date = LocalDate.parse(dateText);
            LocalTime time = LocalTime.parse(timeText);
            ZonedDateTime scheduled = ZonedDateTime.of(date, time, ZONE);
            long epoch = scheduled.toInstant().toEpochMilli();

            if (epoch <= System.currentTimeMillis()) {
                source.sendFailure(Component.literal(
                        "De geplande opening moet in de toekomst liggen."
                ));
                return 0;
            }

            config.open = false;
            config.scheduledOpenAt = epoch;
            saveConfig();

            source.sendSuccess(
                    () -> Component.literal(
                            "The End gesloten en gepland om te openen op "
                                    + formatEpoch(epoch) + " (Europe/Amsterdam)."
                    ),
                    false
            );
            return 1;
        } catch (DateTimeException exception) {
            source.sendFailure(Component.literal(
                    "Ongeldige datum/tijd. Gebruik: /end schedule YYYY-MM-DD HH:MM"
            ));
            return 0;
        }
    }

    private static String formatEpoch(long epochMillis) {
        return DISPLAY_FORMAT.format(
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZONE)
        );
    }

    private static void loadConfig() {
        if (!Files.exists(CONFIG_PATH)) {
            config = new EndConfig();
            saveConfig();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            EndConfig loaded = GSON.fromJson(reader, EndConfig.class);
            config = loaded != null ? loaded : new EndConfig();
        } catch (Exception exception) {
            LOGGER.error("Kon {} niet lezen. Standaardconfig wordt gebruikt.",
                    CONFIG_PATH, exception);
            config = new EndConfig();
        }
    }

    private static void saveConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException exception) {
            LOGGER.error("Kon End-config niet opslaan.", exception);
        }
    }

    private static final class EndConfig {
        boolean open = false;
        long scheduledOpenAt = 0L;
    }
}
