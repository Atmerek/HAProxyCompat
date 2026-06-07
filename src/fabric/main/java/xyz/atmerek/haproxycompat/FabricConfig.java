package xyz.atmerek.haproxycompat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class FabricConfig {

    public boolean enabled = true;
    public boolean requireProxyProtocol = true;
    public List<String> trustedProxies = List.of("127.0.0.1/32", "::1/128");
    public boolean logConnections = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static FabricConfig loadOrCreate() {
        final Path configFile = FabricLoader.getInstance().getConfigDir().resolve("haproxycompat.json");
        if (Files.exists(configFile)) {
            try (final Reader reader = Files.newBufferedReader(configFile)) {
                final FabricConfig loaded = GSON.fromJson(reader, FabricConfig.class);
                return loaded != null ? loaded : new FabricConfig();
            } catch (final IOException e) {
                HAProxyCompatConfig.LOGGER.error("Failed to read config, using defaults: {}", e.getMessage());
                return new FabricConfig();
            }
        }
        final FabricConfig defaults = new FabricConfig();
        try {
            Files.createDirectories(configFile.getParent());
            try (final Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(defaults, writer);
            }
            HAProxyCompatConfig.LOGGER.info("Created default config at {}", configFile);
        } catch (final IOException e) {
            HAProxyCompatConfig.LOGGER.error("Failed to write default config: {}", e.getMessage());
        }
        return defaults;
    }
}
