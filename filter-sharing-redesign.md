# Vault Filters Enhanced — Player-to-Player Sharing Redesign (Client-Only)

**Branch:** `V2`
**Audience:** implementing coding agent (no prior repo context assumed beyond this doc + the repo itself)
**Status:** approved design, ready to implement

---

## 0. Why the existing feature is being replaced, not patched

The current implementation (`network/ShareC2SPacket.java`, `network/ShareS2CPacket.java`) relays a
filter between two players **through the server**: sender's client → custom C2S packet → server
handler → custom S2C packet → recipient's client.

This cannot work in this project's real deployment model, for a reason no amount of bug-fixing
inside those two files can address:

- The relay logic (`ShareC2SPacket.handle()`, which looks up the target player and forwards the
  packet) **must execute on the server**. The person deploying this mod does not own/administer the
  server the pack runs on, and cannot install a build containing this handler there.
- Forge's custom network channels are a private contract between a mod's client and server copies.
  If the actual server the pack runs on doesn't have a build with these two packet types
  registered, sending them does nothing observable: the client-side "Sent filter to X" message
  still fires (it's unconditional — see below), and the packet is dropped somewhere in the stack
  with no error visible to either player. This is consistent with the reported symptom ("send
  message shows, recipient gets nothing") independent of any other bug.
- Confirmed from the code: `VFMessages.java` registers `ShareC2SPacket`/`ShareS2CPacket` **last**
  (packet IDs 2 and 3, after the two pre-existing packets), so at least this particular mismatch
  wouldn't have corrupted the IDs of other, already-working features — but it does mean these two
  packet types simply have no counterpart on a server that doesn't run this exact fork.
- Separately, and worth fixing regardless: the old code also has a false-positive confirmation
  (the sender is told "Sent" the instant the packet is queued, not when/if it's delivered) and a
  silent failure path on the recipient's side (`catch (Exception e) { LOGGER.warn(...) }` with no
  chat feedback). These were real defects, but they are moot once the whole transport is replaced.

**Conclusion:** sharing must not depend on any packet type the actual game server needs to
recognize. The only channel guaranteed to be relayed by _any_ vanilla or modded server, without
requiring that server to run this mod, is **chat** (public chat or the vanilla `/tell`/`/msg`/`/w`
whisper command). This spec replaces the custom-packet relay with a chat-relay transport.

---

## 1. Goals / Non-goals

**Goals**

- Let a player send a saved filter (attribute or list) to another _online_ player, in-game, without
  needing anything installed or configured on the server.
- Give both sides accurate, honest feedback: sender knows whether it was delivered and accepted;
  recipient gets an explicit accept/decline prompt (not silent auto-import).
- Keep working for filters within a reasonable size; gracefully point to the _already-existing_
  clipboard Export/Import feature for anything too large to chunk through chat.
- Don't get the sending player's account flagged/kicked for chat spam.

**Non-goals**

- No attempt to guarantee delivery to a player who doesn't have this mod installed — that remains
  physically impossible without server cooperation, same as before. The goal here is that failure
  is _reported_, not silent.
- No changes anywhere that require the actual game server to run any part of this mod's code.

---

## 2. Critical assumption to verify FIRST, before writing any other code

This entire design depends on vanilla `/tell` (aliased `/msg`, `/w`) successfully delivering a
message end-to-end between two players on the actual server this is used on. Some modpacks run a
party/chat-management system that intercepts, restricts, or disables player whispers.

**Before implementing anything else:** have two accounts on the real target server run
`/tell OtherPlayerName test123` and confirm the _exact recipient_ sees the literal text `test123`
(not a reformatted/truncated/blocked version of it). If whispers are blocked or altered, switch the
config default in §7 from `WHISPER` to `PUBLIC` (plain public chat) — the rest of the design is
identical either way, since detection is done by scanning the flattened chat text for our own
marker, not by relying on `/tell`'s specific formatting.

---

## 3. Architecture

```
Sender client                                      Recipient client
     │                                                    │
     │ user clicks "Share" → picks target → confirms      │
     │                                                    │
     │  encode filter JSON → base64 → split into chunks   │
     │  each chunk = one chat message:                    │
     │  "##VFS##<sessionId>#<idx>#<total>#<sender>#<data>" │
     │                                                    │
     │  queue chunks, send one every N ticks via           │
     │  /tell <target> <chunk>   (or public chat)          │
     ├─────────────────────────────────────────────────►  │ ClientChatReceivedEvent fires per chunk
     │                                                    │ → matched via marker in flattened text
     │                                                    │ → event cancelled (never shown raw)
     │                                                    │ → buffered by (senderName, sessionId)
     │                                                    │
     │                                                    │ once all chunks present:
     │                                                    │ reassemble → base64 decode → parse JSON
     │                                                    │ → validate → show ConfirmScreen
     │                                                    │   "X wants to share filter 'Name'. Import?"
     │                                                    │ → on Yes: FilterLibraryStore.upsert(...)
     │                                                    │ → chat message with final result
     │                                                    │
     │  local progress feedback only (no ack from         │
     │  recipient — see §9, this is a deliberate,          │
     │  documented limitation of a serverless transport)   │
```

No custom network channel, no server-side code, at all. Everything above happens entirely inside
the two clients using vanilla chat as the carrier.

---

## 4. Wire format (the chat message protocol)

Each chat message the sender emits looks like:

```
##VFS##<sessionId>#<index>#<total>#<senderName>#<data>
```

- `##VFS##` — fixed marker. Chosen to be extremely unlikely to occur in normal chat.
- `sessionId` — 5 random base36 chars (`A-Z0-9`), generated fresh per share. Scopes chunk buffering
  so two concurrent shares (even from the same sender) don't collide.
- `index` — 1-based chunk number.
- `total` — total chunk count for this session.
- `senderName` — the sending player's display name. **Only populated on the chunk with `index == 1`**
  (other chunks send an empty field) to save per-message character budget. The receiver captures it
  once, from whichever chunk arrives first with `index == 1`.
- `data` — a slice of the full base64-encoded JSON payload. Chunks are sliced from the _already
  fully base64-encoded_ string (not encoded independently per chunk), so reassembly is just
  string concatenation in index order followed by a single base64 decode at the end. This is
  simpler and more robust than trying to keep each chunk independently valid base64.

**Detection on receive:** do not attempt to parse vanilla's `/tell` formatting. Take the fully
flattened chat text (`component.getString()`), search for the literal substring `##VFS##` anywhere
within it, and parse from that point forward. This makes detection completely independent of
whatever text vanilla/the server wraps around the whisper (`"X whispers to you: ..."` etc.), which
is the safest possible approach given we can't test against the pack's exact server behavior.

**Size budget:** vanilla caps a single chat message at 256 characters. Budget for `/tell` delivery:

| Component                                    | Chars (worst case) |
| -------------------------------------------- | ------------------ |
| `/tell ` command prefix                      | 6                  |
| target player name                           | 16                 |
| space                                        | 1                  |
| `##VFS##` marker                             | 7                  |
| sessionId                                    | 5                  |
| `#idx#total#` separators + digits            | ~9                 |
| senderName field (chunk 1 only) + separators | ~18                |
| **overhead subtotal**                        | **~62**            |
| **remaining budget for `data`**              | **~194**           |

Set `CHUNK_CHAR_SIZE = 160` (base64 chars of payload per chunk) to leave real margin. This is a
constant in `ShareCodec`, not hardcoded inline, so it can be tuned later without touching logic.

---

## 5. Chat-spam risk — this is the part most likely to bite in production

Vanilla Minecraft servers penalize rapid consecutive chat messages from one player and will
**disconnect them** ("kicked for spamming") past a threshold. Sending 10–30 chunk messages back to
back would very likely trip this on an unmodified vanilla threshold.

**Mitigation:** never send more than one chunk per fixed number of client ticks (a simple queue
drained from a `ClientTickEvent` handler, not `Thread.sleep`, which would freeze the game). Default
to **1 chunk every 30 ticks (~1.5s)**, configurable (see §7). For a filter needing the maximum
allowed 30-ish chunks, a share takes under a minute — acceptable for a background action with
visible progress feedback. This value is a starting point; if testing against the real server shows
messages still get flagged, increase it.

---

## 6. Payload size cap and fallback to existing Export/Import

Cap the feature to filters whose compact JSON is **≤ 6000 bytes** (roughly 35 chunks at the pacing
above, ~50 seconds worst case). Above that, refuse to start the chat transfer and tell the user to
use the existing **Export** button (copies JSON to clipboard, no size limit, already implemented and
untouched by this change) and send the clipboard text to their friend through Discord/whatever they
already use, for the friend to **Import**. This keeps the new feature's complexity and spam risk
bounded, and there is already a fully-working unlimited-size path for outliers.

---

## 7. New client-side config

Add `configs/VFClientConfig.java`, mirroring the existing `VFServerConfig.java` pattern but
registered as `ModConfig.Type.CLIENT` (the only config type the sharer can actually control on
their own install — `VFServerConfig` requires the _server_ to load it, which is exactly the
constraint we're designing around, so nothing about sharing should ever go in `VFServerConfig`
again).

| Key                         | Default   | Purpose                                                                                                                                                                  |
| --------------------------- | --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `Max Share Bytes`           | 6000      | Payload cap before falling back to Export/Import (§6)                                                                                                                    |
| `Chunk Delay Ticks`         | 30        | Ticks between outgoing chunk messages (§5)                                                                                                                               |
| `Delivery Mode`             | `WHISPER` | `WHISPER` (`/tell`) or `PUBLIC` (plain chat) — flip if §2's test shows whispers don't work                                                                               |
| `Accept Shares From Others` | `true`    | Master on/off switch; if false, incoming share chunks are ignored entirely (still consumed/cancelled so they don't spam the player's chat, just never buffered/prompted) |

Register in `VaultFilters.java`'s constructor alongside the existing server config registration:

```java
ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, VFClientConfig.SPEC, "vaultfilters-client.toml");
```

---

## 8. Files

### New package: `net.joseph.vaultfilters.share`

#### `ShareCodec.java` — pure logic, zero Minecraft/Forge dependency, fully unit-testable

```java
package net.joseph.vaultfilters.share;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Pure encode/decode logic for splitting a filter payload into vanilla-chat-safe
 * chunks and reassembling them. No Minecraft/Forge dependency — unit test this
 * directly with plain JUnit, no game environment needed.
 */
public final class ShareCodec {

    public static final String MARKER = "##VFS##";
    public static final int CHUNK_CHAR_SIZE = 160;
    private static final String SESSION_ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SESSION_ID_LENGTH = 5;
    private static final int MAX_TOTAL_CHUNKS = 500; // sanity ceiling, well above the real cap in §6
    private static final SecureRandom RANDOM = new SecureRandom();

    private ShareCodec() {}

    public static String newSessionId() {
        StringBuilder sb = new StringBuilder(SESSION_ID_LENGTH);
        for (int i = 0; i < SESSION_ID_LENGTH; i++) {
            sb.append(SESSION_ID_ALPHABET.charAt(RANDOM.nextInt(SESSION_ID_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Splits {@code json} into ordered, independently-sendable chat messages. */
    public static List<String> encode(String json, String sessionId, String senderName) {
        byte[] raw = json.getBytes(StandardCharsets.UTF_8);
        String base64 = Base64.getEncoder().encodeToString(raw);

        int total = Math.max(1, (int) Math.ceil(base64.length() / (double) CHUNK_CHAR_SIZE));

        List<String> messages = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            int start = i * CHUNK_CHAR_SIZE;
            int end = Math.min(start + CHUNK_CHAR_SIZE, base64.length());
            String chunk = base64.substring(start, end);
            String senderField = (i == 0) ? senderName : "";
            messages.add(MARKER + sessionId + "#" + (i + 1) + "#" + total + "#" + senderField + "#" + chunk);
        }
        return messages;
    }

    /** Rough chunk-count estimate, for pre-flight size checks / progress display. */
    public static int estimateChunkCount(int jsonByteLength) {
        int base64Length = (int) Math.ceil(jsonByteLength / 3.0) * 4;
        return Math.max(1, (int) Math.ceil(base64Length / (double) CHUNK_CHAR_SIZE));
    }

    public record ParsedChunk(String sessionId, int index, int total, String senderName, String data) {}

    /** Returns null if {@code flattenedChatText} contains no recognizable, well-formed chunk. */
    public static ParsedChunk tryParseChunk(String flattenedChatText) {
        int markerIdx = flattenedChatText.indexOf(MARKER);
        if (markerIdx < 0) return null;

        String rest = flattenedChatText.substring(markerIdx + MARKER.length());
        String[] parts = rest.split("#", 5);
        if (parts.length != 5) return null;

        String sessionId = parts[0];
        if (sessionId.length() != SESSION_ID_LENGTH) return null;

        int index, total;
        try {
            index = Integer.parseInt(parts[1]);
            total = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (index < 1 || total < 1 || index > total || total > MAX_TOTAL_CHUNKS) return null;

        return new ParsedChunk(sessionId, index, total, parts[3], parts[4]);
    }

    /** Decodes a fully-reassembled (in-order-concatenated) base64 string back to JSON text. */
    public static String decode(String fullBase64) {
        byte[] raw = Base64.getDecoder().decode(fullBase64);
        return new String(raw, StandardCharsets.UTF_8);
    }
}
```

#### `ShareReassembler.java` — pure logic, per-session chunk buffer

```java
package net.joseph.vaultfilters.share;

import java.util.Map;
import java.util.TreeMap;

/** Buffers chunks for one in-progress inbound transfer. Pure logic, unit-testable. */
public final class ShareReassembler {

    public enum Status { INCOMPLETE, COMPLETE, MISMATCHED }

    private final Map<Integer, String> chunks = new TreeMap<>();
    private int declaredTotal = -1;
    private String senderName = null;
    public final long firstSeenAtMillis;

    public ShareReassembler(long nowMillis) {
        this.firstSeenAtMillis = nowMillis;
    }

    public Status add(ShareCodec.ParsedChunk chunk) {
        if (declaredTotal == -1) {
            declaredTotal = chunk.total();
        } else if (declaredTotal != chunk.total()) {
            return Status.MISMATCHED;
        }
        if (!chunk.senderName().isEmpty()) {
            senderName = chunk.senderName();
        }
        chunks.put(chunk.index(), chunk.data());
        return chunks.size() >= declaredTotal ? Status.COMPLETE : Status.INCOMPLETE;
    }

    public String senderName() {
        return senderName == null ? "Unknown" : senderName;
    }

    public String reassembleBase64() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= declaredTotal; i++) {
            String part = chunks.get(i);
            if (part == null) throw new IllegalStateException("Missing chunk " + i + " of " + declaredTotal);
            sb.append(part);
        }
        return sb.toString();
    }
}
```

#### `ChatShareSender.java` — client-side, drives outgoing chunk queue off the tick event

```java
package net.joseph.vaultfilters.share;

import net.joseph.vaultfilters.configs.VFClientConfig;
import net.joseph.vaultfilters.library.SavedFilter;
import net.joseph.vaultfilters.util.FilterUiUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class ChatShareSender {

    private static final Deque<String> outbox = new ArrayDeque<>();
    private static String pendingTargetName;
    private static int tickCounter = 0;
    private static Runnable onDone; // called once the last chunk has been sent

    private ChatShareSender() {}

    public static boolean isBusy() {
        return !outbox.isEmpty();
    }

    public enum StartResult { STARTED, TOO_LARGE, ALREADY_BUSY }

    public static StartResult beginShare(SavedFilter filter, String targetName, Runnable onDoneCallback) {
        if (isBusy()) return StartResult.ALREADY_BUSY;

        String json = FilterUiUtils.GSON.toJson(filter.toEntryJson());
        int byteLength = json.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength > VFClientConfig.MAX_SHARE_BYTES.get()) {
            return StartResult.TOO_LARGE;
        }

        String senderName = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getGameProfile().getName() : "Unknown";
        String sessionId = ShareCodec.newSessionId();
        List<String> messages = ShareCodec.encode(json, sessionId, senderName);

        outbox.clear();
        outbox.addAll(messages);
        pendingTargetName = targetName;
        tickCounter = VFClientConfig.CHUNK_DELAY_TICKS.get(); // send first chunk almost immediately
        onDone = onDoneCallback;
        return StartResult.STARTED;
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || outbox.isEmpty()) return;
        if (++tickCounter < VFClientConfig.CHUNK_DELAY_TICKS.get()) return;
        tickCounter = 0;

        String next = outbox.poll();
        sendChunk(pendingTargetName, next);

        if (outbox.isEmpty() && onDone != null) {
            Runnable callback = onDone;
            onDone = null;
            callback.run();
        }
    }

    private static void sendChunk(String targetName, String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return;

        // NOTE (verify against local mapped sources — see §11): in 1.18.2,
        // ClientPacketListener exposes sendChat(String) for plain chat and
        // sendCommand(String) for a command WITHOUT the leading slash.
        if (VFClientConfig.DELIVERY_MODE.get() == VFClientConfig.DeliveryMode.PUBLIC) {
            mc.player.connection.sendChat(message);
        } else {
            mc.player.connection.sendCommand("tell " + targetName + " " + message);
        }
    }
}
```

#### `ChatShareReceiver.java` — client-side, listens for chunks, reassembles, prompts

```java
package net.joseph.vaultfilters.share;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.joseph.vaultfilters.VaultFilters;
import net.joseph.vaultfilters.configs.VFClientConfig;
import net.joseph.vaultfilters.library.FilterLibraryStore;
import net.joseph.vaultfilters.library.SavedFilter;
import net.joseph.vaultfilters.library.SavedFilterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
// NOTE (verify against local mapped sources — see §11): package/class name and
// exact accessors for the client chat-received event in this Forge version.
import net.minecraftforge.client.event.ClientChatReceivedEvent;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class ChatShareReceiver {

    private static final long SESSION_TIMEOUT_MS = 30_000;
    private static final int MAX_CONCURRENT_SESSIONS = 5; // bound memory/spam from a hostile flood

    private record Key(String senderName, String sessionId) {}
    private static final Map<Key, ShareReassembler> sessions = new LinkedHashMap<>();

    private ChatShareReceiver() {}

    public static void onChatReceived(ClientChatReceivedEvent event) {
        String text = event.getMessage().getString(); // flattened plain text — robust to formatting
        ShareCodec.ParsedChunk chunk = ShareCodec.tryParseChunk(text);
        if (chunk == null) return;

        // Always cancel: never let a raw chunk line show in the recipient's chat,
        // regardless of whether sharing is enabled — a disabled toggle should look
        // like nothing happened, not spam the chat with garbled lines.
        event.setCanceled(true);

        if (!VFClientConfig.ACCEPT_SHARES.get()) return;

        Key preliminaryKey = new Key(chunk.senderName().isEmpty() ? "?" : chunk.senderName(), chunk.sessionId());
        // Chunk 1 carries the real sender name; later chunks may arrive with an
        // empty name field, so look up any existing session for this sessionId
        // regardless of the name we currently have for it.
        Key key = findExistingKey(chunk.sessionId()).orElse(preliminaryKey);

        if (!sessions.containsKey(key) && sessions.size() >= MAX_CONCURRENT_SESSIONS) {
            return; // drop silently; prevents unbounded growth from a malicious flood of sessionIds
        }

        ShareReassembler reassembler = sessions.computeIfAbsent(key, k -> new ShareReassembler(System.currentTimeMillis()));
        ShareReassembler.Status status = reassembler.add(chunk);

        if (status == ShareReassembler.Status.MISMATCHED) {
            sessions.remove(key);
            return;
        }
        if (status != ShareReassembler.Status.COMPLETE) return;

        sessions.remove(key);
        handleComplete(reassembler);
    }

    private static java.util.Optional<Key> findExistingKey(String sessionId) {
        return sessions.keySet().stream().filter(k -> k.sessionId().equals(sessionId)).findFirst();
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || sessions.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Key, ShareReassembler>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().firstSeenAtMillis > SESSION_TIMEOUT_MS) {
                it.remove();
            }
        }
    }

    private static void handleComplete(ShareReassembler reassembler) {
        String senderName = reassembler.senderName();
        JsonObject obj;
        try {
            String json = ShareCodec.decode(reassembler.reassembleBase64());
            obj = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            VaultFilters.LOGGER.warn("ChatShare: failed to decode share from {}: {}", senderName, e.getMessage());
            notify("vaultfilters.gui.library.share.received.parse_error", senderName);
            return;
        }

        if (!obj.has("payload") || !obj.get("payload").isJsonObject()) {
            notify("vaultfilters.gui.library.share.received.no_payload", senderName);
            return;
        }
        SavedFilterType type = SavedFilterType.fromJsonId(obj.has("type") ? obj.get("type").getAsString() : "");
        if (type == null) {
            notify("vaultfilters.gui.library.share.received.unknown_type", senderName);
            return;
        }

        String name = obj.has("name") ? obj.get("name").getAsString() : "Shared Filter";
        if (name.length() > 35) name = name.substring(0, 35);
        JsonObject payload = obj.getAsJsonObject("payload");

        promptAccept(senderName, name, type, payload);
    }

    private static void promptAccept(String senderName, String name, SavedFilterType type, JsonObject payload) {
        Minecraft mc = Minecraft.getInstance();
        Screen previous = mc.screen;
        mc.setScreen(new ConfirmScreen(
                accepted -> {
                    mc.setScreen(previous);
                    if (!accepted) return;
                    if (!FilterLibraryStore.canAddMore()) {
                        notify("vaultfilters.gui.library.share.received.full", senderName);
                        return;
                    }
                    String resolvedName = resolveName(type, name);
                    SavedFilter filter = SavedFilter.createNew(type, resolvedName, payload);
                    FilterLibraryStore.upsert(filter);
                    notify("vaultfilters.gui.library.share.received.success", senderName, resolvedName);
                },
                new TranslatableComponent("vaultfilters.gui.library.share.prompt.title"),
                new TranslatableComponent("vaultfilters.gui.library.share.prompt.message", senderName, name)));
    }

    /** Same dedupe intent as the old ShareS2CPacket.resolveName, with a hard iteration cap
     *  (the old version's while(true) loop had no bound — closing that off here). */
    private static String resolveName(SavedFilterType type, String baseName) {
        if (FilterLibraryStore.findByName(type, baseName) == null) return baseName;
        for (int i = 2; i <= 200; i++) {
            String suffix = " (" + i + ")";
            int keep = Math.max(0, 35 - suffix.length());
            String candidate = baseName.substring(0, Math.min(keep, baseName.length())) + suffix;
            if (FilterLibraryStore.findByName(type, candidate) == null) return candidate;
        }
        return baseName.substring(0, Math.min(28, baseName.length())) + " (" + ShareCodec.newSessionId().substring(0, 4) + ")";
    }

    private static void notify(String key, Object... args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendMessage(new TranslatableComponent(key, args), mc.player.getUUID());
    }
}
```

### Changed files

- **`configs/VFClientConfig.java`** _(new)_ — client config as described in §7. Model it directly on
  `VFServerConfig.java`'s structure (`ForgeConfigSpec.Builder`, `defineInRange`/`defineEnum` etc.),
  just registered as `ModConfig.Type.CLIENT`.

- **`VaultFilters.java`**:
  - Add `ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, VFClientConfig.SPEC, "vaultfilters-client.toml");` in the constructor, next to the existing server config line.
  - In `onClientSetup`, register the two new tick/chat listeners:
    ```java
    MinecraftForge.EVENT_BUS.addListener(ChatShareSender::onClientTick);
    MinecraftForge.EVENT_BUS.addListener(ChatShareReceiver::onClientTick);
    MinecraftForge.EVENT_BUS.addListener(ChatShareReceiver::onChatReceived);
    ```
  - Remove the `VFMessages.register()` call **only if** you also do the removal below — otherwise
    leave it (it still registers `MenuFeaturesPacket`/`NestedFilterPacket`, which are unaffected by
    this change and presumably already work against the real server).

- **`network/VFMessages.java`** — delete the two `registerMessage(...)` calls for
  `ShareC2SPacket`/`ShareS2CPacket` (keep everything else). Delete the now-unused
  `MAX_SHARE_SIZE` constant.

- **Delete** `network/ShareC2SPacket.java` and `network/ShareS2CPacket.java` entirely (this also
  removes the nested `ShareTracker` rate limiter, which was server-side rate limiting that can't be
  deployed anyway — replaced by the client-side `MAX_CONCURRENT_SESSIONS` bound in
  `ChatShareReceiver` and the pacing in `ChatShareSender`).

- **`configs/VFServerConfig.java`** — remove `MAX_SHARE_BYTES` / `MAX_SHARES_PER_MINUTE`. These were
  only ever consulted by the deleted server-side handler and required the server to load this mod's
  config, which was never actually achievable given the ownership constraint.

- **`client/gui/FilterLibraryScreen.java`** (`confirmShare()` / share modal) — replace the body that
  built and sent `ShareC2SPacket` with a call into `ChatShareSender.beginShare(...)`:

  ```java
  SavedFilter sel = selectedFilter();
  if (sel == null) { cleanupShare(); setStatus("No filter selected"); return; }
  ChatShareSender.StartResult result = ChatShareSender.beginShare(sel, targetName, () ->
          setStatus("Finished sending \"" + sel.name() + "\" to " + targetName));
  switch (result) {
      case STARTED -> setStatus("Sending \"" + sel.name() + "\" to " + targetName + "...");
      case TOO_LARGE -> setStatus("Too large to share directly — use Export, then send the "
              + "copied text to " + targetName + " to Import instead.");
      case ALREADY_BUSY -> setStatus("A share is already in progress, wait for it to finish.");
  }
  cleanupShare();
  ```

  The existing player-name autocomplete (`updateShareSuggestions()` reading
  `Minecraft.getInstance().player.connection.getOnlinePlayers()`) is untouched — it's a pure vanilla
  client API and never depended on the custom packets.

- **`en_us.json`** — add (matching the existing `%1$s`-numbered-placeholder convention seen at
  `vaultfilters.gui.library.overwrite.message`):
  ```json
  "vaultfilters.gui.library.share.prompt.title": "Filter Share",
  "vaultfilters.gui.library.share.prompt.message": "%1$s wants to share a filter named \"%2$s\" with you. Import it?",
  "vaultfilters.gui.library.share.received.success": "%1$s shared a filter '%2$s' with you. Imported successfully.",
  "vaultfilters.gui.library.share.received.full": "%1$s tried to share a filter with you, but your library is full.",
  "vaultfilters.gui.library.share.received.no_payload": "%1$s tried to share a filter with you, but it was missing data.",
  "vaultfilters.gui.library.share.received.unknown_type": "%1$s tried to share a filter with you, but its type wasn't recognized.",
  "vaultfilters.gui.library.share.received.parse_error": "%1$s tried to share a filter with you, but it couldn't be read."
  ```

---

## 9. What this design deliberately does NOT do, and why

- **No delivery/import acknowledgment back to the sender.** A true ack would itself need to travel
  recipient → sender, which means _another_ chat-relay round trip. That's a reasonable Phase 2 if
  wanted, but adds real complexity (another marker protocol, another timeout) for a benefit that's
  mostly cosmetic — the sender already sees "Finished sending" once their queue drains, which is
  honest about what actually happened (all chunks left the sender's client), without claiming
  something the sender's client has no way to verify (whether the recipient's client is even
  running, has the toggle disabled, or declines the prompt). Documented here explicitly so it isn't
  mistaken for an oversight.
- **No silent auto-import.** Every incoming, fully-reassembled share requires an explicit
  `ConfirmScreen` accept — this also fixes a gap flagged in the earlier server-relay design (any
  online player who knows your name could previously push filters into your library unattended).
- **No unlimited payload size.** Bounded by chat message length and spam-limit pacing; anything
  bigger is explicitly pointed at the existing, unlimited clipboard Export/Import path.

---

## 10. Testing plan

**Fully unit-testable without Minecraft** (mirror the existing `FilterLibraryStoreTest.java` /
`FilterPayloadUtilsTest.java` style, same `src/test/java` tree):

- `ShareCodec`: encode → concatenate all chunks' `data` fields in order → decode → equals original
  JSON, for payload sizes spanning 1 chunk, exactly `CHUNK_CHAR_SIZE` boundary, and multi-chunk.
- `ShareCodec.tryParseChunk`: valid chunk parses correctly; text with no marker returns null; marker
  present but malformed fields (non-numeric index, index > total, missing fields) returns null and
  never throws.
- `ShareReassembler`: out-of-order chunk insertion still reassembles correctly; a chunk with a
  mismatched `total` from the same session returns `MISMATCHED`; sender name captured correctly
  when chunk 1 arrives last (not just first).

**Manual, in-game QA checklist** (can't be automated without a running game + real server):

1. §2's raw `/tell` verification, on the real target server, before anything else.
2. Two real clients, small filter (1–2 chunks): full send → prompt → accept → appears in library.
3. Same, but Decline on the prompt: nothing added, no error.
4. A filter sized to need ~10+ chunks: confirm no spam-kick occurs at the default pacing; confirm
   full, correct reassembly.
5. A filter just over the `Max Share Bytes` cap: confirm the "too large, use Export" message and
   that no chunks are sent.
6. Recipient has `Accept Shares From Others` set to false: confirm chunks are silently discarded
   (nothing shown, nothing imported) with no chat spam.
7. Recipient's library already at 500/500: confirm the `LIBRARY_FULL` message appears after
   accepting the prompt (the cap can only be checked after decode, so the prompt itself still
   shows — that's expected, decline path still works too).
8. Sender tries to start a second share while one is already in flight: confirm the "already in
   progress" message and that chunks aren't interleaved/corrupted between the two sessions.

---

## 11. Open items for the implementing agent to verify (no Forge/Mojang Maven access was

     available while writing this spec, so these could not be compile-checked)

1. **`net.minecraftforge.client.event.ClientChatReceivedEvent`** — confirm this is the correct
   package/class for this Forge version (per `build.gradle`), and confirm `getMessage()` returns a
   `Component` (flattenable via `.getString()`) and that the event is cancelable
   (`event.setCanceled(true)`). If the exact class/method names differ, adjust
   `ChatShareReceiver.onChatReceived` accordingly — the surrounding logic doesn't depend on any other
   details of this event.
2. **`ClientPacketListener#sendChat(String)` / `#sendCommand(String)`** — confirm these exact method
   names on the mapped (Parchment, per `build.gradle`) sources for this Minecraft version. These are
   the two calls in `ChatShareSender.sendChunk`.
3. Confirm vanilla's actual chat-spam threshold/kick behavior on the target server (§5) empirically,
   and tune `Chunk Delay Ticks`'s default accordingly if needed.
4. Confirm the 256-character vanilla chat cap still applies as assumed in §4's budget table for
   this specific server (some server-side chat mods raise or ignore it — if so, `CHUNK_CHAR_SIZE` can
   be raised to reduce chunk counts, but don't assume this without checking).

Everything else in this spec (`ConfirmScreen` constructor shape, `TickEvent.ClientTickEvent`,
`TranslatableComponent`, `ForgeConfigSpec` patterns, `FilterLibraryStore`/`SavedFilter` APIs) is
modeled directly on code that already compiles and runs elsewhere in this repository, so should not
need adjustment.
