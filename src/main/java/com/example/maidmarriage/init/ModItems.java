package com.example.maidmarriage.init;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.item.DescriptionItem;
import com.example.maidmarriage.item.FlowerTestKitItem;
import com.example.maidmarriage.item.MarriageConsentFormItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模组物品注册表。
 * 这里统一注册婚姻系统、测试工具以及辅助交互相关的全部物品。
 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MaidMarriageMod.MOD_ID);

    public static final RegistryObject<Item> PROPOSAL_RING = ITEMS.register("proposal_ring",
            () -> new DescriptionItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                    "tooltip.maidmarriage.proposal_ring"));

    public static final RegistryObject<Item> YES_PILLOW = ITEMS.register("yes_pillow",
            () -> new DescriptionItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.maidmarriage.yes_pillow"));

    public static final RegistryObject<Item> SUNFLOWER_HAIRPIN = ITEMS.register("sunflower_hairpin",
            () -> new DescriptionItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                    "tooltip.maidmarriage.sunflower_hairpin"));

    public static final RegistryObject<Item> RAINBOW_BOUQUET = ITEMS.register("rainbow_bouquet",
            () -> new DescriptionItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON),
                    "tooltip.maidmarriage.rainbow_bouquet"));

    public static final RegistryObject<Item> LONGING_TESTER = ITEMS.register("longing_tester",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> FLOWER_TEST_KIT = ITEMS.register("flower_test_kit",
            () -> new FlowerTestKitItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.maidmarriage.flower_test_kit"));

    public static final RegistryObject<Item> SAUCE_DUCK = ITEMS.register("sauce_duck",
            () -> new DescriptionItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON),
                    "tooltip.maidmarriage.sauce_duck"));

    public static final RegistryObject<Item> GROWTH_TOOL = ITEMS.register("growth_tool",
            () -> new DescriptionItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.maidmarriage.growth_tool"));

    public static final RegistryObject<Item> BIRTH_TOOL = ITEMS.register("birth_tool",
            () -> new DescriptionItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.maidmarriage.birth_tool"));

    public static final RegistryObject<Item> PREGNANCY_TEST_TOOL = ITEMS.register("pregnancy_test_tool",
            () -> new DescriptionItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.maidmarriage.pregnancy_test_tool"));

    public static final RegistryObject<Item> PREGNANCY_SETTLEMENT_TOOL = ITEMS.register("pregnancy_settlement_tool",
            () -> new DescriptionItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.maidmarriage.pregnancy_settlement_tool"));

    public static final RegistryObject<Item> FAMILY_TREE_TOOL = ITEMS.register("family_tree_tool",
            () -> new DescriptionItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                    "tooltip.maidmarriage.family_tree_tool"));

    public static final RegistryObject<Item> MARRIAGE_CONSENT_FORM = ITEMS.register("marriage_consent_form",
            () -> new MarriageConsentFormItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                    "tooltip.maidmarriage.marriage_consent_form"));

    private ModItems() {
    }
}
