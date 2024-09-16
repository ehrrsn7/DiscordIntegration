package de.erdbeerbaerlp.dcintegration.architectury.mixin;

import com.mojang.authlib.GameProfile;
import dcshadow.net.kyori.adventure.text.Component;
import dcshadow.net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import de.erdbeerbaerlp.dcintegration.architectury.util.ArchitecturyMessageUtils;
import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.WorkThread;
import de.erdbeerbaerlp.dcintegration.common.compat.FloodgateUtils;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.linking.LinkManager;
import de.erdbeerbaerlp.dcintegration.common.util.DiscordMessage;
import de.erdbeerbaerlp.dcintegration.common.util.TextColors;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;
import java.util.UUID;

import static de.erdbeerbaerlp.dcintegration.common.DiscordIntegration.INSTANCE;

@Mixin(PlayerList.class)
public class PlayerManagerMixin {

    /**
     * Handle whitelisting
     */
    @Inject(method = "canPlayerLogin", at = @At("HEAD"), cancellable = true)
    public void canJoin(SocketAddress address, GameProfile profile, CallbackInfoReturnable<net.minecraft.network.chat.Component> cir) {
        if (DiscordIntegration.INSTANCE == null) return;

        LinkManager.checkGlobalAPI(profile.getId());
        final Component eventKick = INSTANCE.callEventO((e) -> e.onPlayerJoin(profile.getId()));
        if (eventKick != null) {
            final String jsonComp = GsonComponentSerializer.gson().serialize(eventKick).replace("\\\\n", "\n");
            try {
                final net.minecraft.network.chat.Component comp = net.minecraft.network.chat.Component.Serializer.fromJson(jsonComp, VanillaRegistries.createLookup());
                cir.setReturnValue(comp);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (Configuration.instance().linking.whitelistMode && DiscordIntegration.INSTANCE.getServerInterface().isOnlineMode()) {
            try {
                if (!LinkManager.isPlayerLinked(profile.getId())) {
                    cir.setReturnValue(net.minecraft.network.chat.Component.literal(Localization.instance().linking.notWhitelistedCode.replace("%code%", "" + (FloodgateUtils.isBedrockPlayer(profile.getId()) ? LinkManager.genBedrockLinkNumber(profile.getId()) : LinkManager.genLinkNumber(profile.getId())))));
                } else if (!DiscordIntegration.INSTANCE.canPlayerJoin(profile.getId())) {
                    cir.setReturnValue(net.minecraft.network.chat.Component.literal(Localization.instance().linking.notWhitelistedRole));
                }
            } catch (IllegalStateException e) {
                cir.setReturnValue(net.minecraft.network.chat.Component.literal("An error occured\nPlease check Server Log for more information\n\n" + e));
                e.printStackTrace();
            }
        }
    }
}
