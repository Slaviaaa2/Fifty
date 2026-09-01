package com.fifty.dev.commands;

import com.fifty.dev.api.CustomItemFactory;
import com.fifty.dev.items.CraftableCore;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

public final class FiftyCommands {
    private FiftyCommands(){}

    public static LiteralCommandNode<CommandSourceStack> create(){
        return Commands.literal("fifty")
                .then(Commands.literal("item")
                        .then(Commands.literal("give")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            if (!(ctx.getSource().getExecutor() instanceof Player player)) return 0;

                                            String id = StringArgumentType.getString(ctx, "id");
                                            var item = CustomItemFactory.Provide(id);
                                            if (item == null) return 0;
                                            item.giveItem(player);
                                            return 1;
                                        })))

                ).build();
    }
}
