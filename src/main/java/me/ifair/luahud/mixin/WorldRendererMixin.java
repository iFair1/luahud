package me.ifair.luahud.mixin;

import me.ifair.luahud.sound.RecordTracker;
import me.ifair.luahud.sound.SoundState;
import net.minecraft.block.jukebox.JukeboxSong;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

//? if <1.21.2 {
@Mixin(WorldRenderer.class)
//?} else {
/*@Mixin(net.minecraft.client.world.WorldEventHandler.class)
*///?}
public abstract class WorldRendererMixin implements SoundState {
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private Map<BlockPos, SoundInstance> playingSongs;
    @Unique private final RecordTracker luahud$records = new RecordTracker();

    @Inject(method = "playJukeboxSong", at = @At("TAIL"))
    private void luahud$startRecord(RegistryEntry<JukeboxSong> song, BlockPos pos, CallbackInfo ci) {
        ClientWorld world = client.world;
        if (world == null) return;
        String id = song.getKey().map(key -> key.getValue().toString())
                //? if <1.21.2 {
                .orElseGet(() -> song.value().soundEvent().value().getId().toString());
                //?} else {
                /*.orElseGet(() -> song.value().soundEvent().value().id().toString());
                *///?}
        // Playback packets carry a song, not an item stack. Resolve the actual disc item
        // from its component instead of assuming vanilla item or sound naming conventions.
        for (var item : Registries.ITEM) {
            var itemSong = JukeboxSong.getSongEntryFromStack(world.getRegistryManager(), item.getDefaultStack());
            if (itemSong.isPresent() && itemSong.get().equals(song)) {
                id = Registries.ITEM.getId(item).toString();
                break;
            }
        }
        luahud$records.start(pos, id, song.value().description().getString(), song.value().lengthInSeconds());
    }

    @Inject(method = "stopJukeboxSong", at = @At("HEAD"))
    private void luahud$stopRecord(BlockPos pos, CallbackInfo ci) {
        luahud$records.stop(pos);
    }

    //? if <1.21.2 {
    @Inject(method = "setWorld", at = @At("HEAD"))
    private void luahud$clearRecords(ClientWorld world, CallbackInfo ci) {
        luahud$records.clear();
    }

    //?}

    @Override
    public void luahud$setSoundPaused(boolean paused) {
        luahud$records.setPaused(paused);
    }

    @Override
    public RecordTracker.PlayingRecord luahud$getRecord() {
        ClientWorld world = client.world;
        if (world == null || client.player == null) return null;
        return luahud$records.nearest(new net.minecraft.util.math.Vec3d(client.player.getX(), client.player.getY(), client.player.getZ()), pos -> {
            SoundInstance sound = playingSongs.get(pos);
            return sound != null && client.getSoundManager().isPlaying(sound);
        });
    }
}
