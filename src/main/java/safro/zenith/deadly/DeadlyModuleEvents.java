package safro.zenith.deadly;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import safro.zenith.deadly.commands.CategoryCheckCommand;
import safro.zenith.deadly.commands.LootifyCommand;
import safro.zenith.deadly.commands.ModifierCommand;
import safro.zenith.deadly.commands.RarityCommand;
import safro.zenith.deadly.loot.LootCategory;
import safro.zenith.deadly.loot.affix.Affix;
import safro.zenith.deadly.loot.affix.AffixHelper;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;

public class DeadlyModuleEvents {

    public static void init() {
        CommandRegistrationCallback.EVENT.register(((dispatcher, dedicated) -> {
            CategoryCheckCommand.register(dispatcher);
            LootifyCommand.register(dispatcher);
            ModifierCommand.register(dispatcher);
            RarityCommand.register(dispatcher);
        }));
    }

    public static Multimap<Attribute, AttributeModifier> sortModifiers(ItemStack stack, EquipmentSlot equipmentSlot, Multimap<Attribute,AttributeModifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty() || FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) return null;
        Multimap<Attribute, AttributeModifier> map = TreeMultimap.create(Comparator.comparing(Registry.ATTRIBUTE::getKey), (v1, v2) -> {
            int compOp = Integer.compare(v1.getOperation().ordinal(), v2.getOperation().ordinal());
            int compValue = Double.compare(v2.getAmount(), v1.getAmount());
            return compOp == 0 ? compValue == 0 ? v1.getName().compareTo(v2.getName()) : compValue : compOp;
        });
        for (Map.Entry<Attribute, AttributeModifier> ent : modifiers.entries()) {
            if (ent.getKey() != null && ent.getValue() != null) map.put(ent.getKey(), ent.getValue());
            else DeadlyModule.LOGGER.debug("Detected broken attribute modifier entry on item {}.  Attr={}, Modif={}", stack, ent.getKey(), ent.getValue());
        }
        return map;
    }

    public static void affixModifiers(ItemStack stack, EquipmentSlot equipmentSlot, Multimap<Attribute,AttributeModifier> modifiers) {
        if (stack.hasTag()) {
            Map<Affix, Float> affixes = AffixHelper.getAffixes(stack);
            affixes.forEach((afx, lvl) -> afx.addModifiers(stack, lvl, equipmentSlot, modifiers::put));
        }
    }

    private static final Set<Float> values = ImmutableSet.of(0.1F, 0.2F, 0.25F, 0.33F, 0.5F, 1.0F, 1.1F, 1.2F, 1.25F, 1.33F, 1.5F, 2.0F, 2.1F, 2.25F, 2.33F, 2.5F, 3F);

    /**
     * This event handler makes the Draw Speed attribute work as intended.
     * Modifiers targetting this attribute should use the MULTIPLY_BASE operation.
     */
    public static int drawSpeed(LivingEntity e, ItemStack item, int currentTicks) {
        if (e instanceof Player player) {
            double t = player.getAttribute(DeadlyModule.DRAW_SPEED).getValue() - 1;
            if (t == 0 || !isRanged(item)) return currentTicks;
            float clamped = values.stream().filter(f -> f >= t).min(Float::compareTo).orElse(3F);
            while (clamped > 0) {
                if (e.tickCount % (int) Math.floor(1 / Math.min(1, t)) == 0) currentTicks--;
                clamped--;
            }
        }
        return currentTicks;
    }

    private static boolean isRanged(ItemStack item) {
        if (LootCategory.forItem(item) == null) {
            return false;
        }
        return LootCategory.forItem(item).isRanged();
    }
}
