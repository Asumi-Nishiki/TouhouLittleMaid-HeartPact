package com.example.maidmarriage.compat;

import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.data.ChildLineageData;
import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.example.maidmarriage.init.ModEntities;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.ItemFilm;
import com.github.tartaricacid.touhoulittlemaid.item.ItemPhoto;
import com.github.tartaricacid.touhoulittlemaid.item.ItemSmartSlab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.Objects;

/**
 * Keep child-maid identity stable across Smart Slab (soul spell) store/restore.
 */
public final class SoulSlabChildBridge {
    private static final String FORGE_DATA_TAG = "ForgeData";
    private static final String TAGS_TAG = "Tags";
    private static final String BRIDGE_CHILD_TAG = "maidmarriage_bridge_child";
    private static final int DAY_TICKS = 24000;
    public static final String PERSISTENT_TEMP_REVIVE_CHILD_KEY = "maidmarriage_temp_revive_child";
    public static final String PERSISTENT_TEMP_REVIVE_FROM_PHOTO_KEY = "maidmarriage_temp_revive_from_photo";

    private SoulSlabChildBridge() {
    }

    @SubscribeEvent
    public static void onToItem(MaidAndItemTransformEvent.ToItem event) {
        if (!isReviveTransform(event.getItem())) {
            return;
        }
        EntityMaid maid = event.getMaid();
        if (!maid.level().isClientSide()) {
            MaidCarryChildManager.releaseBeforeMaidTransform(maid);
        }
        boolean isPhoto = event.getItem().getItem() instanceof ItemPhoto;
        boolean isChild = isCurrentlyChildState(maid);
        ChildLineageData lineageData = maid.getData(ModTaskData.CHILD_LINEAGE_DATA);
        boolean hasLineage = hasLineageMarkers(maid.getPersistentData())
                || maid.getTags().contains(MaidChildEntity.BORN_MAID_TAG)
                || (lineageData != null && lineageData.bornMaid());
        /*
         * 稳定方案：这里只负责“真实小女仆 / 子代血统”的持久化桥接。
         * 不再承担成年大女仆的临时 child revive 逻辑，避免照片、胶片、神龛之间互相误伤。
         */
        if (!isChild && !hasLineage) {
            return;
        }
        CompoundTag data = event.getData();
        CompoundTag forgeData = data.getCompound(FORGE_DATA_TAG);
        CompoundTag persistent = maid.getPersistentData();

        if (isPhoto && isChild) {
            /*
             * 只有照片恢复真实小女仆时，才能把实体 id 改成 MaidChildEntity。
             *
             * 注意：神龛复活走的是 ItemFilm.filmToMaid，原版会先检查胶片 NBT 里的 id
             * 是否等于 touhou_little_maid:maid。若这里把胶片 id 改成 maidmarriage:maid_child，
             * 神龛会直接认为“这张胶片中没有女仆数据”。
             *
             * 因此胶片/魂符只写 child 标记，不改 id；恢复成普通 EntityMaid 后，
             * 再由 EntityJoinLevelEvent 中的 repairOrPromoteLegacyChild 替换成 MaidChildEntity。
             */
            data.putString("id", Objects.requireNonNull(ForgeRegistries.ENTITY_TYPES.getKey(ModEntities.MAID_CHILD.get())).toString());
        }

        if (isChild) {
            copyChildMarkers(maid, forgeData);
            data.putBoolean(BRIDGE_CHILD_TAG, true);
        }

        if (hasLineage) {
            copyLineageMarkers(persistent, forgeData);
            if (lineageData != null && lineageData.bornMaid()) {
                lineageData.mother().ifPresent(uuid -> forgeData.putUUID(MaidChildEntity.PERSISTENT_MOTHER_UUID_KEY, uuid));
                lineageData.father().ifPresent(uuid -> forgeData.putUUID(MaidChildEntity.PERSISTENT_FATHER_UUID_KEY, uuid));
                lineageData.grandParent().ifPresent(uuid -> forgeData.putUUID(MaidChildEntity.PERSISTENT_GRAND_PARENT_UUID_KEY, uuid));
                lineageData.customNameJson().ifPresent(name -> forgeData.putString(MaidChildEntity.PERSISTENT_CHILD_NAME_JSON_KEY, name));
            }
            ensureBornTag(data);
        }
        data.put(FORGE_DATA_TAG, forgeData);
    }

