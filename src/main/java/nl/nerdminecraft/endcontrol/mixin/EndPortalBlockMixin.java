package nl.nerdminecraft.endcontrol.mixin;

import nl.nerdminecraft.endcontrol.EndControlMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndPortalBlock.class)
public abstract class EndPortalBlockMixin {

    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void nerdEndControl$blockEntryWhileClosed(
            ServerLevel currentLevel,
            Entity entity,
            BlockPos portalEntryPos,
            CallbackInfoReturnable<TeleportTransition> cir
    ) {
        // Anyone already in The End can still use the exit portal.
        if (currentLevel.dimension().equals(Level.END)) return;

        EndControlMod.refreshSchedule(currentLevel.getServer());

        if (EndControlMod.isOpen()) return;

        if (entity instanceof ServerPlayer player) {
            EndControlMod.notifyBlocked(player);
        }

        // Prevent teleport into The End while closed.
        cir.setReturnValue(null);
    }
}
