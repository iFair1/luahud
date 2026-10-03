package me.ifair.luahud.api.core;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;
import net.fabricmc.loader.impl.util.SystemProperties;

import java.lang.reflect.InvocationTargetException;

/** Applies Fabric's development class transformations without launching a game. */
public final class DocumentationExportLauncher {
    private DocumentationExportLauncher() {}

    public static void main(String[] args) throws Throwable {
        System.setProperty(SystemProperties.DEVELOPMENT, "true");
        System.setProperty(SystemProperties.UNIT_TEST, "true");
        ClassLoader loader = new Knot(EnvType.CLIENT).init(new String[0]);
        try {
            loader.loadClass("me.ifair.luahud.api.core.ExportDocumentation")
                    .getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }
}
