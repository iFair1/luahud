package dev.ifair.luahud.core;

import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.function.LibFunction;

import java.io.File;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Host filesystem bindings for the Lua 5.2 package library. */
final class LuaModules {
    private static final String RESOURCE = "/assets/luahud/runtime/package.lua";

    private LuaModules() {}

    static void install(LuaState state, Path scriptsDirectory) throws LuaError {
        Path root = scriptsDirectory == null ? null : scriptsDirectory.toAbsolutePath().normalize();
        LuaTable host = new LuaTable();
        host.rawset("separator", ValueFactory.valueOf(File.separator));
        host.rawset("registry", state.registry().get());
        host.rawset("readable", LibFunction.create((luaState, requested) -> {
            try {
                Path file = resolve(root, requested.checkString());
                if (file == null) return Constants.FALSE;
                try (InputStream ignored = Files.newInputStream(file)) {
                    return Constants.TRUE;
                }
            } catch (IOException | InvalidPathException error) {
                return Constants.FALSE;
            }
        }));
        host.rawset("loadfile", LibFunction.createV((luaState, args) -> {
            String requested = args.arg(1).checkString();
            try {
                Path file = resolve(root, requested);
                if (file == null) return failure("cannot open " + requested);
                try (BufferedInputStream source = new BufferedInputStream(Files.newInputStream(file))) {
                    // luaL_loadfile accepts a UTF-8 BOM before the chunk (and optional shebang).
                    source.mark(3);
                    if (source.read() != 0xEF || source.read() != 0xBB || source.read() != 0xBF) {
                        source.reset();
                    }
                    return LoadState.load(luaState, source, "@" + requested, luaState.globals());
                }
            } catch (IOException | InvalidPathException | CompileException error) {
                return failure(error.getMessage());
            }
        }));
        try (InputStream source = LuaModules.class.getResourceAsStream(RESOURCE)) {
            if (source == null) throw new IllegalStateException("Missing Lua package resource: " + RESOURCE);
            LuaThread.runMain(state, LoadState.load(state, source, "=LuaHUD package", state.globals()), host);
        } catch (CompileException | IOException error) {
            throw new IllegalStateException("Cannot initialize Lua package library", error);
        }
    }

    private static Path resolve(Path root, String requested) throws IOException {
        if (root == null) return null;
        Path file = root.resolve(requested).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) return null;
        return file.toRealPath().startsWith(root.toRealPath()) ? file : null;
    }

    private static Varargs failure(String message) {
        return ValueFactory.varargsOf(Constants.NIL, ValueFactory.valueOf(message));
    }
}
