package com.example.maidmarriage.compat;

import com.example.maidmarriage.data.MaidMoodData;
import com.example.maidmarriage.init.ModItems;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import javax.annotation.Nullable;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 礼物规则表。
 *
 * <p>这里是送礼系统的单一规则源：
 * 客户端用它做预览，服务端也用它做最终结算。
 * 这样礼物表以后再扩时，不会出现“面板显示一套、服务器算另一套”的问题。
 */
public final class GiftTable {
    private GiftTable() {
    }

    public static GiftPreview preview(ItemStack stack, @Nullable EntityMaid maid) {
        RelationStage stage = maid == null ? RelationStage.INITIAL : MaidRelationshipManager.resolveStage(maid);
        MaidMoodData.MoodState mood = maid == null ? MaidMoodData.MoodState.NORMAL : MaidMoodManager.state(maid);
        return preview(stack, stage, mood);
    }

    public static GiftPreview repeatedFlowerAsGenericPreview(@Nullable EntityMaid maid) {
        RelationStage stage = maid == null ? RelationStage.INITIAL : MaidRelationshipManager.resolveStage(maid);
        MaidMoodData.MoodState mood = maid == null ? MaidMoodData.MoodState.NORMAL : MaidMoodManager.state(maid);
        return buildGenericPreview(stage, mood);
    }

    public static GiftPreview preview(ItemStack stack, RelationStage stage, MaidMoodData.MoodState mood) {
        if (stack == null || stack.isEmpty()) {
            return GiftPreview.blocked(GiftCategory.EMPTY);
        }

        GiftCategory category = classify(stack);
        if (stack.is(ModItems.RAINBOW_BOUQUET.get())) {
            return buildRainbowBouquetPreview(mood);
        }
        return switch (category) {
            case FLOWER -> buildFlowerPreview(stage, mood);
            case SWEET -> buildSweetPreview(stage, mood);
            case MEAL -> buildMealPreview(stage, mood);
            case VALUABLE -> buildValuablePreview(stage, mood);
            case GENERIC -> buildGenericPreview(stage, mood);
            case ODD -> buildOddPreview(stage, mood);
            case OFFENSIVE -> buildOffensivePreview();
            case SPECIAL_BLOCKED, EMPTY -> GiftPreview.blocked(category);
        };
    }

    public static boolean isGiftBlocked(ItemStack stack) {
        return classify(stack) == GiftCategory.SPECIAL_BLOCKED;
    }

    public static boolean isFlowerGift(ItemStack stack) {
        return classify(stack) == GiftCategory.FLOWER;
    }

    private static GiftPreview buildFlowerPreview(RelationStage stage, MaidMoodData.MoodState mood) {
        int favor = 1;
        int moodDelta = 3;
        if (stage == RelationStage.WARM) {
            favor++;
        } else if (stage == RelationStage.CLOSE || stage == RelationStage.DATING) {
            favor += 2;
        } else if (stage == RelationStage.MARRIAGE) {
            favor += 1;
        }
        if (isNegativeMood(mood)) {
            moodDelta += 1;
        }
        return new GiftPreview(
                GiftCategory.FLOWER,
                favor >= 3 ? GiftReaction.LOVE : GiftReaction.LIKE,
                "ui.maidmarriage.gift.preview.flower",
                favor,
                moodDelta,
                true,
                true
        );
    }

    private static GiftPreview buildRainbowBouquetPreview(MaidMoodData.MoodState mood) {
        int moodDelta = isNegativeMood(mood) ? 5 : 4;
        return new GiftPreview(
                GiftCategory.FLOWER,
                GiftReaction.LOVE,
                "ui.maidmarriage.gift.preview.flower",
                5,
                moodDelta,
                true,
                true
        );
    }

    private static GiftPreview buildSweetPreview(RelationStage stage, MaidMoodData.MoodState mood) {
        int favor = stage == RelationStage.MARRIAGE ? 1 : 0;
        int moodDelta = isNegativeMood(mood) ? 8 : 5;
        GiftReaction reaction = isNegativeMood(mood) ? GiftReaction.LIKE : GiftReaction.WARM;
        return new GiftPreview(GiftCategory.SWEET, reaction, "ui.maidmarriage.gift.preview.sweet", favor, moodDelta, true, true);
    }

