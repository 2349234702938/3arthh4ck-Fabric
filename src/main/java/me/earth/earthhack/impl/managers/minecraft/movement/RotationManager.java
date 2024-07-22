package me.earth.earthhack.impl.managers.minecraft.movement;

import me.earth.earthhack.api.event.bus.EventListener;
import me.earth.earthhack.api.event.bus.SubscriberImpl;
import me.earth.earthhack.api.event.events.Stage;
import me.earth.earthhack.api.util.interfaces.Globals;
import me.earth.earthhack.impl.core.ducks.entity.IClientPlayerEntity;
import me.earth.earthhack.impl.event.events.network.MotionUpdateEvent;
import me.earth.earthhack.impl.event.events.network.PacketEvent;
import me.earth.earthhack.impl.managers.Managers;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.util.math.MathHelper;

/**
 * Manages the last rotation that has been
 * reported to or, via SPacketPlayerPosLook,
 * set by the server.
 */
@SuppressWarnings({"unused", "ConstantConditions"})
public class RotationManager extends SubscriberImpl implements Globals
{
    private final PositionManager positionManager;
    private boolean blocking;
    private volatile float last_yaw;
    private volatile float last_pitch;
    private float renderYaw;
    private float renderPitch;
    private float renderYawOffset;
    private float prevYaw;
    private float prevPitch;
    private float prevRenderYawOffset;
    private float prevRotationYawHead;
    private float rotationYawHead;
    private int ticksExisted;

    public RotationManager()
    {
        this(Managers.POSITION);
    }

    /** Constructs a new RotationManager. */
    public RotationManager(PositionManager positionManager)
    {
        this.positionManager = positionManager;
        this.listeners.add(new EventListener<
                PacketEvent.Receive<EntityPositionS2CPacket>>
                (PacketEvent.Receive.class,
                        Integer.MAX_VALUE,
                        EntityPositionS2CPacket.class)
        {
            @Override
            public void invoke(PacketEvent.Receive<EntityPositionS2CPacket> event)
            {
                EntityPositionS2CPacket packet = event.getPacket();
                float yaw = packet.getYaw();
                float pitch = packet.getPitch();

                if (mc.player != null)
                {
                    setServerRotations(yaw, pitch);
                }
            }
        });
        this.listeners.add(new EventListener<>
                (MotionUpdateEvent.class, Integer.MIN_VALUE)
        {
            @Override
            public void invoke(MotionUpdateEvent event)
            {
                if (event.getStage() == Stage.PRE)
                {
                    set(event.getYaw(), event.getPitch());
                }
            }
        });
        // Keep all packets here, even the not rotating ones!
        // they set onGround for the PositionManager!
        this.listeners.add(new EventListener<PacketEvent.Post<PlayerMoveC2SPacket>>
                (PacketEvent.Post.class, PlayerMoveC2SPacket.class)
        {
            @Override
            public void invoke(PacketEvent.Post<PlayerMoveC2SPacket> event)
            {
                readC2SPacket(event.getPacket());
            }
        });
        this.listeners.add(new EventListener<
                PacketEvent.Post<PlayerMoveC2SPacket.PositionAndOnGround>>
                (PacketEvent.Post.class, PlayerMoveC2SPacket.PositionAndOnGround.class)
        {
            @Override
            public void invoke(PacketEvent.Post<PlayerMoveC2SPacket.PositionAndOnGround> event)
            {
                readC2SPacket(event.getPacket());
            }
        });
        this.listeners.add(new EventListener<
                PacketEvent.Post<PlayerMoveC2SPacket.LookAndOnGround>>
                (PacketEvent.Post.class, PlayerMoveC2SPacket.LookAndOnGround.class)
        {
            @Override
            public void invoke(PacketEvent.Post<PlayerMoveC2SPacket.LookAndOnGround> event)
            {
                readC2SPacket(event.getPacket());
            }
        });
        this.listeners.add(new EventListener<
                PacketEvent.Post<PlayerMoveC2SPacket.Full>>
                (PacketEvent.Post.class, PlayerMoveC2SPacket.Full.class)
        {
            @Override
            public void invoke
                    (PacketEvent.Post<PlayerMoveC2SPacket.Full> event)
            {
                readC2SPacket(event.getPacket());
            }
        });
    }

    /**
     * @return the last yaw reported to/by the server.
     */
    public float getServerYaw()
    {
        return last_yaw;
    }

