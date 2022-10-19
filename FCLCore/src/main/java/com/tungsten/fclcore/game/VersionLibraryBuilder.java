package com.tungsten.fclcore.game;

import com.tungsten.fclcore.util.StringUtils;
import com.tungsten.fclcore.util.platform.CommandBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class VersionLibraryBuilder {
    private final Version version;
    private final List<String> mcArgs;
    private final List<Argument> game;
    private final List<Argument> jvm;
    private final List<Library> libraries;
    private final boolean useMcArgs;
    private boolean jvmChanged = false;

    public VersionLibraryBuilder(Version version) {
        this.version = version;
        this.libraries = new ArrayList<>(version.getLibraries());
        this.mcArgs = version.getMinecraftArguments() != null ? StringUtils.tokenize(version.getMinecraftArguments()) : null;
        this.game = new ArrayList<>(version.getArguments().getGame() != null ? version.getArguments().getGame() : Arguments.DEFAULT_GAME_ARGUMENTS);
        this.jvm = new ArrayList<>(version.getArguments().getJvm() != null ? version.getArguments().getJvm() : Arguments.DEFAULT_JVM_ARGUMENTS);
        this.useMcArgs = mcArgs != null;
    }

    public Version build() {
        Version ret = version;
        if (useMcArgs) {
            // The official launcher will not parse the "arguments" property when it detects the presence of "mcArgs".
            // The "arguments" property with the "rule" is simply ignored here.
            for (Argument arg : this.game) {
                List<String> argStr = arg.toString(new HashMap<>(), new HashMap<>());
                this.mcArgs.addAll(argStr);
            }
            ret = ret.setArguments(null);

            // Since $ will be escaped in linux, and our maintain of minecraftArgument will not cause escaping,
            // so we regenerate the minecraftArgument without escaping.
            ret = ret.setMinecraftArguments(new CommandBuilder().addAllWithoutParsing(mcArgs).toString());
        } else {
            if (ret.getArguments() != null) {
                ret = ret.setArguments(ret.getArguments().withGame(game));
                if (jvmChanged) {
                    ret = ret.setArguments(ret.getArguments().withJvm(jvm));
                }
            }
            else {
                ret = ret.setArguments(new Arguments(game, jvmChanged ? jvm : null));
            }
        }
        return ret.setLibraries(libraries);
    }

    public boolean hasTweakClass(String tweakClass) {
        boolean match = false;
        for (Argument argument : game) {
            if (argument.toString().equals(tweakClass)) {
                match = true;
                break;
            }
        }
        return useMcArgs && mcArgs.contains(tweakClass) || match;
    }

    public void removeTweakClass(String target) {
        replaceTweakClass(target, null, false);
    }

    /**
     * Replace existing tweak class without reordering.
     * If the tweak class does not exist, the new tweak class will be appended to the end of argument list.
     * If the tweak class appears more than one time, the tweak classes will be removed excluding the first one.
     *
     * @param target the tweak class to replace
     * @param replacement the new tweak class to be replaced with
     */
    public void replaceTweakClass(String target, String replacement) {
        replaceTweakClass(target, replacement, true);
    }

    /**
     * Replace existing tweak class and add the new tweak class to the end of argument list.
     *
     * @param target the tweak class to replace
     * @param replacement the new tweak class to be replaced with
     */
    public void addTweakClass(String target, String replacement) {
        replaceTweakClass(target, replacement, false);
    }

    /**
     * Replace existing tweak class.
     * If the tweak class does not exist, the new tweak class will be appended to the end of argument list.
     * If the tweak class appears more than one time, the tweak classes will be removed excluding the first one.
     *
     * @param target the tweak class to replace
     * @param replacement the new tweak class to be replaced with, if null, remove the tweak class only
     * @param inPlace if true, replace the tweak class in place, otherwise add the tweak class to the end of the argument list without replacement.
     */
    public void replaceTweakClass(String target, String replacement, boolean inPlace) {
        replaceTweakClass(target, replacement, inPlace, false);
    }

    /**
     * Replace existing tweak class.
     * If the tweak class does not exist, the new tweak class will be added to argument list.
     * If the tweak class appears more than one time, the tweak classes will be removed excluding the first one.
     *
     * @param target the tweak class to replace
     * @param replacement the new tweak class to be replaced with, if null, remove the tweak class only
     * @param inPlace if true, replace the tweak class in place, otherwise add the tweak class to the end of the argument list without replacement.
     * @param reserve if true, add the tweak class to the start of the argument list.
     */
    public void replaceTweakClass(String target, String replacement, boolean inPlace, boolean reserve) {
        if (replacement == null && inPlace)
            throw new IllegalArgumentException("Replacement cannot be null in replace mode");

        boolean replaced = false;
        if (useMcArgs) {
            for (int i = 0; i + 1 < mcArgs.size(); ++i) {
                String arg0Str = mcArgs.get(i);
                String arg1Str = mcArgs.get(i + 1);
                if (arg0Str.equals("--tweakClass") && arg1Str.equals(target)) {
                    if (!replaced && inPlace) {
                        // for the first one, we replace the tweak class only.
                        mcArgs.set(i + 1, replacement);
                        replaced = true;
                    } else {
                        // otherwise, we remove the duplicate tweak classes.
                        mcArgs.remove(i);
                        mcArgs.remove(i);
                        --i;
                    }
                }
            }
        }

        for (int i = 0; i + 1 < game.size(); ++i) {
            Argument arg0 = game.get(i);
            Argument arg1 = game.get(i + 1);
            if (arg0 instanceof StringArgument && arg1 instanceof StringArgument) {
                // We need to preserve the tokens
                String arg0Str = arg0.toString();
                String arg1Str = arg1.toString();
                if (arg0Str.equals("--tweakClass") && arg1Str.equals(target)) {
                    if (!replaced && inPlace) {
                        // for the first one, we replace the tweak class only.
                        game.set(i + 1, new StringArgument(replacement));
                        replaced = true;
                    } else {
                        // otherwise, we remove the duplicate tweak classes.
                        game.remove(i);
                        game.remove(i);
                        --i;
                    }
                }
            }
        }

        // if the tweak class does not exist, add a new one to the end.
        if (!replaced && replacement != null) {
            if (reserve) {
                if (useMcArgs) {
                    mcArgs.add(0, replacement);
                    mcArgs.add(0, "--tweakClass");
                } else {
                    game.add(0, new StringArgument(replacement));
                    game.add(0, new StringArgument("--tweakClass"));
                }
            } else {
                game.add(new StringArgument("--tweakClass"));
                game.add(new StringArgument(replacement));
            }
        }
    }

    public List<Argument> getMutableJvmArguments() {
        jvmChanged = true;
        return jvm;
    }

    public void addGameArgument(String... args) {
        for (String arg : args)
            game.add(new StringArgument(arg));
    }

    public void addJvmArgument(String... args) {
        jvmChanged = true;
        for (String arg : args)
            jvm.add(new StringArgument(arg));
    }

    public void addLibrary(Library library) {
        libraries.add(library);
    }
}