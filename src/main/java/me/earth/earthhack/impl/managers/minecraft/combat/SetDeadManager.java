package me.earth.earthhack.impl.managers.minecraft.combat;

import io.netty.util.internal.ConcurrentSet;
import me.earth.earthhack.api.cache.SettingCache;
import me.earth.earthhack.api.event.bus.EventListener;
import me.earth.earthhack.api.event.bus.SubscriberImpl;
import me.earth.earthhack.api.setting.Setting;
import me.earth.earthhack.api.setting.settings.BooleanSetting;
import me.earth.earthhack.api.setting.settings.NumberSetting;
import me.earth.earthhack.api.util.interfaces.Globals;
import me.earth.earthhack.impl.event.events.misc.UpdateEvent;
import me.earth.earthhack.impl.event.events.network.PacketEvent;
import me.earth.earthhack.impl.event.events.network.WorldClientEvent;
import me.earth.earthhack.impl.managers.Managers;
import me.earth.earthhack.impl.managers.minecraft.combat.util.CustomEntityTime;
import me.earth.earthhack.impl.managers.minecraft.combat.util.EntityTime;
import me.earth.earthhack.impl.managers.minecraft.combat.util.SoundObserver;
import me.earth.earthhack.impl.modules.Caches;
import me.earth.earthhack.impl.modules.client.management.Management;
import me.earth.earthhack.impl.util.math.MathUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages entities that have been set dead manually.
 */
public class SetDeadManager extends SubscriberImpl implements Globals
{
    private static final SettingCache
        <Integer, NumberSetting<Integer>, Management> DEATH_TIME =
        Caches.getSetting(Management.class, Setting.class, "DeathTime", 500);
    private static final SettingCache
        <Boolean, BooleanSetting, Management> SOUND_REMOVE =
        Caches.getSetting(Management.class, BooleanSetting.class, "SoundRemove", true);

    private final Map<Integer, EntityTime> killed;
    private final Set<SoundObserver> observers;

    public SetDeadManager()
    {
        this.observers = new ConcurrentSet<>();
        this.killed    = new ConcurrentHashMap<>();

        this.listeners.add(
            new EventListener<PacketEvent.Receive<PlaySoundS2CPacket>>
                (PacketEvent.Receive.class,
                        Integer.MAX_VALUE,
                        PlaySoundS2CPacket.class)
        {
            @Override
            public void invoke
                    (PacketEvent.Receive<PlaySoundS2CPacket> event)
            {
                PlaySoundS2CPacket p = event.getPacket();
                if (p.getCategory() == SoundCategory.BLOCKS
                        && p.getSound() == SoundEvents.ENTITY_GENERIC_EXPLODE
                        && shouldRemove())
                {
                    Vec3d pos = new Vec3d(p.getX(), p.getY(), p.getZ());
                    mc.execute(() ->
                    {
                        //11.0f and not 12.0f, because distance inaccuracies (?)
                        removeCrystals(pos, 11.0f, Managers.ENTITIES.getEntities());
                        for (SoundObserver observer : observers)
                        {
                            // TODO: async observers
                            if (observer.shouldBeNotified())
                            {
                                observer.onChange(p);
                            }
                        }
                    });
                }
            }
        });
        this.listeners.add(
            new EventListener<PacketEvent.Receive<EntitiesDestroyS2CPacket>>
                (PacketEvent.Receive.class,
                        Integer.MAX_VALUE,
                        EntitiesDestroyS2CPacket.class)
        {
            @Override
            public void invoke
                    (PacketEvent.Receive<EntitiesDestroyS2CPacket> event)
            {
                // With this on the main thread there's no reason
                // why we need all the concurrency stuff...?
                mc.execute(() ->
                {
                    for (int id : event.getPacket().getEntityIds())
                    {
                        confirmKill(id);
                    }
                });
            }
        });
        this.listeners.add(
            new EventListener<>(UpdateEvent.class)
        {
            @Override
            public void invoke(UpdateEvent event)
            {
                updateKilled();
            }
        });
        this.listeners.add(new EventListener<>
                (WorldClientEvent.Load.class)
        {
            @Override
            public void invoke(WorldClientEvent.Load event)
            {
                clear();
            }
        });
        this.listeners.add(new EventListener<>
                (WorldClientEvent.Unload.class)
        {
            @Override
            public void invoke(WorldClientEvent.Unload event)
            {
                clear();
            }
        });
    }

    public Entity getEntity(int id)
    {
        EntityTime time = killed.get(id);
        if (time != null)
        {
            return time.getEntity();
        }

        return null;
    }

