package me.earth.earthhack.impl.core.mixins.world;

import me.earth.earthhack.impl.core.ducks.world.IChunk;
import me.earth.earthhack.impl.util.misc.collections.CollectionUtil;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayDeque;
import java.util.Deque;

@Mixin(Chunk.class)
public abstract class MixinChunk implements IChunk
{
    @Unique private final Deque<Runnable> postHoleCompilationTasks = new ArrayDeque<>();
    @Unique private boolean compilingHoles = false;
    @Unique private int holeVersion;

    @Override
    public void setCompilingHoles(boolean compilingHoles)
    {
        this.compilingHoles = compilingHoles;
        if (!compilingHoles)
        {
            CollectionUtil.emptyQueue(postHoleCompilationTasks);
        }
    }

    @Override
    public void addHoleTask(Runnable task)
    {
        if (isCompilingHoles())
        {
            postHoleCompilationTasks.add(task);
        }
        else
        {
            task.run();
        }
    }

    @Override
    public int getHoleVersion()
    {
        return holeVersion;
    }

    @Override
    public void setHoleVersion(int version)
    {
        this.holeVersion = version;
    }
}
