package dev.ifair.luahud.api.core;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;

import java.nio.file.Path;

/** Build-time entry point; never packaged in the mod. */
public final class ExportDocumentation {
    private ExportDocumentation() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected the output JSON path");
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        ApiRegistry registry = new ApiRegistry(message -> { throw new IllegalStateException(message); });
        CoreApi.register(registry, null);
        registry.freeze();
        StubGenerator.writeIfChanged(Path.of(args[0]), DocumentationGenerator.generate(registry));
    }
}
