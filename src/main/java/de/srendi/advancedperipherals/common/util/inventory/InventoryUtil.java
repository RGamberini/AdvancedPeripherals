package de.srendi.advancedperipherals.common.util.inventory;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import de.srendi.advancedperipherals.AdvancedPeripherals;
import de.srendi.advancedperipherals.common.addons.computercraft.owner.IPeripheralOwner;
import de.srendi.advancedperipherals.common.util.CoordUtil;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class InventoryUtil {

    private InventoryUtil() {
    }

    public static IItemHandler extractHandler(@Nullable Object object) {
        if (object instanceof ICapabilityProvider capabilityProvider) {
            LazyOptional<IItemHandler> cap = capabilityProvider.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
            if (cap.isPresent())
                return cap.orElseThrow(NullPointerException::new);
        }
        if (object instanceof IItemHandler itemHandler)
            return itemHandler;
        if (object instanceof Container container)
            return new InvWrapper(container);
        return null;
    }

    public static int moveItem(IItemHandler inventoryFrom, IItemHandler inventoryTo, ItemFilter filter) {
        AdvancedPeripherals.LOGGER.error("C");
        if (inventoryFrom == null) return 0;

        int fromSlot = filter.getFromSlot();
        int toSlot = filter.getToSlot();

        int amount = filter.getCount();
        int transferableAmount = 0;

        // The logic changes with storage systems since these systems do not have slots
        if (inventoryFrom instanceof IStorageSystemItemHandler storageSystemHandler) {
            AdvancedPeripherals.LOGGER.error("D1");
            for (int i = toSlot == -1 ? 0 : toSlot; i < (toSlot == -1 ? inventoryTo.getSlots() : toSlot + 1); i++) {
                AdvancedPeripherals.LOGGER.error("TOP OF FOR LOOP");
                AdvancedPeripherals.LOGGER.error(filter.getNbt());
                ItemStack extracted = storageSystemHandler.extractItem(filter, true);
                ItemStack inserted;
                if (toSlot == -1) {
                    inserted = ItemHandlerHelper.insertItem(inventoryTo, extracted, false);
                } else {
                    inserted = inventoryTo.insertItem(toSlot, extracted, false);
                }
                AdvancedPeripherals.LOGGER.error(String.format("amount: %d\ntransferableAmount: %d\nextracted.getCount(): %d\ninserted.getCount(): %d\n", amount, transferableAmount, extracted.getCount(), inserted.getCount()));
                amount -= inserted.getCount();
                transferableAmount += storageSystemHandler.extractItem(filter, false).getCount();
                AdvancedPeripherals.LOGGER.error(String.format("amount: %d\ntransferableAmount: %d\nextracted.getCount(): %d\ninserted.getCount(): %d\n", amount, transferableAmount, extracted.getCount(), inserted.getCount()));
                if (transferableAmount >= filter.getCount())
                    break;
            }
            return transferableAmount;
        }

        if (inventoryTo instanceof IStorageSystemItemHandler storageSystemHandler) {
            AdvancedPeripherals.LOGGER.error("D2");
            for (int i = fromSlot == -1 ? 0 : fromSlot; i < (fromSlot == -1 ? inventoryFrom.getSlots() : fromSlot + 1); i++) {
                if (filter.test(inventoryFrom.getStackInSlot(i))) {
                    ItemStack extracted = inventoryFrom.extractItem(i, amount - transferableAmount, true);
                    ItemStack inserted = storageSystemHandler.insertItem(toSlot, extracted, false);

                    amount -= inserted.getCount();
                    transferableAmount += inventoryFrom.extractItem(i, extracted.getCount() - inserted.getCount(), false).getCount();
                    if (transferableAmount >= filter.getCount())
                        break;
                }
            }
            return transferableAmount;
        }
        AdvancedPeripherals.LOGGER.error("D3");
        for (int i = fromSlot == -1 ? 0 : fromSlot; i < (fromSlot == -1 ? inventoryFrom.getSlots() : fromSlot + 1); i++) {
            if (filter.test(inventoryFrom.getStackInSlot(i))) {
                ItemStack extracted = inventoryFrom.extractItem(i, amount - transferableAmount, true);
                ItemStack inserted;
                if (toSlot == -1) {
                    inserted = ItemHandlerHelper.insertItem(inventoryTo, extracted, false);
                } else {
                    inserted = inventoryTo.insertItem(toSlot, extracted, false);
                }
                amount -= inserted.getCount();
                transferableAmount += inventoryFrom.extractItem(i, extracted.getCount() - inserted.getCount(), false).getCount();
                if (transferableAmount >= filter.getCount())
                    break;
            }
        }
        AdvancedPeripherals.LOGGER.error("E");
        return transferableAmount;
    }

    public static int moveFluid(IFluidHandler inventoryFrom, IFluidHandler inventoryTo, FluidFilter filter) {
        if (inventoryFrom == null) return 0;

        int amount = filter.getCount();
        int transferableAmount = 0;

        // The logic changes with storage systems since these systems do not have slots
        if (inventoryFrom instanceof IStorageSystemFluidHandler storageSystemHandler) {
            FluidStack extracted = storageSystemHandler.drain(filter, IFluidHandler.FluidAction.SIMULATE);
            int inserted = inventoryTo.fill(extracted, IFluidHandler.FluidAction.EXECUTE);

            transferableAmount += storageSystemHandler.drain(filter.setCount(inserted), IFluidHandler.FluidAction.EXECUTE).getAmount();

            return transferableAmount;
        }

        if (inventoryTo instanceof IStorageSystemFluidHandler storageSystemHandler) {
            if (filter.test(inventoryFrom.getFluidInTank(0))) {
                FluidStack toExtract = inventoryFrom.getFluidInTank(0).copy();
                toExtract.setAmount(amount);
                FluidStack extracted = inventoryFrom.drain(toExtract, IFluidHandler.FluidAction.SIMULATE);
                if(extracted.isEmpty())
                    return 0;
                int inserted = storageSystemHandler.fill(extracted, IFluidHandler.FluidAction.EXECUTE);

                extracted.setAmount(inserted);
                transferableAmount += inventoryFrom.drain(extracted, IFluidHandler.FluidAction.EXECUTE).getAmount();
            }

            return transferableAmount;
        }

        return transferableAmount;
    }


    @Nullable
    public static IItemHandler getHandlerFromName(@NotNull IComputerAccess access, String name) throws LuaException {
        IPeripheral location = access.getAvailablePeripheral(name);
        if (location == null)
            return null;

        return extractHandler(location.getTarget());
    }

    @Nullable
    public static IItemHandler getHandlerFromDirection(@NotNull String direction, @NotNull IPeripheralOwner owner) throws LuaException {
        Level level = owner.getLevel();
        Objects.requireNonNull(level);
        Direction relativeDirection = CoordUtil.getDirection(owner.getOrientation(), direction);
        BlockEntity target = level.getBlockEntity(owner.getPos().relative(relativeDirection));
        if (target == null)
            return null;

        return extractHandler(target);
    }
}
