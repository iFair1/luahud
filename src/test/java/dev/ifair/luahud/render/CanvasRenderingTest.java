package dev.ifair.luahud.render;

import dev.ifair.luahud.api.core.ApiRegistry;
import dev.ifair.luahud.api.core.StubGenerator;
import dev.ifair.luahud.api.models.CoreModels;
import dev.ifair.luahud.api.modules.RenderModule;
import dev.ifair.luahud.core.CobaltRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.LuaError;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CanvasRenderingTest {
    @AfterEach void clear() { RenderBuffer.clear(); RenderModule.resetSection(); }

    private static CobaltRuntime runtime() throws Exception {
        var registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        api.event("tick", "");
        api.event("render", "");
        registry.addEventFunctions();
        RenderModule.register(api, CoreModels.register(api));
        registry.freeze();
        var runtime = new CobaltRuntime(registry);
        runtime.start();
        return runtime;
    }

    private static RenderElement.Canvas rootCanvas(int index) { return RenderBuffer.sections().get(index).canvas(); }

    @Test void recordsCommandsAndSnapshotsOptions() throws Exception {
        var runtime = runtime();
        runtime.execute("lh.canvas({width=100,height=40}); local o={x=2.5,y=3,scale=2,rotation=30}; "
                + "lh.draw('A',o); o.x=99; lh.drawRect(0,0,20,6,{color='#80FF0000'}); "
                + "lh.draw('B',{x=8}); lh.endCanvas()", "canvas");
        var canvas = rootCanvas(0).drawing();
        assertEquals(3, canvas.commands().size());
        var first = (CanvasDrawing.Content) canvas.commands().getFirst();
        assertEquals(2.5f, first.transform().x());
        assertEquals(2, first.transform().scale());
        assertEquals(30, first.transform().rotation());
        assertEquals(0, first.element().backgroundColor());
        assertInstanceOf(CanvasDrawing.Mesh.class, canvas.commands().get(1));
        assertEquals(8, ((CanvasDrawing.Content) canvas.commands().get(2)).transform().x());
    }

    @Test void inlineCanvasResumesSameRowAndSectionStyle() throws Exception {
        var runtime = runtime();
        runtime.execute("lh.section({textColor='#FF00FF00'}); lh.draw('before'); "
                + "lh.canvas({inline=true,width=80,height=8,scale=2}); lh.draw('inside'); "
                + "lh.endCanvas(); lh.draw('after')", "inline");
        var sections = RenderBuffer.sections();
        assertEquals(1, sections.size());
        var elements = sections.getFirst().rows().getFirst().elements();
        assertEquals(3, elements.size());
        var canvas = (RenderElement.Canvas) elements.get(1);
        assertEquals(160, RenderElementMeasurer.width(canvas, null, 1));
        assertEquals(16, RenderElementMeasurer.height(canvas, null, 1));
        assertEquals(0xFF00FF00, ((RenderElement.Text) elements.get(2)).textColor());
        assertEquals(0xFFFFFFFF, ((RenderElement.Text) ((CanvasDrawing.Content)
                canvas.drawing().commands().getFirst()).element()).textColor());
    }

    @Test void rootCanvasUsesExactBoundsWithoutRowPadding() throws Exception {
        var runtime = runtime();
        runtime.execute("lh.canvas({anchor='bottomRight',offsetX=7,offsetY=9,width=100,height=40,scale=2}); "
                + "lh.drawRect(-50,-50,500,500)", "anchor");
        var layout = new RenderLayoutEngine().layout(RenderBuffer.sections(),
                (e, s) -> RenderElementMeasurer.width(e, null, s),
                (e, s) -> RenderElementMeasurer.height(e, null, s), 400, 240);
        var positioned = layout.elements().getFirst();
        assertEquals(193, positioned.contentX());
        assertEquals(151, positioned.contentY());
        assertEquals(200, positioned.backgroundWidth());
        assertEquals(80, positioned.rowHeight());
        assertTrue(rootCanvas(0).drawing().clip);
    }

    @Test void invocationStateRestoresAnOpenCanvasAcrossNestedEvents() throws Exception {
        // A nested event is fired through the registry's publisher, as addons do.
        var registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        api.event("nested", "");
        api.event("tick", "");
        var publish = api.root().function("publishNested", dev.ifair.luahud.api.LuaType.nil(), "");
        CobaltRuntime[] holder = new CobaltRuntime[1];
        publish.call(args -> { holder[0].fireEvent("nested"); return null; });
        registry.addEventFunctions();
        RenderModule.register(api, CoreModels.register(api));
        registry.freeze();
        holder[0] = new CobaltRuntime(registry);
        holder[0].start();
        holder[0].execute("lh.on('nested',function() lh.canvas({width=5,height=5}); lh.draw('nested') end); "
                + "lh.on('tick',function() lh.canvas({width=100,height=20}); lh.draw('before'); "
                + "lh.publishNested(); lh.draw('after'); lh.endCanvas() end)", "nested");
        holder[0].fireEvent("tick");
        assertEquals(2, RenderBuffer.sections().size());
        assertEquals(2, rootCanvas(0).drawing().commands().size());
        assertEquals(1, rootCanvas(1).drawing().commands().size());
    }

    @Test void newSelectionsCloseCanvasAndEndCanvasRequiresAnOpenCanvas() throws Exception {
        var runtime = runtime();
        runtime.execute("lh.section(); lh.draw('a'); lh.canvas({width=10,height=10}); "
                + "lh.canvas({width=20,height=20}); lh.endCanvas(); lh.draw('b'); "
                + "lh.canvas({width=30,height=30}); lh.section(); lh.draw('c')", "selection");
        assertEquals(5, RenderBuffer.sections().size());
        assertEquals(2, RenderBuffer.sections().getFirst().rows().getFirst().elements().size());
        assertThrows(LuaError.class, () -> runtime.execute("lh.endCanvas()", "invalid"));
    }

    @Test void validatesCanvasOnlyOptionsAndGeometryWithoutCorruptingFollowingInvocations() throws Exception {
        var runtime = runtime();
        for (String script : List.of(
                "lh.draw('a',{x=1})", "lh.draw('a',{scale=2})", "lh.drawRect(0,0,1,1)",
                "lh.canvas({width=0,height=10})", "lh.canvas({width=-1,height=10})",
                "lh.canvas({width=0/0,height=10})", "lh.canvas({width=math.huge,height=10})",
                "lh.canvas({inline=true,width=10,height=10,id='invalid'})",
                "lh.canvas({inline=true,width=10,height=10,anchor='topRight'})",
                "lh.canvas({width=10,height=10}); lh.drawln('a')",
                "lh.canvas({width=10,height=10}); lh.newline()",
                "lh.canvas({width=10,height=10}); lh.drawRect(0,0,-1,1)",
                "lh.canvas({width=10,height=10}); lh.drawCircle(0,0,-1)",
                "lh.canvas({width=10,height=10}); lh.draw('a',{scale=0})")) {
            assertThrows(LuaError.class, () -> runtime.execute(script, "invalid"), script);
        }
        runtime.execute("lh.draw('works')", "valid");
        assertFalse(RenderBuffer.isCanvas());
    }

    @Test void callbackRefreshAndSharedIdsApplyToCanvases() throws Exception {
        var runtime = runtime();
        runtime.execute("lh.on('tick',function() lh.canvas({id='same',width=10,height=10}); lh.draw('tick') end); "
                + "lh.on('render',function() lh.canvas({id='same',width=20,height=20}); lh.draw('render') end)", "callbacks");
        runtime.fireEvent("tick");
        runtime.fireEvent("render");
        assertEquals(1, RenderBuffer.sections().size());
        assertEquals(20, rootCanvas(0).drawing().width);
        runtime.fireEvent("tick");
        assertEquals(1, RenderBuffer.sections().size());
        assertEquals(10, rootCanvas(0).drawing().width);
    }

    @Test void timedCanvasKeepsItsOwnLifetimeAndInlineCanvasExpiresWithItsSection() {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var queue = new RenderQueue(clock::get);
        var layout = SectionLayout.defaults(Anchor.TOP_LEFT, 0, 0);
        Object owner = new Object();
        queue.beginInvocation(owner);
        queue.beginSection(layout, "section", 2.0);
        queue.beginCanvas(layout, 20, 10, 1, true, true, true, null, null);
        queue.closeCanvas();
        queue.beginCanvas(layout, 50, 30, 1, false, true, false, "canvas", 5.0);
        queue.endInvocation();
        queue.beginInvocation(owner);
        queue.endInvocation();
        assertEquals(2, queue.sections().size());
        clock.set(2_000_000_000L);
        assertTrue(queue.expire());
        assertEquals(1, queue.sections().size());
        assertFalse(queue.sections().getFirst().canvas().drawing().clip);
        clock.set(5_000_000_000L);
        assertTrue(queue.expire());
        assertTrue(queue.sections().isEmpty());
    }

    @Test void hiddenCanvasAndPartialFailureDoNotLeakState() throws Exception {
        var runtime = runtime();
        runtime.execute("lh.canvas({id='hidden',width=10,height=10,visible=false}); lh.draw('hidden')", "hidden");
        assertFalse(rootCanvas(0).drawing().visible);
        assertThrows(LuaError.class, () -> runtime.execute(
                "lh.canvas({width=20,height=20}); lh.draw('partial'); error('stop')", "broken"));
        assertEquals(1, rootCanvas(1).drawing().commands().size());
        runtime.execute("lh.draw('normal')", "normal");
        assertEquals("normal", ((RenderElement.Text) RenderBuffer.sections().getLast().rows()
                .getFirst().elements().getFirst()).value());
    }

    @Test void exportsCanvasSchemasAndFunctions() {
        var registry = new ApiRegistry(ignored -> {});
        var api = registry.registrar("luahud");
        RenderModule.register(api, CoreModels.register(api));
        registry.freeze();
        String stub = StubGenerator.generate(registry);
        for (String name : List.of("canvas", "endCanvas", "drawRect", "drawGradient", "drawLine", "drawCircle", "drawArc", "measureText")) {
            assertTrue(stub.contains("function lh." + name + "("), name);
        }
        assertTrue(stub.contains("---@class CanvasOptions"));
        assertTrue(stub.contains("---@class TextureRegion"));
        assertTrue(stub.contains("---@class NineSlice"));
    }
}