    /**
     * @return the last pitch reported to/by the server.
     */
    public float getServerPitch()
    {
        return last_pitch;
    }

    /**
     * Makes {@link RotationManager#isBlocking()} return the given
     * argument, that won't prevent other modules from
     * spoofing rotations but they can check it. This
     * can be marked as true by modules which think they are
     * important like Surround, to prevent other modules
     * from rotating.
     *
     * Remember to set this to false after
     * the Rotations have been sent.
     *
     * @param blocking blocks rotation spoofing
     */
    public void setBlocking(boolean blocking)
    {
        this.blocking = blocking;
    }

    /**
     * Indicates that a module is currently
     * spoofing rotations, and they shouldn't
     * be spoofed by others.
     *
     * @return <tt>true</tt> if blocking.
     */
    public boolean isBlocking()
    {
        return blocking;
    }

    public void setServerRotations(float yaw, float pitch)
    {
        last_yaw   = yaw;
        last_pitch = pitch;
    }

    /**
     * Reads yaw and pitch from a packet.
     *
     * @param packetIn the packet to read.
     */
    public void readC2SPacket(PlayerMoveC2SPacket packetIn)
    {
        // Prevents us from sending the same rotations again, if we spoofed
        // them with the packet instead of MotionUpdateEvent.
        ((IClientPlayerEntity) mc.player)
                .earthhack$setLastReportedYaw(packetIn.getYaw(
                        ((IClientPlayerEntity) mc.player).earthhack$getLastReportedYaw()));
        ((IClientPlayerEntity) mc.player)
                .earthhack$setLastReportedPitch(packetIn.getPitch(
                        ((IClientPlayerEntity) mc.player).earthhack$getLastReportedPitch()));

        setServerRotations(packetIn.getYaw(last_yaw), packetIn.getPitch(last_pitch));
        // set(packetIn.getYaw(renderYaw), packetIn.getPitch(renderPitch));
        positionManager.setOnGround(packetIn.isOnGround());
    }

    private void set(float yaw, float pitch)
    {
        if (mc.player.age == ticksExisted)
        {
            return;
        }

        ticksExisted = mc.player.age;
        prevYaw      = renderYaw;
        prevPitch    = renderPitch;

        prevRenderYawOffset = renderYawOffset;
        renderYawOffset     = getRenderYawOffset(yaw, prevRenderYawOffset);

        prevRotationYawHead = rotationYawHead;
        rotationYawHead     = yaw;

        renderYaw   = yaw;
        renderPitch = pitch;
    }

    public float getRenderYaw()
    {
        return renderYaw;
    }

    public float getRenderPitch()
    {
        return renderPitch;
    }

    public float getRotationYawHead()
    {
        return rotationYawHead;
    }

    public float getRenderYawOffset()
    {
        return renderYawOffset;
    }

    public float getPrevYaw()
    {
        return prevYaw;
    }

    public float getPrevPitch()
    {
        return prevPitch;
    }

    public float getPrevRotationYawHead()
    {
        return prevRotationYawHead;
    }

    public float getPrevRenderYawOffset()
    {
        return prevRenderYawOffset;
    }

    private float getRenderYawOffset(float yaw, float offsetIn)
    {
        float result = offsetIn;
        float offset;

        double xDif = mc.player.getX() - mc.player.prevX;
        double zDif = mc.player.getY() - mc.player.prevZ;

        if (xDif * xDif + zDif * zDif > 0.0025000002f)
        {
            offset = (float) MathHelper.atan2(zDif, xDif) * 57.295776f - 90.0f;
            float wrap = MathHelper.abs(MathHelper.wrapDegrees(yaw) - offset);
            if (95.0F < wrap && wrap < 265.0F)
            {
                result = offset - 180.0F;
            }
            else
            {
                result = offset;
            }
        }

        if (mc.player.handSwingProgress > 0.0F)
        {
            result = yaw;
        }

        result = offsetIn + MathHelper.wrapDegrees(result - offsetIn) * 0.3f;
        offset = MathHelper.wrapDegrees(yaw - result);

        if (offset < -75.0f)
        {
            offset = -75.0f;
        }
        else if (offset >= 75.0f)
        {
            offset = 75.0f;
        }

        result = yaw - offset;
        if (offset * offset > 2500.0f)
        {
            result += offset * 0.2f;
        }

        return result;
    }

}

