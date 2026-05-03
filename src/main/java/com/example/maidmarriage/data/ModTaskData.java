package com.example.maidmarriage.data;

import com.example.maidmarriage.MaidMarriageMod;
import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import net.minecraft.resources.ResourceLocation;

/**
 * 任务数据键注册：把本模组数据挂载到女仆实体。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public final class ModTaskData {
    public static TaskDataKey<MarriageData> MARRIAGE_DATA;
    public static TaskDataKey<PregnancyData> PREGNANCY_DATA;
    public static TaskDataKey<MaidMoodData> MOOD_DATA;
    public static TaskDataKey<ChildStateData> CHILD_STATE_DATA;
    public static TaskDataKey<ChildLineageData> CHILD_LINEAGE_DATA;
    public static TaskDataKey<RelationshipProgressData> RELATIONSHIP_PROGRESS_DATA;

    private ModTaskData() {
    }

    public static void registerAll(TaskDataRegister register) {
        MARRIAGE_DATA = register.register(
                new ResourceLocation(MaidMarriageMod.MOD_ID, "marriage_data"),
                MarriageData.CODEC
        );
        PREGNANCY_DATA = register.register(
                new ResourceLocation(MaidMarriageMod.MOD_ID, "pregnancy_data"),
                PregnancyData.CODEC
        );
        MOOD_DATA = register.register(
                new ResourceLocation(MaidMarriageMod.MOD_ID, "mood_data"),
                MaidMoodData.CODEC
        );
        CHILD_STATE_DATA = register.register(
                new ResourceLocation(MaidMarriageMod.MOD_ID, "child_state_data"),
                ChildStateData.CODEC
        );
        CHILD_LINEAGE_DATA = register.register(
                new ResourceLocation(MaidMarriageMod.MOD_ID, "child_lineage_data"),
                ChildLineageData.CODEC
        );
        RELATIONSHIP_PROGRESS_DATA = register.register(
                new ResourceLocation(MaidMarriageMod.MOD_ID, "relationship_progress_data"),
                RelationshipProgressData.CODEC
        );
    }
}
