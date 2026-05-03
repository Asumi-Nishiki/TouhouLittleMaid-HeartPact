package com.example.maidmarriage.entity;

import com.example.maidmarriage.compat.bauble.GrowthPauseUtil;
import com.example.maidmarriage.compat.RelationshipThresholds;
import com.example.maidmarriage.config.DialogueScriptManager;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.data.ChildLineageData;
import com.example.maidmarriage.data.ChildStateData;
import com.example.maidmarriage.data.ModTaskData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class MaidChildEntity extends EntityMaid {
    private static final int DAY_TICKS = 24000;
    private static final EntityDataAccessor<Integer> DATA_GROWTH_TICKS =
            SynchedEntityData.defineId(MaidChildEntity.class, EntityDataSerializers.INT);

    public static final String BORN_MAID_TAG = "maidmarriage_born_maid";
    public static final String PERSISTENT_MOTHER_UUID_KEY = "maidmarriage_mother_uuid";
    public static final String PERSISTENT_FATHER_UUID_KEY = "maidmarriage_father_uuid";
    public static final String PERSISTENT_CHILD_ACTIVE_KEY = "maidmarriage_child_active";
    public static final String PERSISTENT_GROWTH_TICKS_KEY = "maidmarriage_child_growth_ticks";
    public static final String PERSISTENT_GROWTH_STAGE_KEY = "maidmarriage_child_growth_stage";
    public static final String PERSISTENT_INFANT_CARRY_END_TICK_KEY = "maidmarriage_infant_carry_end_tick";
    public static final String PERSISTENT_TAME_INITIALIZED_KEY = "maidmarriage_child_tame_initialized";
    public static final String PERSISTENT_CHILD_NAME_JSON_KEY = "maidmarriage_child_name_json";
    public static final String PERSISTENT_CHILD_NAME_CONFIRMED_KEY = "maidmarriage_child_name_confirmed";
    public static final String PERSISTENT_GRAND_PARENT_UUID_KEY = "maidmarriage_grand_parent_uuid";

    private static final double HEALTH_MULTIPLIER = 1.3D;
    private static final int INFANT_STAGE_DAYS = 1;
    private static final String TAG_GROWTH_TICKS = "GrowthTicks";
    private static final String TAG_STAGE = "GrowthStage";
    private static final String TAG_MOTHER_UUID = "MotherUuid";
    private static final String TAG_FATHER_UUID = "FatherUuid";

    private int growthTicks = 0;
    private UUID motherUuid;
    private UUID fatherUuid;
    private GrowthStage stage = GrowthStage.INFANT;

    public int debugGrowthTicks() {
        return this.entityData.get(DATA_GROWTH_TICKS);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public MaidChildEntity(EntityType<? extends MaidChildEntity> type, Level level) {
        super((EntityType<EntityMaid>) (EntityType) type, level);
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_GROWTH_TICKS, 0);
    }

    public void setParents(UUID motherUuid, UUID fatherUuid) {
        this.motherUuid = motherUuid;
        this.fatherUuid = fatherUuid;
        writeParentData(this, motherUuid, fatherUuid);
        syncLineageData(this, true);
        syncChildStateData(false);
    }

    public void inheritModelFromMother(EntityMaid mother) {
        if (mother.isYsmModel()) {
            String ysmModelId = mother.getYsmModelId();
            String ysmTexture = mother.getYsmModelTexture();
            Component ysmName = mother.getYsmModelName();
            this.setIsYsmModel(true);
            this.setYsmModel(ysmModelId, ysmTexture, ysmName);
            return;
        }
        this.setIsYsmModel(false);
        this.setModelId(mother.getModelId());
    }

    public void applyBornMaidTraits() {
        applyBornMaidTraits(this);
        syncChildStateData(false);
    }

    /**
     * 初始化“新生婴儿”状态：
     * - 阶段固定为婴儿；
     * - 记录被妈妈抱在怀里的截止时间；
     * - 让渲染与存档字段立即同步，避免出生瞬间出现旧阶段。
     */
    public void prepareNewbornInfant(long currentGameTime) {
        this.growthTicks = 0;
        this.stage = GrowthStage.INFANT;
        this.entityData.set(DATA_GROWTH_TICKS, 0);
        CompoundTag persistent = this.getPersistentData();
        persistent.putInt(PERSISTENT_GROWTH_TICKS_KEY, 0);
        persistent.putString(PERSISTENT_GROWTH_STAGE_KEY, GrowthStage.INFANT.name());
        persistent.remove(PERSISTENT_INFANT_CARRY_END_TICK_KEY);
        syncChildStateData(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (!hasChildLifecycleState(this)) {
            return;
        }
        getPersistentData().putBoolean(PERSISTENT_CHILD_ACTIVE_KEY, true);
        ensureFullTameState();
        boolean pauseGrowthByHairpin = GrowthPauseUtil.hasSunflowerHairpin(this);
        if (!pauseGrowthByHairpin) {
            this.growthTicks++;
            this.entityData.set(DATA_GROWTH_TICKS, this.growthTicks);
        }
        updateGrowthStage();
        tickInfantCarryState();
        syncPersistentGrowthData();
        syncChildStateData(false);
        if (this.growthTicks >= getAdultAfterTicks()) {
            promoteToAdult();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_GROWTH_TICKS, this.growthTicks);
        if (this.motherUuid != null) {
            tag.putUUID(TAG_MOTHER_UUID, this.motherUuid);
        }
        if (this.fatherUuid != null) {
            tag.putUUID(TAG_FATHER_UUID, this.fatherUuid);
        }
        tag.putString(TAG_STAGE, this.stage.name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.growthTicks = tag.getInt(TAG_GROWTH_TICKS);
        if (tag.hasUUID(TAG_MOTHER_UUID)) {
            this.motherUuid = tag.getUUID(TAG_MOTHER_UUID);
        }
        if (tag.hasUUID(TAG_FATHER_UUID)) {
            this.fatherUuid = tag.getUUID(TAG_FATHER_UUID);
        }
        if (tag.contains(TAG_STAGE)) {
            this.stage = GrowthStage.byName(tag.getString(TAG_STAGE));
        }
        CompoundTag persistent = this.getPersistentData();
        if (persistent.contains(PERSISTENT_GROWTH_TICKS_KEY)) {
            this.growthTicks = Math.max(this.growthTicks, persistent.getInt(PERSISTENT_GROWTH_TICKS_KEY));
        }
        this.entityData.set(DATA_GROWTH_TICKS, this.growthTicks);
        if (persistent.contains(PERSISTENT_GROWTH_STAGE_KEY)) {
            this.stage = GrowthStage.byName(persistent.getString(PERSISTENT_GROWTH_STAGE_KEY));
        }
        ChildStateData childStateData = this.getData(ModTaskData.CHILD_STATE_DATA);
        if (childStateData != null && childStateData.child()) {
            this.growthTicks = Math.max(this.growthTicks, Math.max(0, childStateData.growthTicks()));
            this.stage = GrowthStage.byName(childStateData.growthStage());
            childStateData.mother().ifPresent(uuid -> this.motherUuid = uuid);
            childStateData.father().ifPresent(uuid -> this.fatherUuid = uuid);
            childStateData.customNameJson().ifPresent(nameJson -> {
                Component component = parseComponentJson(nameJson);
                if (component != null) {
                    this.setCustomName(component);
                }
            });
            this.getPersistentData().putBoolean(PERSISTENT_CHILD_ACTIVE_KEY, true);
            this.getPersistentData().putInt(PERSISTENT_GROWTH_TICKS_KEY, this.growthTicks);
            this.getPersistentData().putString(PERSISTENT_GROWTH_STAGE_KEY, this.stage.name());
            this.getPersistentData().putBoolean(PERSISTENT_TAME_INITIALIZED_KEY, childStateData.tameInitialized());
            childStateData.customNameJson().ifPresent(name -> this.getPersistentData().putString(PERSISTENT_CHILD_NAME_JSON_KEY, name));
            this.entityData.set(DATA_GROWTH_TICKS, this.growthTicks);
        }
        ChildLineageData lineageData = this.getData(ModTaskData.CHILD_LINEAGE_DATA);
        if (lineageData != null && lineageData.bornMaid()) {
            lineageData.mother().ifPresent(uuid -> this.motherUuid = uuid);
            lineageData.father().ifPresent(uuid -> this.fatherUuid = uuid);
            lineageData.grandParent().ifPresent(uuid -> this.getPersistentData().putUUID(PERSISTENT_GRAND_PARENT_UUID_KEY, uuid));
            lineageData.customNameJson().ifPresent(nameJson -> {
                Component component = parseComponentJson(nameJson);
                if (component != null) {
                    this.setCustomName(component);
                }
            });
            this.addTag(BORN_MAID_TAG);
        }
        if (!this.hasCustomName() && this.getPersistentData().contains(PERSISTENT_CHILD_NAME_JSON_KEY)) {
            Component component = parseComponentJson(this.getPersistentData().getString(PERSISTENT_CHILD_NAME_JSON_KEY));
            if (component != null) {
                this.setCustomName(component);
            }
        }
        if (this.motherUuid != null || this.fatherUuid != null) {
            writeParentData(this, this.motherUuid, this.fatherUuid);
        }
    }

    private void promoteToAdult() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        /*
         * 小女仆成年必须走“同一个实体原地成长”：
         * 1. 不 new EntityMaid，不 discard 当前实体，避免 UUID、背包、饰品、装备、任务、好感和心情等长期数据断链；
         * 2. 成年只是生命周期阶段变化，所以这里只清理“仍是小女仆”的状态字段；
         * 3. 血统、父母、祖辈、自定义名等长期身份数据保留，后续家谱和婚约仍能识别她是出生女仆。
         */
        if (this.isPassenger()) {
            this.stopRiding();
        }
        this.noPhysics = false;
        this.stage = GrowthStage.ADULT;
        this.entityData.set(DATA_GROWTH_TICKS, getAdultAfterTicks());
        writeParentData(this, this.motherUuid, this.fatherUuid);
        syncLineageData(this, true);
        markAsAdult(this);
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY(1.0D), this.getZ(),
                10, 0.3D, 0.2D, 0.3D, 0.02D);
        if (this.getOwner() instanceof Player owner) {
            owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.child.growth.adult", this.getDisplayName()));
        }
    }

    /**
     * 新生婴儿阶段的特殊处理：
     * 1. 出生后的第一天强制由妈妈抱在怀里；
     * 2. 到时后自动放下，并在落地后切换到幼年期；
     * 3. 若妈妈暂时不在加载范围内，则等重新加载后继续补挂载。
     */
    private void tickInfantCarryState() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        EntityMaid mother = this.motherUuid == null ? null : findMother(serverLevel);
        if (mother == null || !mother.isAlive()) {
            return;
        }

        if (this.stage == GrowthStage.INFANT) {
            mother.setInSittingPose(false);
            this.setInSittingPose(false);
            if (this.getVehicle() != mother) {
                if (this.isPassenger()) {
                    this.stopRiding();
                }
                this.startRiding(mother, true);
            }
            this.setYBodyRot(mother.getYRot());
            this.setYHeadRot(mother.getYRot());
            return;
        }

        if (this.getVehicle() == mother) {
            this.stopRiding();
            moveToMotherSide(mother);
        }
        this.getPersistentData().remove(PERSISTENT_INFANT_CARRY_END_TICK_KEY);
    }

    private void updateGrowthStage() {
        int infantStageTicks = getInfantStageTicks();
        int childStageTicks = getChildStageTicks();
        int adultAfterTicks = getAdultAfterTicks();

        if (this.growthTicks < infantStageTicks) {
            this.stage = GrowthStage.INFANT;
            return;
        }
        if (this.growthTicks < childStageTicks) {
            if (this.stage != GrowthStage.JUVENILE) {
                this.stage = GrowthStage.JUVENILE;
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY(1.0D), this.getZ(),
                            6, 0.20D, 0.15D, 0.20D, 0.01D);
                }
                if (this.getOwner() instanceof Player owner) {
                    owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.child.growth.juvenile", this.getDisplayName()));
                }
            }
            return;
        }
        if (this.growthTicks < adultAfterTicks) {
            if (this.stage != GrowthStage.CHILD) {
                this.stage = GrowthStage.CHILD;
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY(1.0D), this.getZ(),
                            8, 0.25D, 0.2D, 0.25D, 0.02D);
                }
                if (this.getOwner() instanceof Player owner) {
                    owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.child.growth.child", this.getDisplayName()));
                }
            }
            return;
        }
        this.stage = GrowthStage.ADULT;
    }

    private void syncPersistentGrowthData() {
        CompoundTag persistent = this.getPersistentData();
        persistent.putInt(PERSISTENT_GROWTH_TICKS_KEY, this.growthTicks);
        persistent.putString(PERSISTENT_GROWTH_STAGE_KEY, this.stage.name());
    }

    private void syncChildStateData(boolean syncClient) {
        ChildStateData state = new ChildStateData(
                true,
                this.growthTicks,
                this.stage.name(),
                Optional.ofNullable(this.motherUuid),
                Optional.ofNullable(this.fatherUuid),
                this.getPersistentData().getBoolean(PERSISTENT_TAME_INITIALIZED_KEY),
                this.getPersistentData().getBoolean(PERSISTENT_CHILD_NAME_CONFIRMED_KEY),
                encodeCustomName(this)
        );
        applyChildStateData(this, state, syncClient);
        Optional<String> customName = encodeCustomName(this);
        if (customName.isPresent()) {
            this.getPersistentData().putString(PERSISTENT_CHILD_NAME_JSON_KEY, customName.get());
        } else {
            this.getPersistentData().remove(PERSISTENT_CHILD_NAME_JSON_KEY);
        }
    }

    private static void applyChildStateData(EntityMaid maid, ChildStateData state, boolean syncClient) {
        if (ModTaskData.CHILD_STATE_DATA == null || maid.level().isClientSide) {
            return;
        }
        if (syncClient) {
            maid.setAndSyncData(ModTaskData.CHILD_STATE_DATA, state);
        } else {
            maid.setData(ModTaskData.CHILD_STATE_DATA, state);
        }
    }

    public void syncChildStateToClient() {
        syncChildStateData(true);
    }

    private void ensureFullTameState() {
        CompoundTag persistent = this.getPersistentData();
        if (persistent.getBoolean(PERSISTENT_TAME_INITIALIZED_KEY)) {
            return;
        }
        if (this.getOwner() instanceof Player owner) {
            this.tame(owner);
            persistent.putBoolean(PERSISTENT_TAME_INITIALIZED_KEY, true);
        }
    }

    private static int getAdultAfterTicks() {
        return Math.max(INFANT_STAGE_DAYS + 2, ModConfigs.childGrowthDays()) * DAY_TICKS;
    }

    private static int getInfantStageTicks() {
        return INFANT_STAGE_DAYS * DAY_TICKS;
    }

    private static int getChildStageTicks() {
        int adultAfterTicks = getAdultAfterTicks();
        int remainingTicksAfterInfant = Math.max(2 * DAY_TICKS, adultAfterTicks - getInfantStageTicks());
        return getInfantStageTicks() + remainingTicksAfterInfant / 2;
    }

    private static void applyBornMaidTraits(EntityMaid maid) {
        maid.addTag(BORN_MAID_TAG);
        maid.setFavorability(RelationshipThresholds.HUG_UNLOCK);
        if (maid instanceof MaidChildEntity) {
            maid.getPersistentData().putBoolean(PERSISTENT_CHILD_ACTIVE_KEY, true);
            maid.getPersistentData().putBoolean(PERSISTENT_TAME_INITIALIZED_KEY, maid.isTame() && maid.getOwnerUUID() != null);
        }
        AttributeInstance maxHealth = maid.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(maxHealth.getBaseValue() * HEALTH_MULTIPLIER);
            maid.setHealth(maid.getMaxHealth());
        }
        syncLineageData(maid, false);
    }

    private static void writeParentData(EntityMaid maid, UUID motherUuid, UUID fatherUuid) {
        CompoundTag tag = maid.getPersistentData();
        if (motherUuid != null) {
            tag.putUUID(PERSISTENT_MOTHER_UUID_KEY, motherUuid);
        }
        if (fatherUuid != null) {
            tag.putUUID(PERSISTENT_FATHER_UUID_KEY, fatherUuid);
        }
    }

    public static boolean isChildOfPlayer(EntityMaid maid, UUID playerUuid) {
        ChildLineageData lineage = maid.getData(ModTaskData.CHILD_LINEAGE_DATA);
        if (lineage != null && lineage.bornMaid()
                && lineage.father().filter(playerUuid::equals).isPresent()) {
            return true;
        }
        CompoundTag tag = maid.getPersistentData();
        if (tag.hasUUID(PERSISTENT_FATHER_UUID_KEY) && playerUuid.equals(tag.getUUID(PERSISTENT_FATHER_UUID_KEY))) {
            return true;
        }
        ChildStateData state = maid.getData(ModTaskData.CHILD_STATE_DATA);
        return state != null && state.father().filter(playerUuid::equals).isPresent();
    }

    public static boolean isParentOfMaid(EntityMaid maid, UUID playerUuid) {
        ChildLineageData lineage = maid.getData(ModTaskData.CHILD_LINEAGE_DATA);
        if (lineage != null && lineage.bornMaid()) {
            if (lineage.father().filter(playerUuid::equals).isPresent()
                    || lineage.mother().filter(playerUuid::equals).isPresent()
                    || lineage.grandParent().filter(playerUuid::equals).isPresent()) {
                return true;
            }
        }
        CompoundTag tag = maid.getPersistentData();
        if ((tag.hasUUID(PERSISTENT_FATHER_UUID_KEY) && playerUuid.equals(tag.getUUID(PERSISTENT_FATHER_UUID_KEY)))
                || (tag.hasUUID(PERSISTENT_MOTHER_UUID_KEY) && playerUuid.equals(tag.getUUID(PERSISTENT_MOTHER_UUID_KEY)))
                || (tag.hasUUID(PERSISTENT_GRAND_PARENT_UUID_KEY) && playerUuid.equals(tag.getUUID(PERSISTENT_GRAND_PARENT_UUID_KEY)))) {
            return true;
        }
        ChildStateData state = maid.getData(ModTaskData.CHILD_STATE_DATA);
        if (state == null) {
            return false;
        }
        return state.father().filter(playerUuid::equals).isPresent()
                || state.mother().filter(playerUuid::equals).isPresent();
    }

    public static boolean shouldStayChild(EntityMaid maid) {
        if (maid == null) {
            return false;
        }
        /*
         * “是不是小女仆”只看长期生命周期数据，不再看实体类型。
         * MaidChildEntity 只是兼容壳和成长逻辑载体；成年后实体类型可以不变，但 child 状态必须消失。
         */
        ChildStateData state = maid.getData(ModTaskData.CHILD_STATE_DATA);
        if (state != null && state.child()) {
            return !isAdultLifecycleState(state.growthTicks(), GrowthStage.byName(state.growthStage()));
        }
        return hasChildLifecycleState(maid);
    }

    /**
     * 修复“已经成年但仍残留 child=true / CHILD 阶段显示”的旧数据。
     *
     * <p>魂符、照片、胶片恢复时可能把小女仆恢复成普通 EntityMaid；这时 TaskData 里如果还残留
     * child=true，就会让客户端继续按儿童缩放渲染。加入这个统一清理入口后，任何加载/恢复流程
     * 都可以先把成年生命周期收口掉。
     */
    public static boolean repairAdultLifecycleIfNeeded(EntityMaid maid) {
        if (maid == null || maid.level().isClientSide) {
            return false;
        }
        ChildStateData state = maid.getData(ModTaskData.CHILD_STATE_DATA);
        CompoundTag persistent = maid.getPersistentData();
        boolean adultByState = state != null
                && state.child()
                && isAdultLifecycleState(state.growthTicks(), GrowthStage.byName(state.growthStage()));
        boolean adultByPersistent = isAdultLifecycleState(
                persistent.getInt(PERSISTENT_GROWTH_TICKS_KEY),
                persistent.contains(PERSISTENT_GROWTH_STAGE_KEY)
                        ? GrowthStage.byName(persistent.getString(PERSISTENT_GROWTH_STAGE_KEY))
                        : null);
        if (!adultByState && !adultByPersistent) {
            return false;
        }
        markAsAdult(maid);
        return true;
    }

    /**
     * 兼容魂符/胶片恢复成普通 EntityMaid 的小女仆。
     *
     * <p>成长逻辑原本写在 MaidChildEntity.tick() 里；但魂符恢复可能拿到的是原版 EntityMaid。
     * 若不在 MaidTickEvent 里补这条外部生命周期推进，她会带着儿童数据，却永远不会继续长大。
     */
    public static void tickExternalChildLifecycle(EntityMaid maid) {
        if (maid == null || maid instanceof MaidChildEntity || maid.level().isClientSide) {
            return;
        }
        if (repairAdultLifecycleIfNeeded(maid) || !shouldStayChild(maid)) {
            return;
        }
        if (GrowthPauseUtil.hasSunflowerHairpin(maid)) {
            return;
        }

        CompoundTag persistent = maid.getPersistentData();
        ChildStateData oldState = maid.getData(ModTaskData.CHILD_STATE_DATA);
        int growthTicks = oldState != null && oldState.child()
                ? Math.max(0, oldState.growthTicks())
                : Math.max(0, persistent.getInt(PERSISTENT_GROWTH_TICKS_KEY));
        growthTicks++;

        GrowthStage stage = resolveGrowthStageByTicks(growthTicks);
        if (stage == GrowthStage.ADULT || growthTicks >= getAdultAfterTicks()) {
            markAsAdult(maid);
            if (maid.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, maid.getX(), maid.getY(1.0D), maid.getZ(),
                        10, 0.3D, 0.2D, 0.3D, 0.02D);
            }
            if (maid.getOwner() instanceof Player owner) {
                owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.child.growth.adult", maid.getDisplayName()));
            }
            return;
        }

        persistent.putBoolean(PERSISTENT_CHILD_ACTIVE_KEY, true);
        persistent.putInt(PERSISTENT_GROWTH_TICKS_KEY, growthTicks);
        persistent.putString(PERSISTENT_GROWTH_STAGE_KEY, stage.name());

        Optional<UUID> mother = oldState != null && oldState.mother().isPresent()
                ? oldState.mother()
                : resolveMotherUuid(maid);
        Optional<UUID> father = oldState != null && oldState.father().isPresent()
                ? oldState.father()
                : persistent.hasUUID(PERSISTENT_FATHER_UUID_KEY)
                ? Optional.of(persistent.getUUID(PERSISTENT_FATHER_UUID_KEY))
                : Optional.empty();
        boolean tameInitialized = oldState != null
                ? oldState.tameInitialized()
                : persistent.getBoolean(PERSISTENT_TAME_INITIALIZED_KEY);
        boolean nameConfirmed = oldState != null
                ? oldState.childNameConfirmed()
                : persistent.getBoolean(PERSISTENT_CHILD_NAME_CONFIRMED_KEY);
        Optional<String> customName = oldState != null && oldState.customNameJson().isPresent()
                ? oldState.customNameJson()
                : encodeCustomName(maid);
        applyChildStateData(maid, new ChildStateData(
                true,
                growthTicks,
                stage.name(),
                mother,
                father,
                tameInitialized,
                nameConfirmed,
                customName
        ), true);
    }

    public static void markAsAdult(EntityMaid maid) {
        CompoundTag persistent = maid.getPersistentData();
        /*
         * 成年只清理生命周期字段，不清理血统字段。
         * 这样她不再被判定为“小女仆”，但仍然保留出生女仆、父母、祖辈等长期身份。
         */
        if (maid instanceof MaidChildEntity child) {
            child.stage = GrowthStage.ADULT;
            child.entityData.set(DATA_GROWTH_TICKS, getAdultAfterTicks());
            if (child.isPassenger()) {
                child.stopRiding();
            }
            child.noPhysics = false;
        }
        persistent.putBoolean(PERSISTENT_CHILD_ACTIVE_KEY, false);
        persistent.remove(PERSISTENT_GROWTH_TICKS_KEY);
        persistent.remove(PERSISTENT_GROWTH_STAGE_KEY);
        persistent.remove(PERSISTENT_INFANT_CARRY_END_TICK_KEY);
        persistent.remove(PERSISTENT_TAME_INITIALIZED_KEY);
        if (maid.hasCustomName()) {
            String nameJson = Component.Serializer.toJson(maid.getCustomName());
            persistent.putString(PERSISTENT_CHILD_NAME_JSON_KEY, nameJson);
        }
        applyChildStateData(maid, ChildStateData.EMPTY, true);
    }

    public static boolean shortenGrowthByDays(EntityMaid maid, int days) {
        if (days <= 0 || !shouldStayChild(maid)) {
            return false;
        }
        int reduceTicks = days * DAY_TICKS;
        int adultAfterTicks = getAdultAfterTicks();
        CompoundTag persistent = maid.getPersistentData();
        int currentGrowthTicks;

        if (maid instanceof MaidChildEntity child) {
            child.growthTicks = Math.min(adultAfterTicks, Math.max(0, child.growthTicks + reduceTicks));
            child.updateGrowthStage();
            child.tickInfantCarryState();
            child.syncPersistentGrowthData();
            child.entityData.set(DATA_GROWTH_TICKS, child.growthTicks);
            currentGrowthTicks = child.growthTicks;
            if (currentGrowthTicks >= adultAfterTicks) {
                child.promoteToAdult();
                return false;
            }
            /*
             * 酱板鸭会直接跳过一整天成长进度，必须立刻同步 TaskData。
             * 否则魂符恢复成普通 EntityMaid、或客户端调试倒计时读取 ChildStateData 时，
             * 可能继续拿到喂食前的旧进度，看起来就像倒计时卡住了。
             */
            child.syncChildStateData(true);
        } else {
            currentGrowthTicks = persistent.getInt(PERSISTENT_GROWTH_TICKS_KEY);
            currentGrowthTicks = Math.min(adultAfterTicks, Math.max(0, currentGrowthTicks + reduceTicks));
            persistent.putBoolean(PERSISTENT_CHILD_ACTIVE_KEY, true);
            persistent.putInt(PERSISTENT_GROWTH_TICKS_KEY, currentGrowthTicks);
            String growthStage = resolveGrowthStageByTicks(currentGrowthTicks).name();
            persistent.putString(PERSISTENT_GROWTH_STAGE_KEY, growthStage);
            ChildStateData state = maid.getData(ModTaskData.CHILD_STATE_DATA);
            ChildStateData updated = state == null ? ChildStateData.EMPTY : state;
            updated = new ChildStateData(
                    true,
                    currentGrowthTicks,
                    growthStage,
                    updated.mother(),
                    updated.father(),
                    updated.tameInitialized(),
                    updated.childNameConfirmed(),
                    updated.customNameJson().isPresent() ? updated.customNameJson() : encodeCustomName(maid)
            );
            applyChildStateData(maid, updated, true);
            if (currentGrowthTicks >= adultAfterTicks) {
                markAsAdult(maid);
                return false;
            }
        }
        return currentGrowthTicks < adultAfterTicks;
    }

    public static boolean forceGrowToAdult(EntityMaid maid) {
        if (!shouldStayChild(maid)) {
            return false;
        }
        if (maid instanceof MaidChildEntity child) {
            child.stage = GrowthStage.ADULT;
            child.promoteToAdult();
            return true;
        }
        markAsAdult(maid);
        if (maid.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, maid.getX(), maid.getY(1.0D), maid.getZ(),
                    10, 0.3D, 0.2D, 0.3D, 0.02D);
        }
        return true;
    }

    public static GrowthStage resolveGrowthStage(EntityMaid maid) {
        if (!shouldStayChild(maid)) {
            return GrowthStage.ADULT;
        }
        if (maid instanceof MaidChildEntity child) {
            return resolveGrowthStageByTicks(child.debugGrowthTicks());
        }
        ChildStateData state = maid.getData(ModTaskData.CHILD_STATE_DATA);
        if (state != null && state.child()) {
            return GrowthStage.byName(state.growthStage());
        }
        CompoundTag persistent = maid.getPersistentData();
        if (persistent.contains(PERSISTENT_GROWTH_STAGE_KEY)) {
            return GrowthStage.byName(persistent.getString(PERSISTENT_GROWTH_STAGE_KEY));
        }
        return GrowthStage.ADULT;
    }

    public static boolean isInfantBeingCarried(EntityMaid maid) {
        if (!shouldStayChild(maid) || resolveGrowthStage(maid) != GrowthStage.INFANT) {
            return false;
        }
        if (!(maid.getVehicle() instanceof EntityMaid mother)) {
            return false;
        }
        return resolveMotherUuid(maid).filter(mother.getUUID()::equals).isPresent();
    }

    public static boolean isMotherCarryingInfant(EntityMaid maid) {
        for (Entity passenger : maid.getPassengers()) {
            if (passenger instanceof EntityMaid child && isInfantBeingCarried(child)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isMotherOfChild(EntityMaid child, EntityMaid possibleMother) {
        if (child == null || possibleMother == null || child == possibleMother) {
            return false;
        }
        return resolveMotherUuid(child).filter(possibleMother.getUUID()::equals).isPresent();
    }

    public static boolean hasConfirmedChildName(EntityMaid maid) {
        if (maid == null) {
            return false;
        }
        /*
         * 小女仆出生时会先写入一个默认显示名，甚至旧流程里还可能用命名牌给过预设名。
         * 这些都是“出生资料名”，不能等同于玩家已经在正式命名 UI 里确认过。
         *
         * 服务端使用 persistentData 作为最终权威；客户端读不到服务端 persistentData，
         * 所以客户端必须优先看会同步的 ChildStateData.childNameConfirmed。
         */
        ChildStateData state = maid.getData(ModTaskData.CHILD_STATE_DATA);
        if (state != null && state.child()) {
            return state.childNameConfirmed();
        }
        return maid.getPersistentData().getBoolean(PERSISTENT_CHILD_NAME_CONFIRMED_KEY);
    }

    public static void applyOneTimeChildName(EntityMaid maid, Component name) {
        if (maid == null || name == null || maid.level().isClientSide) {
            return;
        }

        String nameJson = Component.Serializer.toJson(name);
        CompoundTag persistent = maid.getPersistentData();
        persistent.putBoolean(PERSISTENT_CHILD_NAME_CONFIRMED_KEY, true);
        persistent.putString(PERSISTENT_CHILD_NAME_JSON_KEY, nameJson);
        maid.setCustomName(name);

        if (maid instanceof MaidChildEntity child) {
            child.syncChildStateData(true);
            syncLineageData(child, true);
            return;
        }

        ChildStateData oldState = maid.getData(ModTaskData.CHILD_STATE_DATA);
        Optional<UUID> mother = resolveMotherUuid(maid);
        Optional<UUID> father = oldState != null && oldState.father().isPresent()
                ? oldState.father()
                : persistent.hasUUID(PERSISTENT_FATHER_UUID_KEY)
                ? Optional.of(persistent.getUUID(PERSISTENT_FATHER_UUID_KEY))
                : Optional.empty();
        int growthTicks = oldState != null
                ? oldState.growthTicks()
                : persistent.getInt(PERSISTENT_GROWTH_TICKS_KEY);
        String growthStage = oldState != null
                ? oldState.growthStage()
                : resolveGrowthStage(maid).name();
        boolean tameInitialized = oldState != null
                ? oldState.tameInitialized()
                : persistent.getBoolean(PERSISTENT_TAME_INITIALIZED_KEY);
        applyChildStateData(maid, new ChildStateData(
                true,
                growthTicks,
                growthStage,
                mother,
                father,
                tameInitialized,
                true,
                Optional.of(nameJson)
        ), true);
        syncLineageData(maid, true);
    }

    public static boolean setGrowthRemainingSeconds(EntityMaid maid, int seconds) {
        if (!shouldStayChild(maid)) {
            return false;
        }
        int adultAfterTicks = getAdultAfterTicks();
        int remainingTicks = Math.max(0, seconds) * 20;
        int targetGrowthTicks = Math.max(0, adultAfterTicks - remainingTicks);

        if (maid instanceof MaidChildEntity child) {
            child.growthTicks = targetGrowthTicks;
            child.updateGrowthStage();
            child.tickInfantCarryState();
            child.syncPersistentGrowthData();
            child.entityData.set(DATA_GROWTH_TICKS, child.growthTicks);
        } else {
            CompoundTag persistent = maid.getPersistentData();
            persistent.putBoolean(PERSISTENT_CHILD_ACTIVE_KEY, true);
            persistent.putInt(PERSISTENT_GROWTH_TICKS_KEY, targetGrowthTicks);
            String stage = resolveGrowthStageByTicks(targetGrowthTicks).name();
            persistent.putString(PERSISTENT_GROWTH_STAGE_KEY, stage);
            ChildStateData state = maid.getData(ModTaskData.CHILD_STATE_DATA);
            ChildStateData updated = state == null ? ChildStateData.EMPTY : state;
            updated = new ChildStateData(
                    true,
                    targetGrowthTicks,
                    stage,
                    updated.mother(),
                    updated.father(),
                    updated.tameInitialized(),
                    updated.childNameConfirmed(),
                    updated.customNameJson().isPresent() ? updated.customNameJson() : encodeCustomName(maid)
            );
            applyChildStateData(maid, updated, false);
        }
        return true;
    }

    private static Optional<String> encodeCustomName(EntityMaid maid) {
        if (!maid.hasCustomName() || maid.getCustomName() == null) {
            return Optional.empty();
        }
        return Optional.of(Component.Serializer.toJson(maid.getCustomName()));
    }

    /**
     * 生命周期 child 状态的统一兜底判断：
     * 1. 优先看显式的 child_active；
     * 2. 再兼容旧存档里只剩成长字段、但还没同步回 TaskData 的情况；
     * 3. 一旦阶段已经是 ADULT，就不再把该实体视为小女仆。
     */
    private static boolean hasChildLifecycleState(EntityMaid maid) {
        CompoundTag persistent = maid.getPersistentData();
        if (persistent.contains(PERSISTENT_GROWTH_STAGE_KEY)
                && GrowthStage.byName(persistent.getString(PERSISTENT_GROWTH_STAGE_KEY)) == GrowthStage.ADULT) {
            return false;
        }
        if (persistent.contains(PERSISTENT_GROWTH_TICKS_KEY)
                && persistent.getInt(PERSISTENT_GROWTH_TICKS_KEY) >= getAdultAfterTicks()) {
            return false;
        }
        if (persistent.getBoolean(PERSISTENT_CHILD_ACTIVE_KEY)) {
            return true;
        }
        if (persistent.contains(PERSISTENT_GROWTH_STAGE_KEY)) {
            return GrowthStage.byName(persistent.getString(PERSISTENT_GROWTH_STAGE_KEY)) != GrowthStage.ADULT;
        }
        return persistent.contains(PERSISTENT_GROWTH_TICKS_KEY);
    }

    private static boolean isAdultLifecycleState(int growthTicks, @javax.annotation.Nullable GrowthStage stage) {
        return stage == GrowthStage.ADULT || growthTicks >= getAdultAfterTicks();
    }

    private static GrowthStage resolveGrowthStageByTicks(int growthTicks) {
        if (growthTicks < getInfantStageTicks()) {
            return GrowthStage.INFANT;
        }
        if (growthTicks < getChildStageTicks()) {
            return GrowthStage.JUVENILE;
        }
        if (growthTicks < getAdultAfterTicks()) {
            return GrowthStage.CHILD;
        }
        return GrowthStage.ADULT;
    }

    private @javax.annotation.Nullable EntityMaid findMother(ServerLevel serverLevel) {
        if (this.motherUuid == null) {
            return null;
        }
        Entity entity = serverLevel.getEntity(this.motherUuid);
        return entity instanceof EntityMaid maid ? maid : null;
    }

    private void moveToMotherSide(EntityMaid mother) {
        float yawRad = mother.getYRot() * ((float) Math.PI / 180F);
        double sideOffset = 0.45D;
        double backwardOffset = 0.20D;
        double x = mother.getX() + Math.cos(yawRad) * sideOffset + Math.sin(yawRad) * backwardOffset;
        double z = mother.getZ() + Math.sin(yawRad) * sideOffset - Math.cos(yawRad) * backwardOffset;
        this.moveTo(x, mother.getY(), z, mother.getYRot(), this.getXRot());
        this.setYBodyRot(mother.getYRot());
        this.setYHeadRot(mother.getYRot());
    }

    private static Optional<UUID> resolveMotherUuid(EntityMaid maid) {
        if (maid instanceof MaidChildEntity child && child.motherUuid != null) {
            return Optional.of(child.motherUuid);
        }
        ChildStateData state = maid.getData(ModTaskData.CHILD_STATE_DATA);
        if (state != null && state.mother().isPresent()) {
            return state.mother();
        }
        CompoundTag persistent = maid.getPersistentData();
        if (persistent.hasUUID(PERSISTENT_MOTHER_UUID_KEY)) {
            return Optional.of(persistent.getUUID(PERSISTENT_MOTHER_UUID_KEY));
        }
        return Optional.empty();
    }

    private static void syncLineageData(EntityMaid maid, boolean syncClient) {
        if (ModTaskData.CHILD_LINEAGE_DATA == null || maid.level().isClientSide) {
            return;
        }
        CompoundTag persistent = maid.getPersistentData();
        Optional<UUID> mother = persistent.hasUUID(PERSISTENT_MOTHER_UUID_KEY)
                ? Optional.of(persistent.getUUID(PERSISTENT_MOTHER_UUID_KEY))
                : Optional.empty();
        Optional<UUID> father = persistent.hasUUID(PERSISTENT_FATHER_UUID_KEY)
                ? Optional.of(persistent.getUUID(PERSISTENT_FATHER_UUID_KEY))
                : Optional.empty();
        Optional<UUID> grandParent = resolveGrandParentPlayer(maid, persistent);
        boolean bornMaid = maid.getTags().contains(BORN_MAID_TAG) || mother.isPresent() || father.isPresent();
        ChildLineageData lineage = bornMaid
                ? new ChildLineageData(true, mother, father, grandParent, encodeCustomName(maid))
                : ChildLineageData.EMPTY;
        if (grandParent.isPresent()) {
            persistent.putUUID(PERSISTENT_GRAND_PARENT_UUID_KEY, grandParent.get());
        } else {
            persistent.remove(PERSISTENT_GRAND_PARENT_UUID_KEY);
        }
        if (syncClient) {
            maid.setAndSyncData(ModTaskData.CHILD_LINEAGE_DATA, lineage);
        } else {
            maid.setData(ModTaskData.CHILD_LINEAGE_DATA, lineage);
        }
    }

    private static Optional<UUID> resolveGrandParentPlayer(EntityMaid maid, CompoundTag persistent) {
        if (persistent.hasUUID(PERSISTENT_GRAND_PARENT_UUID_KEY)) {
            return Optional.of(persistent.getUUID(PERSISTENT_GRAND_PARENT_UUID_KEY));
        }

        Optional<UUID> motherUuid = persistent.hasUUID(PERSISTENT_MOTHER_UUID_KEY)
                ? Optional.of(persistent.getUUID(PERSISTENT_MOTHER_UUID_KEY))
                : Optional.empty();
        if (motherUuid.isEmpty() || !(maid.level() instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }

        Entity motherEntity = serverLevel.getEntity(motherUuid.get());
        if (!(motherEntity instanceof EntityMaid motherMaid)) {
            return Optional.empty();
        }

        ChildLineageData motherLineage = motherMaid.getData(ModTaskData.CHILD_LINEAGE_DATA);
        if (motherLineage != null && motherLineage.father().isPresent()) {
            return motherLineage.father();
        }
        CompoundTag motherPersistent = motherMaid.getPersistentData();
        if (motherPersistent.hasUUID(PERSISTENT_FATHER_UUID_KEY)) {
            return Optional.of(motherPersistent.getUUID(PERSISTENT_FATHER_UUID_KEY));
        }
        return Optional.empty();
    }

    private static Component parseComponentJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return Component.Serializer.fromJson(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    public enum GrowthStage {
        INFANT,
        JUVENILE,
        CHILD,
        ADULT;

        public static GrowthStage byName(String name) {
            if ("MIDDLE".equals(name)) {
                return CHILD;
            }
            for (GrowthStage stage : values()) {
                if (stage.name().equals(name)) {
                    return stage;
                }
            }
            return INFANT;
        }
    }
}
