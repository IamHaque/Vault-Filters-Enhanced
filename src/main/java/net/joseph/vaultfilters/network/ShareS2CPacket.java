package net.joseph.vaultfilters.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.joseph.vaultfilters.VaultFilters;
import net.joseph.vaultfilters.library.FilterLibraryStore;
import net.joseph.vaultfilters.library.SavedFilter;
import net.joseph.vaultfilters.library.SavedFilterType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShareS2CPacket {
    private final String senderName;
    private final String filterJson;

    public ShareS2CPacket(String senderName, String filterJson) {
        this.senderName = senderName;
        this.filterJson = filterJson;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(senderName, 16);
        buf.writeUtf(filterJson, VFMessages.MAX_SHARE_SIZE);
    }

    public static ShareS2CPacket decode(FriendlyByteBuf buf) {
        return new ShareS2CPacket(buf.readUtf(16), buf.readUtf(VFMessages.MAX_SHARE_SIZE));
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                handleClient();
            }
        });
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private void handleClient() {
        try {
            JsonObject obj = JsonParser.parseString(filterJson).getAsJsonObject();
            if (!obj.has("payload") || !obj.get("payload").isJsonObject()) {
                VaultFilters.LOGGER.warn("ShareS2C: received filter without payload");
                return;
            }

            String typeStr = obj.get("type").getAsString();
            SavedFilterType type = SavedFilterType.fromJsonId(typeStr);
            if (type == null) {
                VaultFilters.LOGGER.warn("ShareS2C: unknown filter type: {}", typeStr);
                return;
            }

            String name = obj.has("name") ? obj.get("name").getAsString() : "Shared Filter";
            if (name.length() > 35) name = name.substring(0, 35);

            String resolvedName = resolveName(type, name);

            if (!FilterLibraryStore.canAddMore()) {
                Minecraft.getInstance().player.sendMessage(
                        new TextComponent("\u00a76[Filter Library] \u00a7rCannot import shared filter \"" + resolvedName + "\": library is full"),
                        Minecraft.getInstance().player.getUUID());
                return;
            }

            JsonObject payload = obj.getAsJsonObject("payload");
            SavedFilter filter = SavedFilter.createNew(type, resolvedName, payload);
            FilterLibraryStore.upsert(filter);

            Minecraft.getInstance().player.sendMessage(
                    new TextComponent("\u00a76[Filter Library] \u00a7r" + senderName + " shared a filter \u00a7e'" + resolvedName + "'\u00a7r. Imported successfully."),
                    Minecraft.getInstance().player.getUUID());
        } catch (Exception e) {
            VaultFilters.LOGGER.warn("ShareS2C: failed to import shared filter: {}", e.getMessage());
        }
    }

    private String resolveName(SavedFilterType type, String name) {
        if (!FilterLibraryStore.existsByName(type, name)) return name;

        String suffix = " (from " + senderName + ")";
        int maxLen = 35;
        String result;
        if (name.length() + suffix.length() <= maxLen) {
            result = name + suffix;
        } else {
            result = name.substring(0, maxLen - suffix.length()) + suffix;
        }

        if (!FilterLibraryStore.existsByName(type, result)) return result;

        int counter = 2;
        while (true) {
            String s = " (from " + senderName + " #" + counter + ")";
            String candidate;
            if (name.length() + s.length() <= maxLen) {
                candidate = name + s;
            } else {
                candidate = name.substring(0, maxLen - s.length()) + s;
            }
            if (!FilterLibraryStore.existsByName(type, candidate)) return candidate;
            counter++;
        }
    }
}
