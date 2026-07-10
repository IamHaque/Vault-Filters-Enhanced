package net.joseph.vaultfilters.network;

import net.joseph.vaultfilters.configs.VFServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class ShareC2SPacket {
    private static final int MAX_SHARE_SIZE = 51200;

    private final String targetPlayerName;
    private final String filterJson;

    public ShareC2SPacket(String targetPlayerName, String filterJson) {
        this.targetPlayerName = targetPlayerName;
        this.filterJson = filterJson;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(targetPlayerName, 16);
        buf.writeUtf(filterJson, MAX_SHARE_SIZE);
    }

    public static ShareC2SPacket decode(FriendlyByteBuf buf) {
        return new ShareC2SPacket(buf.readUtf(16), buf.readUtf(MAX_SHARE_SIZE));
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;

            int maxBytes = VFServerConfig.MAX_SHARE_BYTES.get();
            if (filterJson.length() > maxBytes) {
                sender.sendMessage(new TextComponent("\u00a76[Filter Library] \u00a7rFilter too large to share (" + filterJson.length() + " bytes, max " + maxBytes + ")"), sender.getUUID());
                return;
            }

            if (!ShareTracker.tryShare(sender.getUUID())) {
                sender.sendMessage(new TextComponent("\u00a76[Filter Library] \u00a7rYou are sharing too fast. Please wait."), sender.getUUID());
                return;
            }

            ServerPlayer target = sender.server.getPlayerList().getPlayerByName(targetPlayerName);
            if (target == null) {
                sender.sendMessage(new TextComponent("\u00a76[Filter Library] \u00a7rPlayer \u00a7e'" + targetPlayerName + "'\u00a7r is not online."), sender.getUUID());
                return;
            }

            if (target.getUUID().equals(sender.getUUID())) {
                sender.sendMessage(new TextComponent("\u00a76[Filter Library] \u00a7rYou cannot share a filter with yourself."), sender.getUUID());
                return;
            }

            String senderName = sender.getDisplayName().getString();
            VFMessages.VFCHANNEL.send(PacketDistributor.PLAYER.with(() -> target), new ShareS2CPacket(senderName, filterJson));

            sender.sendMessage(new TextComponent("\u00a76[Filter Library] \u00a7rSent filter to \u00a7e" + targetPlayerName), sender.getUUID());
        });
        context.setPacketHandled(true);
    }

    private static class ShareTracker {
        private static final Map<UUID, Long> lastShareTime = new HashMap<>();

        static synchronized boolean tryShare(UUID playerId) {
            long now = System.currentTimeMillis();
            long last = lastShareTime.getOrDefault(playerId, 0L);
            int maxPerMin = VFServerConfig.MAX_SHARES_PER_MINUTE.get();
            long minInterval = 60000L / Math.max(1, maxPerMin);
            if (now - last < minInterval) {
                return false;
            }
            lastShareTime.put(playerId, now);
            return true;
        }
    }
}
