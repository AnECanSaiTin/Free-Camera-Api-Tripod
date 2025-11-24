package cn.anecansaitin.free_camera_api_tripod.commands.argument;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.freecameraapi.api.ModifierStates;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.neoforged.neoforge.server.command.CommandUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class StateArgument implements ArgumentType<Integer> {
    private static final ArrayList<String> EXAMPLES;
    private static final String[] SUGGESTIONS;
    private static final String SUGGESTIONS_STRING;
    private static final Dynamic2CommandExceptionType ERROR = new Dynamic2CommandExceptionType((found, constants) -> CommandUtils.makeTranslatableWithFallback("commands." + FreeCameraApiTripod.MODID + ".arguments.camera_state.invalid", constants, found));

    static {
        EXAMPLES = new ArrayList<>(1);
        EXAMPLES.add("camera state");

        SUGGESTIONS = ModifierStates.NAME_STATE.keySet().toArray(new String[0]);
        SUGGESTIONS_STRING = String.join(" | ", SUGGESTIONS);
    }

    @Override
    public Integer parse(StringReader reader) throws CommandSyntaxException {
        String name = reader.readString();
        int state = ModifierStates.getState(name);

        if (state == ModifierStates.NAME_STATE.defaultReturnValue()) {
            throw ERROR.createWithContext(reader, name, SUGGESTIONS_STRING);
        }

        return state;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(SUGGESTIONS, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}