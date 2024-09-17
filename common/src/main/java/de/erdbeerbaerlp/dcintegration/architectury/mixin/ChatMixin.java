package de.erdbeerbaerlp.dcintegration.architectury.mixin;

import de.erdbeerbaerlp.dcintegration.architectury.DiscordIntegrationMod;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

@Mixin(value= ServerGamePacketListenerImpl.class)
public class ChatMixin {

    // Keep record of messages in memory
    private List<SimpleChatMessage> lastPlayerMessages = new ArrayList<>();

    /**
     * Handle chat messages
     */
    @Redirect(method = "broadcastChatMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V"))
    public void chatMessage(PlayerList instance, PlayerChatMessage signedMessage, ServerPlayer sender, ChatType.Bound bound) {
        if (// TODO: insert check against config to enable/disable checking for duplicates &&
            ignoreDuplicateMessagesDaily(signedMessage, sender)) {
            // ignore message if duplicate is found
            return;
        }
        signedMessage = DiscordIntegrationMod.handleChatMessage(signedMessage, sender);
        instance.broadcastChatMessage(signedMessage, sender, bound);
    }

    private boolean ignoreDuplicateMessagesDaily(PlayerChatMessage signedMessage, ServerPlayer sender) {
        SimpleChatMessage msg = new SimpleChatMessage(signedMessage, sender);

        if (foundMatchingChat(msg)) {
            // A message was already sent today
            return true;
        }

        // While we're at it, let's clean up any older messages
        // (Due to foundMatchingChat's logic, messages will only be cleaned
        // when the first message of the day is sent)
        // before adding any new ones...
        // just so that there are for sure no accidental removals.
        lastPlayerMessages.removeIf(element -> !element.matchesToday());

        // This is the first unique instance of this message today,
        // so we will go ahead and add it to the list.
        lastPlayerMessages.add(msg);
        return false;
    }

    private boolean foundMatchingChat(SimpleChatMessage msg) {
        // Filter potential duplicates to check
        List<SimpleChatMessage> filteredChats = lastPlayerMessages
            .stream()
            // existing date matches today
            .filter(element -> element.matchesToday())
            // incoming playerID matches existing
            .filter(element -> element.matchesPlayerID(msg))
            // incoming message matches existing
            .filter(element -> element.matchesMessage(msg))
            .toList();

        return !filteredChats.isEmpty();
    }

    private class SimpleChatMessage {
        // properties
        private String playerID = new String();
        private String message = new String();
        private LocalDate date = LocalDate.now();

        // constructor
        public SimpleChatMessage(PlayerChatMessage signedMessage, ServerPlayer sender) {
            playerID = sender.getTabListDisplayName().toString();
            message = signedMessage.signedContent();
        }

        // accessors
        public String getPlayerID() { return playerID; }
        public String getMessage() { return message; }

        // match helpers
        public boolean matchesPlayerID(SimpleChatMessage msg) {
            return playerID == msg.getPlayerID();
        }

        public boolean matchesToday() {
            return LocalDate.now().isEqual(date);
        }

        public boolean matchesMessage(SimpleChatMessage msg) {
            return message == msg.getMessage();
        }
    }
}
