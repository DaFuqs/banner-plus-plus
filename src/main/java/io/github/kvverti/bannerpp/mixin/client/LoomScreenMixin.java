package io.github.kvverti.bannerpp.mixin.client;

import io.github.kvverti.bannerpp.api.LoomPattern;
import io.github.kvverti.bannerpp.api.LoomPatterns;
import io.github.kvverti.bannerpp.api.PatternLimitModifier;
import io.github.kvverti.bannerpp.iface.LoomPatternContainer;

import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BannerPattern;
import net.minecraft.client.gui.screen.ingame.AbstractContainerScreen;
import net.minecraft.client.gui.screen.ingame.LoomScreen;
import net.minecraft.container.LoomContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoomScreen.class)
public abstract class LoomScreenMixin extends AbstractContainerScreen<LoomContainer> {

    @Shadow private boolean hasTooManyPatterns;

    private LoomScreenMixin() {
        super(null, null, null);
    }

    /**
     * Adds the number of rows corresponding to Banner++ loom patterns
     * to the loom GUI.
     */
    @Redirect(
        method = "<clinit>",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/block/entity/BannerPattern;COUNT:I"
        )
    )
    private static int takeBppIntoAccountForRowCount() {
        return BannerPattern.COUNT + LoomPatterns.dyeLoomPatternCount();
    }

    /**
     * Modifies the banner pattern count to include the number of
     * dye loom patterns.
     */
    @Redirect(
        method = "drawBackground",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/block/entity/BannerPattern;COUNT:I"
        )
    )
    private int modifyDyePatternCount() {
        return BannerPattern.COUNT + LoomPatterns.dyeLoomPatternCount();
    }

    @Redirect(
        method = "drawBackground",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/container/LoomContainer;getSelectedPattern()I",
            ordinal = 0
        )
    )
    private int negateBppLoomPatternForCmp(LoomContainer self) {
        int res = self.getSelectedPattern();
        if(res < 0) {
            res = -res;
        }
        return res;
    }

    @ModifyConstant(method = "onInventoryChanged", constant = @Constant(intValue = 6))
    private int disarmVanillaPatternLimitCheck(int limit) {
        return Integer.MAX_VALUE;
    }

    @Inject(
        method = "onInventoryChanged",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/gui/screen/ingame/LoomScreen;hasTooManyPatterns:Z",
            opcode = Opcodes.GETFIELD,
            ordinal = 0
        )
    )
    private void addBppLoomPatternsToFullCond(CallbackInfo info) {
        ItemStack banner = ((LoomContainer)this.container).getBannerSlot().getStack();
        int patternLimit = PatternLimitModifier.EVENT.invoker().computePatternLimit(6, this.playerInventory.player);
        this.hasTooManyPatterns |= BannerBlockEntity.getPatternCount(banner) >= patternLimit;
    }

    @Unique
    private int loomPatternIndex;

    /**
     * Prevents an ArrayIndexOutOfBoundsException from occuring when the vanilla
     * code tries to index BannerPattern.values() with an index representing
     * a Banner++ loom pattern (which is negative).
     */
    @ModifyVariable(
        method = "method_22692",
        at = @At(value = "LOAD", ordinal = 0),
        ordinal = 0
    )
    private int disarmBppIndexForVanilla(int patternIndex) {
        loomPatternIndex = patternIndex;
        if(patternIndex < 0) {
            patternIndex = 0;
        }
        return patternIndex;
    }

    /**
     * If the pattern index indicates a Banner++ pattern, put the Banner++
     * pattern in the item NBT instead of a vanilla pattern.
     */
    @Redirect(
        method = "method_22692",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/nbt/CompoundTag;put(Ljava/lang/String;Lnet/minecraft/nbt/Tag;)Lnet/minecraft/nbt/Tag;",
            ordinal = 0
        )
    )
    private Tag proxyPutPatterns(CompoundTag tag, String key, Tag patterns) {
        if(loomPatternIndex < 0) {
            int loomPatternIdx = -loomPatternIndex - (1 + BannerPattern.LOOM_APPLICABLE_COUNT);
            LoomPattern pattern = LoomPatterns.byLoomIndex(loomPatternIdx);
            ListTag loomPatterns = new ListTag();
            CompoundTag patternTag = new CompoundTag();
            patternTag.putString("Pattern", LoomPatterns.REGISTRY.getId(pattern).toString());
            patternTag.putInt("Color", 0);
            patternTag.putInt("Index", 1);
            loomPatterns.add(patternTag);
            // pop dummy vanilla banner pattern
            ListTag vanillaPatterns = (ListTag)patterns;
            assert vanillaPatterns.size() == 2 : vanillaPatterns.size();
            vanillaPatterns.remove(1);
            tag.put(LoomPatternContainer.NBT_KEY, loomPatterns);
        }
        return tag.put(key, patterns);
    }

    /**
     * The dye pattern loop has positive indices, we negate the indices that
     * represent Banner++ loom patterns before passing them to method_22692.
     */
    @ModifyArg(
        method = "drawBackground",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/ingame/LoomScreen;method_22692(III)V",
            ordinal = 0
        ),
        index = 0
    )
    private int modifyBppPatternIdxArg(int patternIdx) {
        if(patternIdx > BannerPattern.LOOM_APPLICABLE_COUNT) {
            patternIdx = -patternIdx;
        }
        return patternIdx;
    }
}
