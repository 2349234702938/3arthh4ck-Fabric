package me.earth.earthhack.impl.modules.movement.step;

import me.earth.earthhack.api.module.util.Category;
import me.earth.earthhack.api.setting.Setting;
import me.earth.earthhack.api.setting.settings.BooleanSetting;
import me.earth.earthhack.api.setting.settings.EnumSetting;
import me.earth.earthhack.api.setting.settings.NumberSetting;
import me.earth.earthhack.impl.managers.Managers;
import me.earth.earthhack.impl.modules.movement.step.mode.StepESP;
import me.earth.earthhack.impl.modules.movement.step.mode.StepMode;
import me.earth.earthhack.impl.modules.movement.step.mode.StepOffsetMode;
import me.earth.earthhack.impl.util.helpers.disabling.DisablingModule;
import me.earth.earthhack.impl.util.helpers.render.BlockESPModule;
import me.earth.earthhack.impl.util.math.StopWatch;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.math.Box;

import java.awt.*;
// TODO: DisablingModule, ListenerRender
public class Step extends BlockESPModule
{
    protected final Setting<StepESP> esp = registerBefore(
            new EnumSetting<>("ESP", StepESP.None), super.color);

    protected final Setting<StepMode> mode =
            register(new EnumSetting<>("Mode", StepMode.Normal));

    protected final Setting<Float> height =
            register(new NumberSetting<>("Height", 2.0f, 0.6f, 10.0f));
    protected final Setting<Boolean> useTimer =
            register(new BooleanSetting("UseTimer", false));
    protected final Setting<Double> timer =
            register(new NumberSetting<>("Timer", 1.0, 0.1, 2.0));
    protected final Setting<StepOffsetMode> offsetMode =
            register(new EnumSetting<>("Offsets", StepOffsetMode.New));
    protected final Setting<Boolean> entityStep =
            register(new BooleanSetting("EntityStep", true));
    protected final Setting<Boolean> autoOff =
            register(new BooleanSetting("AutoOff", false));
    protected final Setting<Integer> lagTime =
            register(new NumberSetting<>("LagTime", 0, 0, 250));
    protected final Setting<Boolean> gapple =
            register(new BooleanSetting("Mine-Gapple", false));

    protected final StopWatch breakTimer = new StopWatch();
    protected Box bb;
    protected boolean stepping;
    protected double[] offsets;
    protected double currHeight;
    protected int index;

    protected double x;
    protected double y;
    protected double z;

    public Step()
    {
        super("Step", Category.Movement);
        this.listeners.add(new ListenerStep(this));
        this.listeners.add(new ListenerDestroy(this));
        this.listeners.add(new ListenerBreak(this));
        // this.listeners.add(new ListenerRender(this));
        this.listeners.add(new ListenerWorldClient(this));
        this.listeners.add(new ListenerPreMotionUpdate(this));
        register(new BooleanSetting("Compatibility", false));
        DisablingModule.makeDisablingModule(this);
        super.color.setValue(new Color(0, 255, 255, 76));
        super.outline.setValue(new Color(0, 255, 255));
        mode.addObserver(e -> mc.execute(this::reset));
        this.setData(new StepData(this));
    }

    @Override
    public String getDisplayInfo() {
        return mode.getValue().toString();
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable()
    {
        if (mc.player != null)
        {
            if (mc.player.getVehicle() != null)
            {
                mc.player.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT).setBaseValue(1.0f);
            }

            mc.player.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT).setBaseValue(0.6f);
        }

        Managers.TIMER.reset();
        reset();
    }

    public void onBreak()
    {
        breakTimer.reset();
    }

    protected boolean canStep()
    {
        return !mc.player.isSubmergedInWater()
                && mc.player.isOnGround()
                && !mc.player.isHoldingOntoLadder()
                && !mc.player.input.jumping
                && mc.player.verticalCollision
                && mc.player.fallDistance < 0.1;
    }

    protected void reset() {
        Managers.TIMER.reset();
        stepping = false;
        bb = null;
        offsets = null;
        index = 0;
    }

}
