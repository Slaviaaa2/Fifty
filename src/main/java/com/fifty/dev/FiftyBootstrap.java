package com.fifty.dev;

import com.fifty.dev.commands.FiftyCommands;
import io.papermc.paper.datapack.Datapack;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

public final class FiftyBootstrap implements PluginBootstrap {
    private static final String DATAPACK_RESOURCE = "/fifty_datapack";
    private static final String DATAPACK_ID = "advancements";

    @Override
    public void bootstrap(final BootstrapContext context) {
        var lifecycleManager = context.getLifecycleManager();
        lifecycleManager.registerEventHandler(
                LifecycleEvents.COMMANDS, commands -> {
                    commands.registrar().register(FiftyCommands.create());
                }
        );
        lifecycleManager.registerEventHandler(
                LifecycleEvents.DATAPACK_DISCOVERY.newHandler(event -> {
                    URL resource = FiftyBootstrap.class.getResource(
                            DATAPACK_RESOURCE
                    );

                    if (resource == null) {
                        throw new IllegalStateException(
                                "Bundled datapack was not found: " +
                                        DATAPACK_RESOURCE
                        );
                    }

                    try {
                        var datapack = event.registrar().discoverPack(
                                resource.toURI(),
                                DATAPACK_ID,
                                configurer -> configurer
                                        .title(Component.text("Fifty advancements"))
                                        .autoEnableOnServerStart(true)
                                        .position(false, Datapack.Position.TOP)
                        );

                        if (datapack == null) {
                            throw new IllegalStateException(
                                    "Paper rejected the bundled Fifty datapack"
                            );
                        }
                    } catch (URISyntaxException | IOException e) {
                        throw new IllegalStateException(
                                "Failed to discover the bundled Fifty datapack",
                                e
                        );
                    }
                })
        );
    }
}
