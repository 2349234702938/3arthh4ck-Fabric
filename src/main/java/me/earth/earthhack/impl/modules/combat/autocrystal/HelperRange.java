package me.earth.earthhack.impl.modules.combat.autocrystal;

import me.earth.earthhack.api.util.interfaces.Globals;
import me.earth.earthhack.impl.managers.Managers;
import me.earth.earthhack.impl.modules.combat.autocrystal.util.SmartRangeUtil;
import me.earth.earthhack.impl.util.math.DistanceUtil;
import me.earth.earthhack.impl.util.math.MathUtil;
import me.earth.earthhack.impl.util.math.rotation.RotationUtil;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class HelperRange implements Globals {
    private final AutoCrystal module;

    public HelperRange(AutoCrystal module) {
        this.module = module;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isCrystalInRange(Entity crystal) {
        return isCrystalInRange(crystal.getX(), crystal.getY(), crystal.getZ(), 0);
    }

    public boolean isCrystalInRangeOfLastPosition(Entity crystal) {
        return isCrystalInRange(crystal.getX(), crystal.getY(), crystal.getZ(),
                                Managers.POSITION.getX(),
                                Managers.POSITION.getY(),
                                Managers.POSITION.getZ());
    }

    public boolean isCrystalInRange(
        double crystalX, double crystalY, double crystalZ, int ticks) {
        if (module.smartBreakTrace.getValue()
            && isOutsideBreakTrace(crystalX, crystalY, crystalZ, ticks)) {
            return false;
        }

        if (module.ncpRange.getValue()) {
            Entity breaker = RotationUtil.getRotationPlayer();
            double breakerX = breaker.getX() + breaker.getVelocity().getX() * ticks;
            double breakerY = breaker.getY() + breaker.getVelocity().getY() * ticks;
            double breakerZ = breaker.getZ() + breaker.getVelocity().getZ() * ticks;
            return SmartRangeUtil.isInStrictBreakRange(
                crystalX, crystalY, crystalZ,
                MathUtil.square(module.breakRange.getValue()),
                breakerX, breakerY, breakerZ);
        }

        return SmartRangeUtil.isInSmartRange(
            crystalX, crystalY, crystalZ, RotationUtil.getRotationPlayer(),
            MathUtil.square(module.breakRange.getValue()), ticks);
    }

    public boolean isCrystalInRange(
        double crystalX, double crystalY, double crystalZ,
        double breakerX, double breakerY, double breakerZ) {
        if (module.smartBreakTrace.getValue()
            && !isCrystalInBreakTrace(crystalX, crystalY, crystalZ,
                                      breakerX, breakerY, breakerZ)) {
            return false;
        }

        if (module.ncpRange.getValue()) {
            return SmartRangeUtil.isInStrictBreakRange(
                crystalX, crystalY, crystalZ,
                MathUtil.square(module.breakRange.getValue()),
                breakerX, breakerY, breakerZ);
        }

        return DistanceUtil.distanceSq(crystalX, crystalY, crystalZ,
                                       breakerX, breakerY, breakerZ)
            < MathUtil.square(module.breakRange.getValue());
    }

    public boolean isCrystalOutsideNegativeRange(BlockPos pos) {
        int negativeTicks = module.negativeTicks.getValue();
        if (negativeTicks == 0) {
            return false;
        }

        if (module.negativeBreakTrace.getValue()
            && isOutsideBreakTrace(pos, negativeTicks)) {
            return true;
        }

        if (module.ncpRange.getValue()) {
            Entity breaker = RotationUtil.getRotationPlayer();
            double breakerX = breaker.getX() + breaker.getVelocity().getX() * negativeTicks;
            double breakerY = breaker.getY() + breaker.getVelocity().getY() * negativeTicks;
            double breakerZ = breaker.getZ() + breaker.getVelocity().getZ() * negativeTicks;
            return !SmartRangeUtil.isInStrictBreakRange(
                pos.getX() + 0.5f, pos.getY() + 1, pos.getZ() + 0.5,
                MathUtil.square(module.breakRange.getValue()),
                breakerX, breakerY, breakerZ);
        }

        return !SmartRangeUtil.isInSmartRange(
            pos, RotationUtil.getRotationPlayer(),
            MathUtil.square(module.breakRange.getValue()),
            negativeTicks);
    }

    public boolean isOutsideBreakTrace(BlockPos pos, int ticks) {
        return isOutsideBreakTrace(
            pos.getX() + 0.5f, pos.getY() + 1, pos.getZ() + 0.5f, ticks);
    }

    public boolean isOutsideBreakTrace(double x, double y, double z, int ticks)
    {
        Entity breaker = RotationUtil.getRotationPlayer();
        double breakerX = breaker.getX() + breaker.getVelocity().getX() * ticks;
        double breakerY = breaker.getY() + breaker.getVelocity().getY() * ticks;
        double breakerZ = breaker.getZ() + breaker.getVelocity().getZ() * ticks;
        return !isCrystalInBreakTrace(x, y, z, breakerX, breakerY, breakerZ);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isCrystalInBreakTrace(double crystalX,
                                         double crystalY,
                                         double crystalZ,
                                         double breakerX,
                                         double breakerY,
                                         double breakerZ)
    {
        BlockPos crystalPos = BlockPos.ofFloored(crystalX, crystalY, crystalZ);

        return DistanceUtil.distanceSq(crystalX, crystalY, crystalZ,
                                       breakerX, breakerY, breakerZ)
                < MathUtil.square(module.breakTrace.getValue())
            || mc.world.raycastBlock(
            new Vec3d(breakerX, breakerY
                + RotationUtil.getRotationPlayer().getEyeHeight(RotationUtil.getRotationPlayer().getPose()), breakerZ),
            new Vec3d(crystalX, crystalY + 1.7, crystalZ),
            crystalPos,
            mc.world.getBlockState(crystalPos).getRaycastShape(mc.world, crystalPos),
            mc.world.getBlockState(crystalPos)) == null; // probably retarded
    }
}
