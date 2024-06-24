package me.earth.earthhack.impl.modules.combat.autocrystal;

import me.earth.earthhack.impl.event.events.network.PacketEvent;
import me.earth.earthhack.impl.event.listeners.ModuleListener;
import me.earth.earthhack.impl.managers.Managers;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;

/**
 * Listener for PlayerInteractItemC2SPacket, which gets sent
 * after a Placement packet has been sent to the server.
 *
 * Manages AutoCrystal - BreakDelay & BreakTimer.
 */
final class ListenerPostPlace extends ModuleListener<AutoCrystal,
        PacketEvent.Post<PlayerInteractItemC2SPacket>>
{
    public ListenerPostPlace(AutoCrystal module)
    {
        super(module,
                PacketEvent.Post.class,
                PlayerInteractItemC2SPacket.class);
    }

    @Override
    public void invoke(PacketEvent.Post<PlayerInteractItemC2SPacket> event)
    {
        if (module.idPredict.getValue()
            && !module.noGod
            && module.breakTimer.passed(module.breakDelay.getValue())
            && !(module.stopWhenEating.getValue() && module.isEating())
            && !(module.stopWhenEatingOffhand.getValue() && module.isEatingOffhand())
            && !(module.stopWhenMining.getValue() && module.isMining())
            && mc.player
                .getStackInHand(event.getPacket().getHand())
                .getItem() == Items.END_CRYSTAL
            && module.idHelper.isSafe(Managers.ENTITIES.getPlayersAsync(),
                                      module.holdingCheck.getValue(),
                                      module.toolCheck.getValue()))
        {
            module.idHelper.attack(module.breakSwing.getValue(),
                                   module.godSwing.getValue(),
                                   module.idOffset.getValue(),
                                   module.idPackets.getValue(),
                                   module.idDelay.getValue());

            module.breakTimer.reset(module.breakDelay.getValue());
        }
    }

}