    private static GiftPreview buildMealPreview(RelationStage stage, MaidMoodData.MoodState mood) {
        int favor = stage == RelationStage.MARRIAGE || stage == RelationStage.DATING ? 1 : 0;
        int moodDelta = 4;
        if (isNegativeMood(mood)) {
            moodDelta += 2;
        }
        return new GiftPreview(GiftCategory.MEAL, GiftReaction.NORMAL, "ui.maidmarriage.gift.preview.meal", favor, moodDelta, true, true);
    }

    private static GiftPreview buildValuablePreview(RelationStage stage, MaidMoodData.MoodState mood) {
        int favor = switch (stage) {
            case INITIAL -> 0;
            case WARM -> 1;
            case CLOSE -> 2;
            case DATING -> 2;
            case MARRIAGE -> 1;
        };
        int moodDelta = isNegativeMood(mood) ? 1 : 0;
        GiftReaction reaction = stage == RelationStage.INITIAL ? GiftReaction.HESITANT : GiftReaction.LIKE;
        return new GiftPreview(GiftCategory.VALUABLE, reaction, "ui.maidmarriage.gift.preview.valuable", favor, moodDelta, true, true);
    }

    private static GiftPreview buildGenericPreview(RelationStage stage, MaidMoodData.MoodState mood) {
        int moodDelta = isNegativeMood(mood) ? 2 : 1;
        GiftReaction reaction = stage == RelationStage.INITIAL ? GiftReaction.NORMAL : GiftReaction.WARM;
        return new GiftPreview(GiftCategory.GENERIC, reaction, "ui.maidmarriage.gift.preview.generic", 0, moodDelta, true, true);
    }

    private static GiftPreview buildOddPreview(RelationStage stage, MaidMoodData.MoodState mood) {
        int favor = -1;
        int moodDelta = -2;
        GiftReaction reaction = stage == RelationStage.MARRIAGE ? GiftReaction.DISLIKE : GiftReaction.AWKWARD;
        return new GiftPreview(GiftCategory.ODD, reaction, "ui.maidmarriage.gift.preview.odd", favor, moodDelta, true, true);
    }

    private static GiftPreview buildOffensivePreview() {
        return new GiftPreview(
                GiftCategory.OFFENSIVE,
                GiftReaction.ANGRY,
                "ui.maidmarriage.gift.preview.offensive",
                -2,
                -6,
                true,
                true
        );
    }

