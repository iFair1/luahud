-- Lua 5.2 package semantics, with filesystem access supplied by LuaHUD.
local host = ...
local type, tostring, error, rawget = type, tostring, error, rawget
local find, sub, gmatch, concat = string.find, string.sub, string.gmatch, table.concat
local registry, separator = host.registry, host.separator
local loaded = registry._LOADED or {}
local preload = registry._PRELOAD or {}
registry._LOADED, registry._PRELOAD = loaded, preload
loaded._G, loaded.string, loaded.table, loaded.math = _G, string, table, math
loaded.coroutine, loaded.utf8 = coroutine, utf8

local pkg = {
    loaded = loaded,
    preload = preload,
    path = '?.lua;?/init.lua',
    cpath = '',
    config = separator .. '\n;\n?\n!\n-\n'
}
package, loaded.package = pkg, pkg

local function checkstring(value, index, name)
    local kind = type(value)
    if kind == 'string' then return value end
    if kind == 'number' then return tostring(value) end
    error("bad argument #" .. index .. " to '" .. name .. "' (string expected, got " .. kind .. ")", 3)
end

-- Lua's path replacements are literal, including multi-character separators.
local function replace(text, needle, replacement)
    if needle == '' then return text end
    local parts, offset = {}, 1
    while true do
        local first, last = find(text, needle, offset, true)
        if not first then
            parts[#parts + 1] = sub(text, offset)
            return concat(parts)
        end
        parts[#parts + 1] = sub(text, offset, first - 1)
        parts[#parts + 1] = replacement
        offset = last + 1
    end
end

local function searchpath(name, path, sep, rep)
    name = replace(name, sep, rep)
    local messages = {}
    for pattern in gmatch(path, '[^;]+') do
        local filename = replace(pattern, '?', name)
        if host.readable(filename) then return filename end
        messages[#messages + 1] = "\n\tno file '" .. filename .. "'"
    end
    return nil, concat(messages)
end

function pkg.searchpath(name, path, sep, rep)
    name = checkstring(name, 1, 'searchpath')
    path = checkstring(path, 2, 'searchpath')
    sep = sep == nil and '.' or checkstring(sep, 3, 'searchpath')
    rep = rep == nil and separator or checkstring(rep, 4, 'searchpath')
    return searchpath(name, path, sep, rep)
end

-- Matches Lua 5.2 built without a dynamic library backend.
local nativeError = 'dynamic libraries not enabled; check your Lua installation'
function pkg.loadlib(libname, funcname)
    checkstring(libname, 1, 'loadlib')
    checkstring(funcname, 2, 'loadlib')
    return nil, nativeError, 'absent'
end

local function findfile(name, field)
    local path = pkg[field]
    if type(path) ~= 'string' and type(path) ~= 'number' then
        error("'package." .. field .. "' must be a string", 3)
    end
    return searchpath(name, tostring(path), '.', separator)
end

local function loaderror(name, filename, message)
    error("error loading module '" .. name .. "' from file '" .. filename .. "':\n\t" .. message, 3)
end

pkg.searchers = {
    function(name)
        name = checkstring(name, 1, '?')
        local loader = registry._PRELOAD[name]
        if loader == nil then return "\n\tno field package.preload['" .. name .. "']" end
        return loader
    end,
    function(name)
        name = checkstring(name, 1, '?')
        local filename, message = findfile(name, 'path')
        if not filename then return message end
        local loader, reason = host.loadfile(filename)
        if not loader then loaderror(name, filename, reason) end
        return loader, filename
    end,
    function(name)
        name = checkstring(name, 1, '?')
        local filename, message = findfile(name, 'cpath')
        if not filename then return message end
        loaderror(name, filename, nativeError)
    end,
    function(name)
        name = checkstring(name, 1, '?')
        local dot = find(name, '.', 1, true)
        if not dot then return end
        local filename, message = findfile(sub(name, 1, dot - 1), 'cpath')
        if not filename then return message end
        loaderror(name, filename, nativeError)
    end
}

function require(name)
    name = checkstring(name, 1, 'require')
    local cache = registry._LOADED
    if cache[name] then return cache[name] end
    local searchers = pkg.searchers
    if type(searchers) ~= 'table' then error("'package.searchers' must be a table", 2) end
    local messages, index = {}, 1
    while true do
        local searcher = rawget(searchers, index)
        if searcher == nil then
            error("module '" .. name .. "' not found:" .. concat(messages), 2)
        end
        local loader, data = searcher(name)
        if type(loader) == 'function' then
            local result = loader(name, data)
            if result ~= nil then cache[name] = result end
            if cache[name] == nil then cache[name] = true end
            return cache[name]
        elseif type(loader) == 'string' or type(loader) == 'number' then
            messages[#messages + 1] = tostring(loader)
        end
        index = index + 1
    end
end
