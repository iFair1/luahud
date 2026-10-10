-- I'll add cool thing here
lh.on("tick", function ()
    -- Increase inlineSpacing to add background-filled pixels between elements.
    lh.section({ inlineSpacing = 0 })
    lh.drawln("Hello, World!")
    -- Model arguments accept tables with the required fields, including lh.player.
    lh.drawEntity(lh.player)
end)