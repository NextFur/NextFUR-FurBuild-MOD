package net.nextfur.fwb.init;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.nextfur.fwb.commands.FurBuildHelpCommand;
import net.nextfur.fwb.commands.FurMapCommand;
import net.nextfur.fwb.commands.FurStyleCommand;
import net.nextfur.fwb.commands.FurWandCommand;
import net.nextfur.fwb.commands.GenerateCommand;
import net.nextfur.fwb.commands.UndoCommand;

public class FwModCommands {
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        FurBuildHelpCommand.register(dispatcher);
        FurStyleCommand.register(dispatcher);
        FurMapCommand.register(dispatcher);
        FurWandCommand.register(dispatcher);
        GenerateCommand.register(dispatcher);
        UndoCommand.register(dispatcher);
    }
}
