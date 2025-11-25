package cn.anecansaitin.free_camera_api_tripod.commands.argument;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.api.control_scheme.ControlScheme;
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
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class SchemeArgument implements ArgumentType<ControlScheme> {
    private static final ArrayList<String> EXAMPLES;
    private static final String[] SUGGESTIONS;
    private static final HashMap<String, ControlScheme> CONSTANTS;
    private static final String SUGGESTIONS_STRING;
    private static final Dynamic2CommandExceptionType ERROR = new Dynamic2CommandExceptionType((found, constants) -> CommandUtils.makeTranslatableWithFallback("commands." + FreeCameraApiTripod.MODID + ".arguments.control_scheme.invalid", constants, found));

    static {
        EXAMPLES = new ArrayList<>(4);
        EXAMPLES.add("control scheme");

        CONSTANTS = new HashMap<>(4);
        CONSTANTS.put("vanilla", ControlScheme.VANILLA);
        CONSTANTS.put("camera_relative", ControlScheme.CAMERA_RELATIVE);
        CONSTANTS.put("camera_relative_strafe", ControlScheme.CAMERA_RELATIVE_STRAFE);
        CONSTANTS.put("player_relative_strafe", ControlScheme.PLAYER_RELATIVE_STRAFE);

        SUGGESTIONS = new String[]{"vanilla", "camera_relative", "camera_relative_strafe", "player_relative_strafe"};
        SUGGESTIONS_STRING = String.join(" | ", SUGGESTIONS);
    }

    @Override
    public ControlScheme parse(StringReader reader) throws CommandSyntaxException {
        String name = reader.readString();
        ControlScheme scheme = CONSTANTS.get(name);

        if (scheme == null) {
            throw ERROR.createWithContext(reader, name, SUGGESTIONS_STRING);
        }

        return scheme;
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
