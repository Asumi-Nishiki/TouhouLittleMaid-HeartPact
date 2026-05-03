package com.example.maidmarriage.item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 婚姻同意申请书物品。
 * <p>
 * 该道具用于两段式移交流程：
 * <ul>
 *     <li>第一步：对成年的子代女仆右键，绑定女仆信息。</li>
 *     <li>第二步：对目标玩家右键，绑定目标玩家并触发移交流程。</li>
 * </ul>
 * 物品会根据绑定状态显示附魔光效与额外文案，便于玩家确认当前流程状态。
 */
public class MarriageConsentFormItem extends DescriptionItem {
    private static final String TAG_BOUND_MAID_UUID = "maidmarriage_consent_bound_maid_uuid";
    private static final String TAG_BOUND_MAID_NAME = "maidmarriage_consent_bound_maid_name";
    private static final String TAG_BOUND_TARGET_UUID = "maidmarriage_consent_bound_target_uuid";
    private static final String TAG_BOUND_TARGET_NAME = "maidmarriage_consent_bound_target_name";

    public MarriageConsentFormItem(Properties properties, String tooltipKey) {
        super(properties, tooltipKey);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getBoundMaidUuid(stack).isPresent() || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);
        getBoundMaidName(stack).ifPresent(name -> tooltipComponents.add(
                Component.translatable("item.maidmarriage.marriage_consent_form.bound_maid", name)
                        .withStyle(ChatFormatting.AQUA)));
        getBoundTargetName(stack).ifPresent(name -> tooltipComponents.add(
                Component.translatable("item.maidmarriage.marriage_consent_form.bound_target", name)
                        .withStyle(ChatFormatting.LIGHT_PURPLE)));
        if (getBoundMaidName(stack).isPresent() && getBoundTargetName(stack).isPresent()) {
            tooltipComponents.add(Component.translatable(
                    "item.maidmarriage.marriage_consent_form.bound_pair",
                    getBoundMaidName(stack).orElse(""),
                    getBoundTargetName(stack).orElse(""))
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /**
     * 绑定女仆信息到申请书。
     * 重新绑定女仆时会清理旧的目标玩家绑定，避免串线。
     */
    public static void bindMaid(ItemStack stack, UUID maidUuid, Component maidName) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(TAG_BOUND_MAID_UUID, maidUuid);
        tag.putString(TAG_BOUND_MAID_NAME, maidName.getString());
        tag.remove(TAG_BOUND_TARGET_UUID);
        tag.remove(TAG_BOUND_TARGET_NAME);
    }

    /**
     * 绑定目标玩家信息到申请书。
     */
    public static void bindTargetPlayer(ItemStack stack, UUID playerUuid, Component playerName) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(TAG_BOUND_TARGET_UUID, playerUuid);
        tag.putString(TAG_BOUND_TARGET_NAME, playerName.getString());
    }

    /**
     * 读取已绑定女仆 UUID。
     */
    public static Optional<UUID> getBoundMaidUuid(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID(TAG_BOUND_MAID_UUID)) {
            return Optional.empty();
        }
        return Optional.of(tag.getUUID(TAG_BOUND_MAID_UUID));
    }

    /**
     * 读取已绑定女仆显示名。
     */
    public static Optional<String> getBoundMaidName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_BOUND_MAID_NAME)) {
            return Optional.empty();
        }
        String name = tag.getString(TAG_BOUND_MAID_NAME);
        return name == null || name.isBlank() ? Optional.empty() : Optional.of(name);
    }

    /**
     * 读取已绑定目标玩家显示名。
     */
    public static Optional<String> getBoundTargetName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_BOUND_TARGET_NAME)) {
            return Optional.empty();
        }
        String name = tag.getString(TAG_BOUND_TARGET_NAME);
        return name == null || name.isBlank() ? Optional.empty() : Optional.of(name);
    }
}