    private static GiftCategory classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return GiftCategory.EMPTY;
        }
        if (isBlockedSpecialItem(stack)) {
            return GiftCategory.SPECIAL_BLOCKED;
        }
        if (stack.is(ItemTags.FLOWERS) || stack.is(ModItems.RAINBOW_BOUQUET.get())) {
            return GiftCategory.FLOWER;
        }
        if (isSweetItem(stack)) {
            return GiftCategory.SWEET;
        }
        if (isMealItem(stack)) {
            return GiftCategory.MEAL;
        }
        if (isValuableItem(stack)) {
            return GiftCategory.VALUABLE;
        }
        if (isOffensiveItem(stack)) {
            return GiftCategory.OFFENSIVE;
        }
        if (isOddItem(stack)) {
            return GiftCategory.ODD;
        }
        return GiftCategory.GENERIC;
    }

    private static boolean isBlockedSpecialItem(ItemStack stack) {
        return stack.is(ModItems.PROPOSAL_RING.get())
                || stack.is(ModItems.YES_PILLOW.get())
                || stack.is(ModItems.SUNFLOWER_HAIRPIN.get())
                || stack.is(ModItems.LONGING_TESTER.get())
                || stack.is(ModItems.FLOWER_TEST_KIT.get())
                || stack.is(ModItems.GROWTH_TOOL.get())
                || stack.is(ModItems.BIRTH_TOOL.get())
                || stack.is(ModItems.PREGNANCY_TEST_TOOL.get())
                || stack.is(ModItems.PREGNANCY_SETTLEMENT_TOOL.get())
                || stack.is(ModItems.FAMILY_TREE_TOOL.get())
                || stack.is(ModItems.MARRIAGE_CONSENT_FORM.get());
    }

    private static boolean isSweetItem(ItemStack stack) {
        return stack.is(Items.COOKIE)
                || stack.is(Items.CAKE)
                || stack.is(Items.PUMPKIN_PIE)
                || stack.is(Items.HONEY_BOTTLE)
                || stack.is(Items.SWEET_BERRIES)
                || stack.is(Items.MELON_SLICE)
                || stack.is(Items.APPLE)
                || stack.is(Items.GOLDEN_APPLE)
                || stack.is(Items.GLISTERING_MELON_SLICE);
    }

    private static boolean isMealItem(ItemStack stack) {
        return stack.is(Items.BREAD)
                || stack.is(Items.MILK_BUCKET)
                || stack.is(Items.COOKED_BEEF)
                || stack.is(Items.COOKED_PORKCHOP)
                || stack.is(Items.COOKED_CHICKEN)
                || stack.is(Items.COOKED_MUTTON)
                || stack.is(Items.COOKED_RABBIT)
                || stack.is(Items.BAKED_POTATO)
                || stack.is(Items.CARROT)
                || stack.is(Items.POTATO)
                || stack.is(Items.BEETROOT)
                || stack.is(Items.BEETROOT_SOUP)
                || stack.is(Items.MUSHROOM_STEW)
                || stack.is(Items.RABBIT_STEW)
                || stack.is(Items.SUSPICIOUS_STEW)
                || stack.is(Items.COOKED_COD)
                || stack.is(Items.COOKED_SALMON)
                || stack.is(ModItems.SAUCE_DUCK.get());
    }

    private static boolean isValuableItem(ItemStack stack) {
        return stack.is(Items.DIAMOND)
                || stack.is(Items.EMERALD)
                || stack.is(Items.GOLD_INGOT)
                || stack.is(Items.GOLD_NUGGET)
                || stack.is(Items.IRON_INGOT)
                || stack.is(Items.AMETHYST_SHARD)
                || stack.is(Items.QUARTZ)
                || stack.is(Items.LAPIS_LAZULI)
                || stack.is(Items.NETHERITE_SCRAP);
    }

    private static boolean isOddItem(ItemStack stack) {
        return stack.is(Items.ROTTEN_FLESH)
                || stack.is(Items.SPIDER_EYE)
                || stack.is(Items.FERMENTED_SPIDER_EYE)
                || stack.is(Items.POISONOUS_POTATO)
                || stack.is(Items.PUFFERFISH)
                || stack.is(Items.BONE);
    }

    private static boolean isOffensiveItem(ItemStack stack) {
        return stack.is(Items.POISONOUS_POTATO)
                || stack.is(Items.ROTTEN_FLESH)
                || stack.is(Items.FERMENTED_SPIDER_EYE)
                || stack.is(Items.PUFFERFISH);
    }

    private static boolean isNegativeMood(MaidMoodData.MoodState mood) {
        return mood == MaidMoodData.MoodState.DEPRESSED || mood == MaidMoodData.MoodState.GENERAL;
    }

    public enum GiftCategory {
        EMPTY,
        FLOWER,
        SWEET,
        MEAL,
        VALUABLE,
        GENERIC,
        ODD,
        OFFENSIVE,
        SPECIAL_BLOCKED
    }

    public enum GiftReaction {
        LOVE,
        LIKE,
        WARM,
        NORMAL,
        HESITANT,
        AWKWARD,
        DISLIKE,
        ANGRY,
        BLOCKED
    }

    public record GiftPreview(
            GiftCategory category,
            GiftReaction reaction,
            String detailKey,
            int favorabilityDelta,
            int moodDelta,
            boolean allowed,
            boolean consumesItem) {
        private static GiftPreview blocked(GiftCategory category) {
            return new GiftPreview(category, GiftReaction.BLOCKED, "ui.maidmarriage.gift.preview.blocked", 0, 0, false, false);
        }
    }
}
