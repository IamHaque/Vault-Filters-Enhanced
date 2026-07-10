package net.joseph.vaultfilters.mixin.compat.create;

import com.simibubi.create.content.logistics.filter.AbstractFilterMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractFilterMenu.class)
public interface AbstractFilterMenuInvoker {
    @Invoker(value = "saveData", remap = false)
    void callSaveData(ItemStack contentHolder);
}
