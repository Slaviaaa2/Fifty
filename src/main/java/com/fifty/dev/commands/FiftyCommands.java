package com.fifty.dev.commands;

import com.fifty.dev.Fifty;
import com.fifty.dev.api.CustomItemFactory;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class FiftyCommands {
    private static final String GIVE_ITEM_PERMISSION =
            "fifty.command.item.give";
    private static final String SELL_ITEM_PERMISSION =
            "fifty.command.item.sell";

    private FiftyCommands(){}

    public static LiteralCommandNode<CommandSourceStack> create(){
        return Commands.literal("fft")
                .then(Commands.literal("item")
                        .then(Commands.literal("give")
                                .requires(source -> source.getSender()
                                        .hasPermission(GIVE_ITEM_PERMISSION))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            if (!(ctx.getSource().getExecutor() instanceof Player player)) return 0;

                                            String id = StringArgumentType.getString(ctx, "id");
                                            var item = CustomItemFactory.Provide(id);
                                            if (item == null) return 0;
                                            item.giveItem(player);
                                            return 1;
                                        })))
                        .then(Commands.literal("sell")
                                .requires(source -> source.getSender()
                                        .hasPermission(SELL_ITEM_PERMISSION))
                                .executes(ctx -> sellMainHand(ctx.getSource())))

                ).build();
    }

    private static int sellMainHand(CommandSourceStack source) {
        if (!(source.getExecutor() instanceof Player player)) {
            source.getSender().sendMessage(Component.text(
                    "このコマンドはプレイヤーのみ実行できます。",
                    NamedTextColor.RED
            ));
            return 0;
        }

        Fifty plugin = JavaPlugin.getPlugin(Fifty.class);
        var vaultEconomy = plugin.getVaultEconomy();

        if (vaultEconomy == null || !vaultEconomy.isAvailable()) {
            player.sendMessage(Component.text(
                    "Vaultの通貨サービスが利用できません。",
                    NamedTextColor.RED
            ));
            return 0;
        }

        ItemStack stack = player.getInventory().getItemInMainHand();
        if (stack.isEmpty()) {
            player.sendMessage(Component.text(
                    "売却するアイテムをメインハンドに持ってください。",
                    NamedTextColor.RED
            ));
            return 0;
        }

        ItemRarity rarity = getRarity(stack);
        double unitPrice = plugin.getConfig().getDouble(
                "item-selling.prices." + rarity.name(),
                0.0D
        );
        double totalPrice = unitPrice * stack.getAmount();

        if (!Double.isFinite(unitPrice)
                || unitPrice <= 0.0D
                || !Double.isFinite(totalPrice)) {
            player.sendMessage(Component.text(
                    rarity.name() + "レアリティの売却価格が設定されていません。",
                    NamedTextColor.RED
            ));
            return 0;
        }

        Optional<EconomyResponse> transaction;
        try {
            transaction = vaultEconomy.deposit(player, totalPrice);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning(
                    "Vault failed to deposit item sale proceeds for "
                            + player.getName() + ": " + exception.getMessage()
            );
            player.sendMessage(Component.text(
                    "通貨サービスでエラーが発生したため、アイテムは売却されませんでした。",
                    NamedTextColor.RED
            ));
            return 0;
        }

        EconomyResponse response = transaction.orElse(null);

        if (response == null || !response.transactionSuccess()) {
            String reason = response == null || response.errorMessage == null
                    || response.errorMessage.isBlank()
                    ? "不明なエラー"
                    : response.errorMessage;
            player.sendMessage(Component.text(
                    "売却に失敗しました: " + reason,
                    NamedTextColor.RED
            ));
            return 0;
        }

        int soldAmount = stack.getAmount();
        player.getInventory().setItemInMainHand(ItemStack.empty());

        String formattedAmount;
        try {
            formattedAmount = vaultEconomy
                    .getProvider()
                    .map(economy -> economy.format(response.amount))
                    .orElse(Double.toString(response.amount));
        } catch (RuntimeException exception) {
            formattedAmount = Double.toString(response.amount);
        }
        player.sendMessage(Component.text(
                rarity.name() + "レアリティのアイテムを"
                        + soldAmount + "個、" + formattedAmount + "で売却しました。",
                NamedTextColor.GREEN
        ));
        return 1;
    }

    private static ItemRarity getRarity(ItemStack stack) {
        var meta = stack.getItemMeta();
        if (meta.hasRarity()) {
            return meta.getRarity();
        }

        ItemRarity materialRarity = stack.getType()
                .asItemType()
                .getItemRarity();
        return materialRarity == null ? ItemRarity.COMMON : materialRarity;
    }
}
