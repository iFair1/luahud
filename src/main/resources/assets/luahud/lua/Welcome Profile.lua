-- Callback drawings are independent. Untimed sections refresh on the next
-- invocation of the same callback; a duration keeps a section for that many seconds.
lh.on("reload", function()
    lh.section({id = "welcomeReloadNotice", duration = 5, anchor = "bottomCenter"})
    lh.draw("LuaHUD profiles reloaded")
end)

lh.on("tick", function()
    lh.section({
        backgroundColor = 0,
        rowHeight = "auto",
        offsetX = 0,
    })

    lh.drawTexture("luahud:icon.png", { scale = 10, newLine = true })
    lh.drawln("Thanks for installing LuaHUD, " .. lh.player.name .. "!")
    lh.drawln("Run '/luahud help' to visit the documentation")
    lh.drawln("Press H to open mod screen")
    lh.drawln("Press J to toggle this")
end)
