package me.earth.earthhack.impl.core.ducks;

import com.mojang.datafixers.DataFixer;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Duck interface for {@link net.minecraft.client.MinecraftClient}.
 */
public interface IMinecraftClient
{
    /**
     * Accessor for mc.rightClickDelayTimer.
     *
     * @return mc.rightClickDelayTimer.
     */
    int getRightClickDelay();

    /**
     * Accessor for mc.rightClickDelayTimer.
     *
     * @param delay the value to set the timer to.
     */
    void setRightClickDelay(int delay);

    /**
     * Accesses Minecraft's timer.
     *
     * @return minecraft's timer.
     */
    RenderTickCounter getTimer();

    /** @return the current gameloop, will be incremented every gameloop. */
    int getGameLoop();

    int getFpsCounter();

    /** @return <tt>true</tt> if 3arthh4ck is running. */
    boolean isRunning();


    /** @return Minecraft's DataFixer (as MetadataSerializer's replacement). */

    DataFixer getDataFixer();

}
