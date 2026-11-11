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

    @Test void omittedNilAndEmptyOptionsKeepDefaultsAndExplicitSectionStyle() throws Exception {
        CobaltRuntime runtime = runtime();
        runtime.execute("""
                lh.section(); lh.draw('default')
                lh.section(nil); lh.draw('default', nil)
                lh.section({}); lh.draw('default', {})
                lh.section({shadow=false, textColor=0, backgroundColor=0, opacity=0.5})
                lh.draw('styled'); lh.draw('styled', nil); lh.draw('styled', {})
                """, "options");

        List<RenderSection> sections = RenderBuffer.sections();
        assertEquals(4, sections.size());
        SectionState defaults = SectionState.defaults();
        RenderElement.Text defaultText = new RenderElement.Text(
                "default", defaults.textColor(), defaults.backgroundColor(), defaults.shadow(), 0, 0);
        for (RenderSection section : sections.subList(0, 3)) {
            assertEquals(defaults.layout(), section.layout());
            assertEquals(List.of(defaultText), section.rows().getFirst().elements());
        }
        assertEquals(List.of(
                new RenderElement.Text("styled", 0, 0, false, 0, 0, 0.5f),
                new RenderElement.Text("styled", 0, 0, false, 0, 0, 0.5f),
                new RenderElement.Text("styled", 0, 0, false, 0, 0, 0.5f)),
                sections.getLast().rows().getFirst().elements());
    }

    @Test void textScaleUsesSectionDefaultAndAllowsPerDrawOverrides() throws Exception {
        CobaltRuntime runtime = runtime();
        runtime.execute("""
                lh.section({scale=2, textScale=1.5, opacity=0.5})
                lh.draw('inherited')
                lh.drawln('override', {scale=0.5})
                lh.draw('inherited again', {})
                lh.section()
                lh.draw('reset')
                """, "text-scale");

        var section = RenderBuffer.sections().getFirst();
        var inherited = (RenderElement.Text) section.rows().getFirst().elements().getFirst();
        var override = (RenderElement.Text) section.rows().getFirst().elements().get(1);
        var nextRow = (RenderElement.Text) section.rows().get(1).elements().getFirst();
        var reset = (RenderElement.Text) RenderBuffer.sections().getLast().rows().getFirst().elements().getFirst();
        assertEquals(1.5f, inherited.scale());
        assertEquals(0.5f, override.scale());
        assertEquals(1.5f, nextRow.scale());
        assertEquals(1f, reset.scale());
        assertEquals(0.5f, inherited.opacity());
        assertEquals(3f, RenderElementMeasurer.effectiveTextScale(inherited.scale(), section.layout().scale()));
        assertEquals(1f, RenderElementMeasurer.effectiveTextScale(override.scale(), section.layout().scale()));
    }

    @Test void textScaleRejectsNonPositiveAndNonFiniteValues() throws Exception {
        CobaltRuntime runtime = runtime();
        for (String value : List.of("0", "-1", "0/0", "math.huge")) {
            assertThrows(org.squiddev.cobalt.LuaError.class,
                    () -> runtime.execute("lh.section({textScale=" + value + "})", "invalid-text-scale"));
            assertThrows(org.squiddev.cobalt.LuaError.class,
                    () -> runtime.execute("lh.draw('text', {scale=" + value + "})", "invalid-draw-scale"));
        }
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
