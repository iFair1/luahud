-- Edit this file if you want to change how LuaHUD shows errors, reloads etc.
-- Acts as a standard lua script
lh.on("error", function(error)
    if not error.isNew then return end
    lh.sendMessage({
        text = "[LuaHUD] ",
        color = "red",
        extra = {
            { text = "[" .. error.phase .. "] error in " .. error.group .. ": ", color = "white" },
            {
                text = "View",
                color = "white",
                underlined = true,
                click_event = { action = "suggest_command", command = "/luahud errors " .. error.id },
                hover_event = { action = "show_text", value = { text = error.message } },
            },
        },
    })
end)

lh.on("reload", function(profiles)
    local count = #profiles

    if count <= 0 then
        return -- A Profile has been disabled / 0 profiles are active
    end

    lh.section({
        id = "luahud:reload-indicator",
        anchor = "bottomCenter",
        offsetY = 60,
        duration = 3,
    })

    if count == 1 then
        lh.drawln(profiles[1].name .. " reloaded successfully!")
    else
        lh.drawln(count .. " profiles reloaded successfully!")
    end
end)