    @SubscribeEvent
    public static void onToMaid(MaidAndItemTransformEvent.ToMaid event) {
        if (!isReviveTransform(event.getItem())) {
            return;
        }
        CompoundTag data = event.getData();
        CompoundTag forgeData = data.getCompound(FORGE_DATA_TAG);
        if (data.getBoolean(PERSISTENT_TEMP_REVIVE_CHILD_KEY)) {
            forgeData.putBoolean(PERSISTENT_TEMP_REVIVE_CHILD_KEY, true);
        }
        if (data.getBoolean(PERSISTENT_TEMP_REVIVE_FROM_PHOTO_KEY)) {
            forgeData.putBoolean(PERSISTENT_TEMP_REVIVE_FROM_PHOTO_KEY, true);
        }
        boolean restoreAsChild = hasChildStateMarkers(forgeData) || data.getBoolean(BRIDGE_CHILD_TAG);
        if (hasLineageMarkers(forgeData)) {
            ensureBornTag(data);
        }
        if (!restoreAsChild) {
            return;
        }

        if (!forgeData.contains(MaidChildEntity.PERSISTENT_CHILD_ACTIVE_KEY)) {
            forgeData.putBoolean(MaidChildEntity.PERSISTENT_CHILD_ACTIVE_KEY, true);
        }
        if (!forgeData.contains(MaidChildEntity.PERSISTENT_TAME_INITIALIZED_KEY)) {
            forgeData.putBoolean(MaidChildEntity.PERSISTENT_TAME_INITIALIZED_KEY, true);
        }
        if (forgeData.hasUUID(MaidChildEntity.PERSISTENT_MOTHER_UUID_KEY)
                || forgeData.hasUUID(MaidChildEntity.PERSISTENT_FATHER_UUID_KEY)) {
            ensureBornTag(data);
        }
        data.put(FORGE_DATA_TAG, forgeData);
    }

