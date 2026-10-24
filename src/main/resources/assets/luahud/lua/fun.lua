local ICON_SCALE = 8
local ICON_SIZE = 12 * ICON_SCALE
local SPEED = 0.15

local x, y = 0, 0
local dx, dy = 5 * SPEED, 3 * SPEED

local function move(position, velocity, maximum)
    position = position + velocity

    if position < 0 or position > maximum then
        velocity = -velocity
        position = math.max(0, math.min(position, maximum))
    end

    return position, velocity
end

lh.on("render", function()
    local width = lh.display.width / lh.display.scale
    local height = lh.display.height / lh.display.scale
    local maxX = math.max(0, width - ICON_SIZE)
    local maxY = math.max(0, height - ICON_SIZE)

    x, dx = move(x, dx, maxX)
    y, dy = move(y, dy, maxY)

    lh.section({
        anchor = "topLeft",
        offsetX = math.floor(x),
        offsetY = math.floor(y),
        backgroundColor = 0,
    })
    lh.drawTexture("luahud:icon.png", { scale = ICON_SCALE })
end)