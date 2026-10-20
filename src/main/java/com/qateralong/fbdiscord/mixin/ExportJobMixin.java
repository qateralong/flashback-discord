package com.qateralong.fbdiscord.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qateralong.fbdiscord.replay.ExportMixer;
import java.nio.FloatBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.moulberry.flashback.exporting.ExportJob", remap = false)
public abstract class ExportJobMixin {
    @WrapOperation(method = "doExport", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/SOFTLoopback;alcRenderSamplesSOFT(JLjava/nio/FloatBuffer;I)V"), require = 0)
    private void flashbackDiscord$mix(long device, FloatBuffer buffer, int samples, Operation<Void> original) {
        original.call(device, buffer, samples);
        ExportMixer.onRender(buffer, samples);
    }
}
