package me.ifair.luahud.core;

import me.ifair.luahud.api.core.ApiRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.squiddev.cobalt.LuaError;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class LuaModulesTest {
    @TempDir Path directory;

    @Test void modulesLoadFromProfilesAndAreCachedUntilReload() throws Exception {
        Files.writeString(directory.resolve("utils.lua"),
                "loads=(loads or 0)+1; local name, file=...; return { label='Merhaba', name=name, file=file }");
        CobaltRuntime runtime = runtime();
        runtime.execute("first=require('utils')", "first-profile");
        runtime.execute("second=require('utils'); same=(first==second); "
                + "cached=(package.loaded.utils==first); label=second.label; name=second.name; file=second.file", "second-profile");
        assertTrue(runtime.global("same").checkBoolean());
        assertTrue(runtime.global("cached").checkBoolean());
        assertEquals("Merhaba", runtime.global("label").checkString());
        assertEquals("utils", runtime.global("name").checkString());
        assertEquals("utils.lua", runtime.global("file").checkString());
        assertEquals(1, runtime.global("loads").checkInteger());

        Files.writeString(directory.resolve("utils.lua"), "return {label='Updated'}");
        runtime.start(directory);
        runtime.execute("label=require('utils').label", "reloaded");
        assertEquals("Updated", runtime.global("label").checkString());
    }

    @Test void dottedNamesInitFilesAndCustomPathsWork() throws Exception {
        Files.createDirectories(directory.resolve("helpers/colors"));
        Files.writeString(directory.resolve("helpers/colors/init.lua"), "return 'fallback'");
        Files.writeString(directory.resolve("helpers/colors.lua"), "return 'preferred'");
        Files.writeString(directory.resolve("helpers/custom.lua"), "return require('helpers.colors')");
        CobaltRuntime runtime = runtime();
        runtime.execute("preferred=require('helpers.colors')", "dotted");
        assertEquals("preferred", runtime.global("preferred").checkString());
        Files.delete(directory.resolve("helpers/colors.lua"));
        runtime.execute("package.loaded['helpers.colors']=nil; fallback=require('helpers.colors'); "
                + "package.path='helpers/?.lua'; custom=require('custom')", "fallback");
        assertEquals("fallback", runtime.global("fallback").checkString());
        assertEquals("fallback", runtime.global("custom").checkString());
    }

    @Test void preloadLoadedAndNilOrFalseReturnsFollowRequireSemantics() throws Exception {
        Files.writeString(directory.resolve("empty.lua"), "emptyLoads=(emptyLoads or 0)+1");
        Files.writeString(directory.resolve("falseValue.lua"), "falseLoads=(falseLoads or 0)+1; return false");
        Files.writeString(directory.resolve("assigned.lua"), "package.loaded[...]='assigned'");
        CobaltRuntime runtime = runtime();
        runtime.execute("package.preload.utils=function(name) return name end; "
                + "preloaded=require('utils'); empty=require('empty'); require('empty'); "
                + "falseValue=require('falseValue'); require('falseValue'); "
                + "assigned=require('assigned'); standard=(require('math')==math)", "semantics");
        assertEquals("utils", runtime.global("preloaded").checkString());
        assertTrue(runtime.global("empty").checkBoolean());
        assertEquals(1, runtime.global("emptyLoads").checkInteger());
        assertFalse(runtime.global("falseValue").checkBoolean());
        assertEquals(2, runtime.global("falseLoads").checkInteger());
        assertEquals("assigned", runtime.global("assigned").checkString());
        assertTrue(runtime.global("standard").checkBoolean());
    }

    @Test void missingAndBrokenModulesReportUsefulErrorsAndFailedLoadsCanBeRetried() throws Exception {
        Files.writeString(directory.resolve("broken.lua"), "return {");
        Files.writeString(directory.resolve("retry.lua"), "attempts=(attempts or 0)+1; error('failed')");
        CobaltRuntime runtime = runtime();
        LuaError missing = assertThrows(LuaError.class, () -> runtime.execute("require('missing')", "missing"));
        assertTrue(missing.getMessage().contains("module 'missing' not found"));
        assertTrue(missing.getMessage().contains("missing.lua"));
        LuaError broken = assertThrows(LuaError.class, () -> runtime.execute("require('broken')", "broken"));
        assertTrue(broken.getMessage().contains("broken.lua"));
        assertThrows(LuaError.class, () -> runtime.execute("require('retry')", "first-attempt"));
        assertThrows(LuaError.class, () -> runtime.execute("require('retry')", "second-attempt"));
        assertEquals(2, runtime.global("attempts").checkInteger());
        Files.writeString(directory.resolve("retry.lua"), "return 'fixed'");
        runtime.execute("retried=require('retry')", "fixed");
        assertEquals("fixed", runtime.global("retried").checkString());
        runtime.execute("recovered=true", "healthy");
        assertTrue(runtime.global("recovered").checkBoolean());
    }

    @Test void modulePathsCannotEscapeTheProfileDirectoryEvenThroughSymlinks() throws Exception {
        Path root = Files.createDirectory(directory.resolve("profiles"));
        Path outside = directory.resolve("outside.lua");
        Files.writeString(outside, "escaped=true; return true");
        Files.createSymbolicLink(root.resolve("link.lua"), outside);
        CobaltRuntime runtime = runtime();
        runtime.start(root);
        LuaError symlink = assertThrows(LuaError.class, () -> runtime.execute("require('link')", "symlink"));
        assertTrue(symlink.getMessage().contains("no file 'link.lua'"));
        LuaError traversal = assertThrows(LuaError.class,
                () -> runtime.execute("package.path='../?.lua'; require('outside')", "traversal"));
        assertTrue(traversal.getMessage().contains("no file '../outside.lua'"));
        assertTrue(runtime.global("escaped").isNil());
    }

    @Test void modulesCanBeRequiredFromCallbacksAndUseTheSameWatchdog() throws Exception {
        Files.writeString(directory.resolve("utils.lua"), "return 42");
        Files.writeString(directory.resolve("loop.lua"), "while true do end");
        CobaltRuntime runtime = runtime();
        runtime.execute("lh.on('tick', function() result=require('utils') end)", "callbacks");
        runtime.fireEvent("tick");
        assertEquals(42, runtime.global("result").checkInteger());
        LuaError error = assertThrows(LuaError.class, () -> runtime.execute("require('loop')", "timeout"));
        assertTrue(error.getMessage().contains("exceeded 25 ms"));
    }

    @Test void searchersPassLoaderDataAndRequireReturnsExactlyOneValue() throws Exception {
        CobaltRuntime runtime = runtime();
        runtime.execute("assert(package.loaders==nil); assert(#package.searchers==4); "
                + "local token={}; package.searchers={function(name) "
                + "assert(name=='123'); return function(module, data) "
                + "assert(module=='123' and data==token); return 'result', 'ignored' end, token end}; "
                + "assert(select('#', require(123))==1); assert(require('123')=='result'); "
                + "package.loaded.empty=false; package.searchers={function() return function(...) "
                + "assert(select('#', ...)==2); assert(select(2, ...)==nil); "
                + "package.loaded.empty=false end end}; assert(require('empty')==false)", "searcher-contract");
    }

    @Test void requireUsesRegistryTablesEvenWhenPublicReferencesAreReplaced() throws Exception {
        CobaltRuntime runtime = runtime();
        runtime.execute("local loaded, preload=package.loaded, package.preload; "
                + "preload.original=function() return 'original' end; loaded.cached='cached'; "
                + "package.loaded={cached='replacement'}; "
                + "package.preload={original=function() return 'replacement' end}; "
                + "assert(require('original')=='original'); assert(require('cached')=='cached'); "
                + "package={}; assert(require('original')=='original'); "
                + "assert(loaded.original=='original')", "registry-tables");
    }

    @Test void searchpathUsesLiteralReplacementsAndDoesNotCompileFiles() throws Exception {
        Files.createDirectories(directory.resolve("helpers"));
        Files.writeString(directory.resolve("helpers/colors.lua"), "invalid Lua {");
        Files.writeString(directory.resolve("a%b.lua"), "return 1");
        Files.writeString(directory.resolve("42.lua"), "return 42");
        CobaltRuntime runtime = runtime();
        runtime.execute("assert(package.searchpath('helpers.colors',';;?.lua;;')=='helpers/colors.lua'); "
                + "assert(package.searchpath('helpers::colors','?.lua','::','/')=='helpers/colors.lua'); "
                + "assert(package.searchpath('a%b','?.lua','','')=='a%b.lua'); "
                + "assert(package.searchpath('a::b','?.lua','::','%')=='a%b.lua'); "
                + "assert(package.searchpath(42,'?.lua')=='42.lua'); "
                + "assert(select('#',package.searchpath(42,'?.lua'))==1); "
                + "local file, message=package.searchpath('missing',';?.lua;;?/init.lua;'); "
                + "assert(file==nil and message==\"\\n\\tno file 'missing.lua'\\n\\tno file 'missing/init.lua'\"); "
                + "local empty, reason=package.searchpath('missing',''); assert(empty==nil and reason=='')", "searchpath");
    }

    @Test void searchersUseRawSequentialLookupAndValidatePackageFields() throws Exception {
        CobaltRuntime runtime = runtime();
        runtime.execute("local preload=package.preload; preload['1']=false; "
                + "assert(package.searchers[1](1)==false); "
                + "assert(select('#',package.searchers[1]('missing'))==1); "
                + "package.searchers=setmetatable({[2]=function() error('must not run') end}, "
                + "{__index=function() error('must not run') end}); "
                + "local ok, message=pcall(require,'gap'); assert(not ok and message:find(\"module 'gap' not found:\",1,true)); "
                + "package.searchers={function() return 123 end}; "
                + "ok,message=pcall(require,'number'); assert(not ok and message:find('not found:123',1,true)); "
                + "package.searchers=false; ok,message=pcall(require,'invalid'); "
                + "assert(not ok and message:find(\"'package.searchers' must be a table\",1,true))", "searcher-validation");
        runtime.start(directory);
        runtime.execute("package.path=false; local ok,message=pcall(require,'invalid'); "
                + "assert(not ok and message:find(\"'package.path' must be a string\",1,true)); "
                + "ok,message=pcall(package.searchpath,'name','?.lua',false); "
                + "assert(not ok and message:find('bad argument #3',1,true)); "
                + "ok,message=pcall(require,{}); assert(not ok and message:find('bad argument #1',1,true))", "argument-validation");
    }

    @Test void recursiveLoadsHaveNoImplicitSentinelAndMayPublishAPartialModule() throws Exception {
        Files.writeString(directory.resolve("recursive.lua"),
                "attempts=(attempts or 0)+1; if attempts<3 then return require('recursive') end; return 'done'");
        Files.writeString(directory.resolve("partial.lua"),
                "local name=...; local module={}; package.loaded[name]=module; module.self=require(name); return module");
        CobaltRuntime runtime = runtime();
        runtime.execute("assert(require('recursive')=='done'); assert(attempts==3); "
                + "local partial=require('partial'); assert(partial.self==partial)", "recursive-loads");
    }

    @Test void nativeLibraryApiMatchesLuaWithoutDynamicLinking() throws Exception {
        Files.writeString(directory.resolve("native.so"), "not a native library");
        CobaltRuntime runtime = runtime();
        runtime.execute("assert(package.cpath==''); "
                + "local library,message,where=package.loadlib('native.so','luaopen_native'); "
                + "assert(library==nil and where=='absent' and message:find('dynamic libraries not enabled',1,true)); "
                + "assert(select('#',package.loadlib('native.so','*'))==3); "
                + "package.cpath='?.so'; local ok,reason=pcall(require,'native'); "
                + "assert(not ok and reason:find(\"error loading module 'native' from file 'native.so'\",1,true)); "
                + "ok,reason=pcall(package.searchers[4],'native.child'); "
                + "assert(not ok and reason:find(\"from file 'native.so'\",1,true))", "native-api");
        assertEquals(File.separator + "\n;\n?\n!\n-\n", runtime.global("package").checkTable().rawget("config").checkString());
    }

    @Test void luaFilesAcceptUtf8BomAndShebangWithoutChangingLineNumbers() throws Exception {
        Files.writeString(directory.resolve("bom.lua"), "\uFEFF#!/usr/bin/env lua\nreturn 42");
        Files.writeString(directory.resolve("brokenBom.lua"), "\uFEFF#!/usr/bin/env lua\nreturn {");
        CobaltRuntime runtime = runtime();
        runtime.execute("assert(require('bom')==42)", "bom");
        LuaError error = assertThrows(LuaError.class, () -> runtime.execute("require('brokenBom')", "broken-bom"));
        assertTrue(error.getMessage().contains("brokenBom.lua:2:"));
    }

    private CobaltRuntime runtime() throws Exception {
        ApiRegistry registry = new ApiRegistry(ignored -> {});
        registry.addEventFunctions();
        registry.freeze();
        CobaltRuntime runtime = new CobaltRuntime(registry);
        runtime.start(directory);
        return runtime;
    }
}