    @SubscribeEvent
    public static void onMaidJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }

        syncLineageTaskData(maid);
        /*
         * 先清成年残留，再判断是否仍是小女仆。
         * 旧存档/魂符恢复可能留下 child=true 的 TaskData，客户端会据此继续缩放模型。
         */
        if (MaidChildEntity.repairAdultLifecycleIfNeeded(maid)) {
            return;
        }

        if (!MaidChildEntity.shouldStayChild(maid)) {
            return;
        }
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }

        repairOrPromoteLegacyChild(level, maid);
    }

    /**
     * 统一修复旧 child 生命周期数据：
     * 1) 若恢复后的成长已到成年阈值，就在原实体上直接清理 child 生命周期；
     * 2) 若还没成年，则保持当前实体不变，继续作为同一个整体成长；
     * 3) 不再把普通 EntityMaid 强制替换成 MaidChildEntity，避免 UUID 与长期数据再次断链。
     */
    private static void repairOrPromoteLegacyChild(ServerLevel level, EntityMaid maid) {
        int adultAfterTicks = Math.max(1, ModConfigs.childGrowthDays()) * DAY_TICKS;
        int growthTicks = Math.max(0, maid.getPersistentData().getInt(MaidChildEntity.PERSISTENT_GROWTH_TICKS_KEY));
        if (growthTicks >= adultAfterTicks) {
            MaidChildEntity.markAsAdult(maid);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, maid.getX(), maid.getY(1.0D), maid.getZ(),
                    10, 0.3D, 0.2D, 0.3D, 0.02D);
        }
    }

    private static void copyChildMarkers(EntityMaid maid, CompoundTag to) {
        CompoundTag from = maid.getPersistentData();
        ChildStateDataSnapshot snapshot = resolveChildStateSnapshot(maid);
        if (from.contains(MaidChildEntity.PERSISTENT_CHILD_ACTIVE_KEY)) {
            to.putBoolean(MaidChildEntity.PERSISTENT_CHILD_ACTIVE_KEY,
                    from.getBoolean(MaidChildEntity.PERSISTENT_CHILD_ACTIVE_KEY));
        } else {
            to.putBoolean(MaidChildEntity.PERSISTENT_CHILD_ACTIVE_KEY, true);
        }
        if (from.contains(MaidChildEntity.PERSISTENT_TAME_INITIALIZED_KEY)) {
            to.putBoolean(MaidChildEntity.PERSISTENT_TAME_INITIALIZED_KEY,
                    from.getBoolean(MaidChildEntity.PERSISTENT_TAME_INITIALIZED_KEY));
        }
        if (snapshot.growthTicks() > 0 || from.contains(MaidChildEntity.PERSISTENT_GROWTH_TICKS_KEY)) {
            to.putInt(MaidChildEntity.PERSISTENT_GROWTH_TICKS_KEY, snapshot.growthTicks());
        }
        if (!snapshot.growthStage().isEmpty() || from.contains(MaidChildEntity.PERSISTENT_GROWTH_STAGE_KEY)) {
            to.putString(MaidChildEntity.PERSISTENT_GROWTH_STAGE_KEY, snapshot.growthStage());
        }
        if (from.contains(MaidChildEntity.PERSISTENT_CHILD_NAME_JSON_KEY)) {
            to.putString(MaidChildEntity.PERSISTENT_CHILD_NAME_JSON_KEY,
                    from.getString(MaidChildEntity.PERSISTENT_CHILD_NAME_JSON_KEY));
        }
        if (from.hasUUID(MaidChildEntity.PERSISTENT_MOTHER_UUID_KEY)) {
            to.putUUID(MaidChildEntity.PERSISTENT_MOTHER_UUID_KEY,
                    from.getUUID(MaidChildEntity.PERSISTENT_MOTHER_UUID_KEY));
        }
        if (from.hasUUID(MaidChildEntity.PERSISTENT_FATHER_UUID_KEY)) {
            to.putUUID(MaidChildEntity.PERSISTENT_FATHER_UUID_KEY,
                    from.getUUID(MaidChildEntity.PERSISTENT_FATHER_UUID_KEY));
        }
        if (from.hasUUID(MaidChildEntity.PERSISTENT_GRAND_PARENT_UUID_KEY)) {
            to.putUUID(MaidChildEntity.PERSISTENT_GRAND_PARENT_UUID_KEY,
                    from.getUUID(MaidChildEntity.PERSISTENT_GRAND_PARENT_UUID_KEY));
        }
    }

    private static ChildStateDataSnapshot resolveChildStateSnapshot(EntityMaid maid) {
        CompoundTag persistent = maid.getPersistentData();
        int growthTicks = Math.max(0, persistent.getInt(MaidChildEntity.PERSISTENT_GROWTH_TICKS_KEY));
        String growthStage = persistent.contains(MaidChildEntity.PERSISTENT_GROWTH_STAGE_KEY)
                ? persistent.getString(MaidChildEntity.PERSISTENT_GROWTH_STAGE_KEY)
                : "";
        com.example.maidmarriage.data.ChildStateData state = maid.getData(ModTaskData.CHILD_STATE_DATA);
        if (state != null && state.child()) {
            growthTicks = Math.max(growthTicks, Math.max(0, state.growthTicks()));
            if (growthStage.isEmpty() || state.growthTicks() >= persistent.getInt(MaidChildEntity.PERSISTENT_GROWTH_TICKS_KEY)) {
                growthStage = state.growthStage();
            }
        }
        if (maid instanceof MaidChildEntity child) {
            growthTicks = Math.max(growthTicks, child.debugGrowthTicks());
            growthStage = MaidChildEntity.resolveGrowthStage(child).name();
        }
        if (growthStage.isEmpty()) {
            growthStage = MaidChildEntity.resolveGrowthStage(maid).name();
        }
        return new ChildStateDataSnapshot(growthTicks, growthStage);
    }

    private record ChildStateDataSnapshot(int growthTicks, String growthStage) {
    }

    private static boolean hasAnyChildMarker(CompoundTag data) {
        return hasChildStateMarkers(data) || hasLineageMarkers(data);
    }

    private static boolean hasChildStateMarkers(CompoundTag data) {
        return data.getBoolean(MaidChildEntity.PERSISTENT_CHILD_ACTIVE_KEY)
                || data.contains(MaidChildEntity.PERSISTENT_GROWTH_TICKS_KEY)
                || data.contains(MaidChildEntity.PERSISTENT_GROWTH_STAGE_KEY);
    }

    private static boolean hasLineageMarkers(CompoundTag data) {
        return data.hasUUID(MaidChildEntity.PERSISTENT_MOTHER_UUID_KEY)
                || data.hasUUID(MaidChildEntity.PERSISTENT_FATHER_UUID_KEY)
                || data.hasUUID(MaidChildEntity.PERSISTENT_GRAND_PARENT_UUID_KEY)
                || data.contains(MaidChildEntity.PERSISTENT_CHILD_NAME_JSON_KEY);
    }

    private static void copyLineageMarkers(CompoundTag from, CompoundTag to) {
        if (from.hasUUID(MaidChildEntity.PERSISTENT_MOTHER_UUID_KEY)) {
            to.putUUID(MaidChildEntity.PERSISTENT_MOTHER_UUID_KEY,
                    from.getUUID(MaidChildEntity.PERSISTENT_MOTHER_UUID_KEY));
        }
        if (from.hasUUID(MaidChildEntity.PERSISTENT_FATHER_UUID_KEY)) {
            to.putUUID(MaidChildEntity.PERSISTENT_FATHER_UUID_KEY,
                    from.getUUID(MaidChildEntity.PERSISTENT_FATHER_UUID_KEY));
        }
        if (from.hasUUID(MaidChildEntity.PERSISTENT_GRAND_PARENT_UUID_KEY)) {
            to.putUUID(MaidChildEntity.PERSISTENT_GRAND_PARENT_UUID_KEY,
                    from.getUUID(MaidChildEntity.PERSISTENT_GRAND_PARENT_UUID_KEY));
        }
        if (from.contains(MaidChildEntity.PERSISTENT_CHILD_NAME_JSON_KEY)) {
            to.putString(MaidChildEntity.PERSISTENT_CHILD_NAME_JSON_KEY,
                    from.getString(MaidChildEntity.PERSISTENT_CHILD_NAME_JSON_KEY));
        }
    }

    private static void ensureBornTag(CompoundTag data) {
        ListTag tags = data.getList(TAGS_TAG, Tag.TAG_STRING);
        for (int i = 0; i < tags.size(); i++) {
            if (MaidChildEntity.BORN_MAID_TAG.equals(tags.getString(i))) {
                data.put(TAGS_TAG, tags);
                return;
            }
        }
        tags.add(StringTag.valueOf(MaidChildEntity.BORN_MAID_TAG));
        data.put(TAGS_TAG, tags);
    }

    private static boolean isReviveTransform(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.getItem() instanceof ItemSmartSlab
                || stack.getItem() instanceof ItemPhoto
                || stack.getItem() instanceof ItemFilm;
    }

    private static boolean isCurrentlyChildState(EntityMaid maid) {
        if (MaidChildEntity.shouldStayChild(maid)) {
            return true;
        }
        CompoundTag persistent = maid.getPersistentData();
        return persistent.getBoolean(MaidChildEntity.PERSISTENT_CHILD_ACTIVE_KEY)
                || persistent.contains(MaidChildEntity.PERSISTENT_GROWTH_TICKS_KEY)
                || persistent.contains(MaidChildEntity.PERSISTENT_GROWTH_STAGE_KEY);
    }

    private static void syncLineageTaskData(EntityMaid maid) {
        if (ModTaskData.CHILD_LINEAGE_DATA == null || maid.level().isClientSide()) {
            return;
        }
        CompoundTag persistent = maid.getPersistentData();
        Optional<java.util.UUID> mother = persistent.hasUUID(MaidChildEntity.PERSISTENT_MOTHER_UUID_KEY)
                ? Optional.of(persistent.getUUID(MaidChildEntity.PERSISTENT_MOTHER_UUID_KEY))
                : Optional.empty();
        Optional<java.util.UUID> father = persistent.hasUUID(MaidChildEntity.PERSISTENT_FATHER_UUID_KEY)
                ? Optional.of(persistent.getUUID(MaidChildEntity.PERSISTENT_FATHER_UUID_KEY))
                : Optional.empty();
        Optional<java.util.UUID> grandParent = persistent.hasUUID(MaidChildEntity.PERSISTENT_GRAND_PARENT_UUID_KEY)
                ? Optional.of(persistent.getUUID(MaidChildEntity.PERSISTENT_GRAND_PARENT_UUID_KEY))
                : Optional.empty();
        boolean bornMaid = maid.getTags().contains(MaidChildEntity.BORN_MAID_TAG) || mother.isPresent() || father.isPresent();
        if (!bornMaid) {
            return;
        }
        Optional<String> customName = maid.hasCustomName() && maid.getCustomName() != null
                ? Optional.of(net.minecraft.network.chat.Component.Serializer.toJson(maid.getCustomName()))
                : Optional.empty();
        maid.setData(ModTaskData.CHILD_LINEAGE_DATA, new ChildLineageData(true, mother, father, grandParent, customName));
    }
}
