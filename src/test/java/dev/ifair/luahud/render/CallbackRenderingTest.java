package dev.ifair.luahud.render;

import dev.ifair.luahud.api.core.ApiRegistry;
import dev.ifair.luahud.api.models.CoreModels;
import dev.ifair.luahud.api.modules.RenderModule;
import dev.ifair.luahud.core.CobaltRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CallbackRenderingTest {
    @AfterEach void clear() { RenderBuffer.clear(); RenderModule.resetSection(); }

    private static CobaltRuntime runtime() throws Exception {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        api.event("reload", "");
        api.event("tick", "");
        api.event("render", "");
        api.event("damage", "");
        registry.addEventFunctions();
        RenderModule.register(api, CoreModels.register(api));
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start();
        return runtime;
    }

    @Test void reloadTickRenderAndTimersRetainIndependentDrawings() throws Exception {
        CobaltRuntime runtime = runtime();
        runtime.execute("lh.draw('load'); "
                + "lh.on('reload', function() lh.draw('reload') end); "
                + "lh.on('tick', function() lh.draw('tick') end); "
                + "lh.on('render', function() lh.draw('render') end); "
                + "lh.setInterval(2, 'tick', function() lh.draw('timer') end)", "profile");
        runtime.fireEvent("reload");
        runtime.fireEvent("tick");
        runtime.fireEvent("render");
        runtime.fireEvent("tick");
        runtime.fireEvent("render");

        assertEquals(List.of("load", "reload", "timer", "tick", "render"), texts());
    }

    @Test void errorsKeepPartialDrawingsAndNextInvocationRemovesOnlyUntimedOutput() throws Exception {
        CobaltRuntime runtime = runtime();
        runtime.execute("calls=0; lh.on('damage', function() calls=calls+1; "
                + "lh.draw('partial'); lh.section({duration=5}); lh.draw('timed'); "
                + "error('after drawing') end)", "broken");
        runtime.fireEvent("damage");
        assertEquals(List.of("partial", "timed"), texts());
        runtime.fireEvent("damage");
        assertEquals(List.of("timed", "partial", "timed"), texts());
    }

    @Test void idReplacementWorksAcrossProfilesAndWithinOneCallback() throws Exception {
        CobaltRuntime runtime = runtime();
        runtime.execute("lh.on('damage', function() lh.section({id='indicator', duration=5}); lh.draw('first') end)", "first");
        runtime.execute("lh.on('damage', function() lh.section({id='indicator', duration=5}); lh.draw('second'); "
                + "lh.section({id='indicator'}); lh.draw('last') end)", "second");
        runtime.fireEvent("damage");
        assertEquals(List.of("last"), texts());
    }

    @Test void anEmptySecondInvocationRemovesPreviousDefaultSectionWithoutTouchingOtherCallbacks() throws Exception {
        CobaltRuntime runtime = runtime();
        runtime.execute("show=true; lh.on('tick', function() if show then lh.draw('conditional') end end); "
                + "lh.on('tick', function() lh.draw('steady') end)", "profile");
        runtime.fireEvent("tick");
        runtime.execute("show=false", "other");
        runtime.fireEvent("tick");
        assertEquals(List.of("steady"), texts());
    }

    @Test void aRuntimeRestartDropsBothTimedAndUntimedSections() throws Exception {
        CobaltRuntime runtime = runtime();
        runtime.execute("lh.draw('default'); lh.section({duration=5}); lh.draw('timed')", "profile");
        runtime.start();
        assertTrue(RenderBuffer.sections().isEmpty());
    }

    @Test void invalidLifetimeOptionsSurfaceAsLuaErrors() throws Exception {
        CobaltRuntime runtime = runtime();
        for (String options : List.of("{duration=0}", "{duration=-1}", "{duration=0/0}", "{duration=math.huge}", "{id=''}")) {
            assertThrows(org.squiddev.cobalt.LuaError.class,
                    () -> runtime.execute("lh.section(" + options + ")", "invalid"));
        }
    }

    @Test void timedOutCallbacksKeepTheirPartialOutputAndStopRunning() throws Exception {
        CobaltRuntime runtime = runtime();
        runtime.execute("lh.on('damage', function() lh.draw('partial'); "
                + "lh.section({duration=5}); lh.draw('timed'); while true do end end)", "timeout");
        runtime.fireEvent("damage");
        assertEquals(List.of("partial", "timed"), texts());
        runtime.fireEvent("damage");
        assertEquals(List.of("partial", "timed"), texts());
    }

    private static List<String> texts() {
        return RenderBuffer.sections().stream().flatMap(section -> section.rows().stream())
                .flatMap(row -> row.elements().stream()).map(element -> ((RenderElement.Text) element).value()).toList();
    }
}