    public void setDeadCustom(Entity entity, long t)
    {
        EntityTime time = killed.get(entity.getId());
        if (time instanceof CustomEntityTime)
        {
            time.getEntity().remove(Entity.RemovalReason.KILLED);
            time.reset();
        }
        else
        {
            entity.remove(Entity.RemovalReason.KILLED);
            killed.put(entity.getId(), new CustomEntityTime(entity, t));
        }
    }

    public void revive(int id)
    {
        EntityTime time = killed.remove(id);
        if (time != null && time.isValid())
        {
            Entity entity = time.getEntity();
            entity.unsetRemoved();
            mc.world.addEntity(entity);
            entity.unsetRemoved();
        }
    }

    /**
     * Checks all killed entities. If they have been killed
     * for longer than the value of the given deathTime setting
     * and the kill hasn't been confirmed yet they will be added back
     * to the world.
     */
    public void updateKilled()
    {
        for (Map.Entry<Integer, EntityTime> entry : killed.entrySet())
        {
            if (!entry.getValue().isValid())
            {
                entry.getValue().getEntity().remove(Entity.RemovalReason.KILLED);
                killed.remove(entry.getKey());
            }
            else if (entry.getValue().passed(DEATH_TIME.getValue()))
            {
                Entity entity = entry.getValue().getEntity();
                entity.unsetRemoved();
                if (!Managers.ENTITIES.getEntities().contains(entity))
                {
                    mc.world.addEntity(entity);
                    entity.unsetRemoved();
                    killed.remove(entry.getKey());
                }
            }
        }
    }

    /**
     * Kills all EndCrystals in the given EntityList that
     * lie within the given range (radius) around the BlockPos.
     *
     * @param pos the center.
     * @param range maxDistance to the center.
     * @param entities the Entities to check.
     */
    public void removeCrystals(Vec3d pos, float range, List<Entity> entities)
    {
        for (Entity entity : entities)
        {
            if (entity instanceof EndCrystalEntity
                    && entity.squaredDistanceTo(pos.x, pos.y, pos.z)
                            <= MathUtil.square(range))
            {
                setDead(entity);
            }
        }
    }

    /**
     * Calls {@link Entity#remove(Entity.RemovalReason)} for the given entity and
     * adds it to the killed list. If the kill isn't confirmed
     * within the deathTime by {@link SetDeadManager#confirmKill(int)}
     * the entity will be added back to the world.
     *
     * @param entity the entity to kill.
     */
    public void setDead(Entity entity)
    {
        EntityTime time = killed.get(entity.getId());
        if (time != null)
        {
            time.getEntity().remove(Entity.RemovalReason.KILLED);
            time.reset();
        }
        else if (entity.isAlive())
        {
            entity.remove(Entity.RemovalReason.KILLED);
            killed.put(entity.getId(), new EntityTime(entity));
        }
    }

    /**
     * Confirms that the entity belonging to the given
     * EntityID has indeed been killed.
     *
     * @param id the id to confirm.
     */
    public void confirmKill(int id)
    {
        EntityTime time = killed.get(id);
        if (time != null)
        {
            time.setValid(false);
            time.getEntity().remove(Entity.RemovalReason.KILLED);
        }
    }

    public boolean passedDeathTime(Entity entity, long deathTime)
    {
        return passedDeathTime(entity.getId(), deathTime);
    }

    public boolean passedDeathTime(int id, long deathTime)
    {
        if (deathTime <= 0)
        {
            return true;
        }

        EntityTime time = killed.get(id);
        if (time != null && time.isValid())
        {
            return time.passed(deathTime);
        }

        return true;
    }

    /**
     * Clears all killed entities.
     */
    public void clear()
    {
        killed.clear();
    }

    /**
     * Adds a SoundObserver. If any of the added SoundObservers
     * {@link SoundObserver#shouldRemove()} methods returns
     * <tt>true</tt> crystals will be removed when SPacketSoundEffects
     * arrive. The SoundRemover then will be notified.
     *
     * @param observer the observer to add.
     */
    public void addObserver(SoundObserver observer)
    {
        this.observers.add(observer);
    }

    /**
     * {@see SetDeadManager#addObserver(SoundObserver)}.
     * @param observer the observer to remove.
     */
    public void removeObserver(SoundObserver observer)
    {
        this.observers.remove(observer);
    }

    private boolean shouldRemove()
    {
        if (!SOUND_REMOVE.getValue())
        {
            return false;
        }

        for (SoundObserver soundObserver : observers)
        {
            if (soundObserver.shouldRemove())
            {
                return true;
            }
        }

        return false;
    }

}
