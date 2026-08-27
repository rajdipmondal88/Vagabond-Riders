import math
import struct
import zlib
import os

def render_logo(output_size):
    scale = 2
    w = output_size * scale
    h = output_size * scale
    cx = w / 2.0
    cy = h / 2.0
    r = w / 2.0 - 2 * scale

    # RGBA buffer
    buf = bytearray(w * h * 4)

    def set_px(x, y, cr, cg, cb, ca):
        if x < 0 or x >= w or y < 0 or y >= h or ca <= 0:
            return
        idx = (y * w + x) * 4
        sa = ca / 255.0
        da = buf[idx+3] / 255.0
        
        # Alpha over
        out_a = sa + da * (1.0 - sa)
        if out_a > 0:
            buf[idx] = int((cr * sa + buf[idx] * da * (1.0 - sa)) / out_a)
            buf[idx+1] = int((cg * sa + buf[idx+1] * da * (1.0 - sa)) / out_a)
            buf[idx+2] = int((cb * sa + buf[idx+2] * da * (1.0 - sa)) / out_a)
            buf[idx+3] = int(out_a * 255)

    def draw_circle(cx, cy, radius, cr, cg, cb, ca=255, stroke=0, stroke_c=(0,0,0,0)):
        r_min = int(cy - radius - 2)
        r_max = int(cy + radius + 2)
        c_min = int(cx - radius - 2)
        c_max = int(cx + radius + 2)
        for py in range(max(0, r_min), min(h, r_max)):
            dy = py - cy
            for px in range(max(0, c_min), min(w, c_max)):
                dx = px - cx
                dist = math.hypot(dx, dy)
                if stroke > 0:
                    edge_dist = abs(dist - radius)
                    if edge_dist < stroke / 2.0 + 1:
                        alpha = 1.0
                        if edge_dist > stroke / 2.0 - 1:
                            alpha = (stroke / 2.0 + 1 - edge_dist) / 2.0
                        set_px(px, py, stroke_c[0], stroke_c[1], stroke_c[2], int(stroke_c[3] * alpha))
                else:
                    if dist < radius + 1:
                        alpha = 1.0
                        if dist > radius - 1:
                            alpha = (radius + 1 - dist) / 2.0
                        set_px(px, py, cr, cg, cb, int(ca * alpha))

    # 1. 3D Torus Orange Outer Ring
    for py in range(h):
        dy = py - cy
        for px in range(w):
            dx = px - cx
            dist = math.hypot(dx, dy)
            if dist <= r + 1:
                alpha = 1.0 if dist <= r - 1 else (r + 1 - dist) / 2.0
                
                # Shading for 3D Orange Donut (inner r ~ 0.77 * r)
                r_inner = r * 0.77
                if dist >= r_inner:
                    # Angle relative to top-left light source (-0.35, -0.45)
                    lx = dx / (dist + 0.001) - (-0.35)
                    ly = dy / (dist + 0.001) - (-0.45)
                    light = math.exp(-(lx*lx + ly*ly) * 0.9)
                    
                    # Torus cross section depth
                    mid_r = (r + r_inner) / 2.0
                    depth = 1.0 - (abs(dist - mid_r) / ((r - r_inner) / 2.0))**2
                    
                    red = int(245 + light * 20 - (1.0 - depth) * 20)
                    green = int(105 + light * 40 - (1.0 - depth) * 35)
                    blue = int(10 + light * 30)
                    
                    red = min(255, max(190, red))
                    green = min(180, max(50, green))
                    blue = min(120, max(0, blue))
                    
                    # Inner edge smoothing
                    if dist < r_inner + 2:
                        edge_a = (dist - r_inner) / 2.0
                        set_px(px, py, red, green, blue, int(255 * alpha * edge_a))
                    else:
                        set_px(px, py, red, green, blue, int(255 * alpha))

    # 2. White Separator Ring
    r_white_outer = r * 0.77
    r_white_inner = r * 0.73
    for py in range(h):
        dy = py - cy
        for px in range(w):
            dx = px - cx
            dist = math.hypot(dx, dy)
            if r_white_inner - 1 <= dist <= r_white_outer + 1:
                set_px(px, py, 255, 255, 255, 255)

    # 3. Vibrant Lime Green Disc
    r_green = r * 0.73
    for py in range(h):
        dy = py - cy
        for px in range(w):
            dx = px - cx
            dist = math.hypot(dx, dy)
            if dist <= r_green + 1:
                alpha = 1.0 if dist <= r_green - 1 else (r_green + 1 - dist) / 2.0
                # Lime green gradient (#80C836 to #6DAF2C)
                grad = (dy / r_green) * 0.5 + 0.5
                gr = int(128 - grad * 20)
                gg = int(200 - grad * 30)
                gb = int(54 - grad * 15)
                set_px(px, py, gr, gg, gb, int(255 * alpha))

    # 4. White Silhouette Shadow / Sticker Base for Motorcycle & Gear
    # Gear Teeth (12 radial teeth)
    gear_r_out = r * 0.55
    gear_r_in = r * 0.42
    gear_cy = cy - r * 0.05
    num_teeth = 12

    # Draw White Sticker outline around gear and bike
    # Draw gear teeth
    for i in range(num_teeth):
        mid_angle = i * (2 * math.pi / num_teeth)
        # Draw tooth rectangle / trapezoid in black with white border
        for step_r in range(int(gear_r_in * 0.9), int(gear_r_out * 1.15)):
            for step_a in range(-14, 15):
                angle = mid_angle + math.radians(step_a)
                px = int(cx + step_r * math.cos(angle))
                py = int(gear_cy + step_r * math.sin(angle))
                if step_r > gear_r_out * 1.05 or abs(step_a) > 10:
                    set_px(px, py, 255, 255, 255, 240) # White sticker outline
                else:
                    set_px(px, py, 24, 24, 27, 255) # Black gear tooth

    # Black Gear Ring
    for py in range(int(gear_cy - gear_r_out - 4), int(gear_cy + gear_r_out + 4)):
        dy = py - gear_cy
        for px in range(int(cx - gear_r_out - 4), int(cx + gear_r_out + 4)):
            dist = math.hypot(px - cx, dy)
            if gear_r_in <= dist <= gear_r_out:
                set_px(px, py, 24, 24, 27, 255)
            elif gear_r_in - 4 <= dist < gear_r_in:
                set_px(px, py, 115, 185, 48, 255) # Green hole

    # 5. Motorcycle Body Elements
    # Red Fuel Tank bulges (Left & Right)
    tank_w = r * 0.16
    tank_h = r * 0.22
    for py in range(int(cy - r * 0.08), int(cy + r * 0.20)):
        for px in range(int(cx - r * 0.46), int(cx + r * 0.46)):
            # Left tank
            ldx = (px - (cx - r * 0.28)) / tank_w
            ldy = (py - (cy + r * 0.05)) / tank_h
            if ldx*ldx + ldy*ldy <= 1.0:
                if ldx*ldx + ldy*ldy >= 0.78:
                    set_px(px, py, 255, 255, 255, 255) # White border
                else:
                    set_px(px, py, 225, 26, 26, 255) # Red tank

            # Right tank
            rdx = (px - (cx + r * 0.28)) / tank_w
            rdy = (py - (cy + r * 0.05)) / tank_h
            if rdx*rdx + rdy*rdy <= 1.0:
                if rdx*rdx + rdy*rdy >= 0.78:
                    set_px(px, py, 255, 255, 255, 255)
                else:
                    set_px(px, py, 225, 26, 26, 255)

    # Motorcycle Front Forks (Chrome white tubes with black line)
    fork_x_left = cx - r * 0.25
    fork_x_right = cx + r * 0.25
    fork_top = cy - r * 0.16
    fork_bot = cy + r * 0.18
    fork_thick = r * 0.035
    for py in range(int(fork_top), int(fork_bot)):
        for dx_f in range(int(-fork_thick), int(fork_thick + 1)):
            # Left Fork
            px_l = int(fork_x_left + dx_f)
            if abs(dx_f) == int(fork_thick):
                set_px(px_l, py, 24, 24, 27, 255)
            elif abs(dx_f) <= 1:
                set_px(px_l, py, 40, 40, 45, 255)
            else:
                set_px(px_l, py, 255, 255, 255, 255)
            
            # Right Fork
            px_r = int(fork_x_right + dx_f)
            if abs(dx_f) == int(fork_thick):
                set_px(px_r, py, 24, 24, 27, 255)
            elif abs(dx_f) <= 1:
                set_px(px_r, py, 40, 40, 45, 255)
            else:
                set_px(px_r, py, 255, 255, 255, 255)

    # Swept Handlebars
    bar_y_center = cy - r * 0.24
    for px in range(int(cx - r * 0.58), int(cx + r * 0.58)):
        norm_x = (px - cx) / (r * 0.58)
        py_curve = bar_y_center - math.cos(norm_x * math.pi * 0.5) * (r * 0.08)
        for dy_b in range(int(-r * 0.04), int(r * 0.04 + 1)):
            py = int(py_curve + dy_b)
            if abs(dy_b) >= int(r * 0.028):
                set_px(px, py, 255, 255, 255, 255) # White border
            else:
                set_px(px, py, 24, 24, 27, 255) # Black pipe

    # Teardrop Mirrors (Left & Right)
    mirror_r = r * 0.09
    mirror_l_x = cx - r * 0.56
    mirror_l_y = cy - r * 0.35
    mirror_r_x = cx + r * 0.56
    mirror_r_y = cy - r * 0.35
    for py in range(int(mirror_l_y - mirror_r * 1.3), int(mirror_l_y + mirror_r * 1.3)):
        for px in range(int(mirror_l_x - mirror_r * 1.3), int(mirror_l_x + mirror_r * 1.3)):
            d = math.hypot(px - mirror_l_x, py - mirror_l_y)
            if d <= mirror_r:
                if d >= mirror_r * 0.75:
                    set_px(px, py, 255, 255, 255, 255)
                else:
                    set_px(px, py, 24, 24, 27, 255)

    for py in range(int(mirror_r_y - mirror_r * 1.3), int(mirror_r_y + mirror_r * 1.3)):
        for px in range(int(mirror_r_x - mirror_r * 1.3), int(mirror_r_x + mirror_r * 1.3)):
            d = math.hypot(px - mirror_r_x, py - mirror_r_y)
            if d <= mirror_r:
                if d >= mirror_r * 0.75:
                    set_px(px, py, 255, 255, 255, 255)
                else:
                    set_px(px, py, 24, 24, 27, 255)

    # Red Turn Indicators (Left & Right)
    turn_r = r * 0.08
    turn_l_x = cx - r * 0.38
    turn_l_y = cy - r * 0.14
    turn_r_x = cx + r * 0.38
    turn_r_y = cy - r * 0.14
    for py in range(int(turn_l_y - turn_r * 1.4), int(turn_l_y + turn_r * 1.4)):
        for px in range(int(turn_l_x - turn_r * 1.4), int(turn_l_x + turn_r * 1.4)):
            d = math.hypot(px - turn_l_x, py - turn_l_y)
            if d <= turn_r:
                if d >= turn_r * 0.8:
                    set_px(px, py, 255, 255, 255, 255)
                elif d < turn_r * 0.35:
                    set_px(px, py, 255, 255, 255, 255) # Center white gleam
                else:
                    set_px(px, py, 225, 26, 26, 255)

    for py in range(int(turn_r_y - turn_r * 1.4), int(turn_r_y + turn_r * 1.4)):
        for px in range(int(turn_r_x - turn_r * 1.4), int(turn_r_x + turn_r * 1.4)):
            d = math.hypot(px - turn_r_x, py - turn_r_y)
            if d <= turn_r:
                if d >= turn_r * 0.8:
                    set_px(px, py, 255, 255, 255, 255)
                elif d < turn_r * 0.35:
                    set_px(px, py, 255, 255, 255, 255)
                else:
                    set_px(px, py, 225, 26, 26, 255)

    # Classic Round Headlight
    hl_cy = cy - r * 0.06
    hl_r = r * 0.20
    for py in range(int(hl_cy - hl_r * 1.2), int(hl_cy + hl_r * 1.2)):
        dy = py - hl_cy
        for px in range(int(cx - hl_r * 1.2), int(cx + hl_r * 1.2)):
            dx = px - cx
            dist = math.hypot(dx, dy)
            if dist <= hl_r * 1.15:
                if dist > hl_r:
                    set_px(px, py, 255, 255, 255, 255) # Outer white ring
                elif dist > hl_r * 0.82:
                    set_px(px, py, 24, 24, 27, 255) # Black housing
                elif dist > hl_r * 0.76:
                    set_px(px, py, 255, 255, 255, 255) # Chrome ring
                else:
                    # Glass lens fluting
                    is_flute = (int(abs(dx)) % int(hl_r * 0.25) < 2)
                    if dist < hl_r * 0.30:
                        if dist < hl_r * 0.12:
                            set_px(px, py, 255, 255, 255, 255) # Bulb point
                        else:
                            set_px(px, py, 24, 24, 27, 255) # Bulb housing
                    elif is_flute:
                        set_px(px, py, 40, 40, 45, 255) # Vertical glass lines
                    else:
                        set_px(px, py, 245, 248, 250, 255) # Glass lens

    # 6. Shield Banner ("Vagabond Riders", "2020", Stars)
    banner_top = cy + r * 0.16
    banner_w = r * 0.68
    banner_h_rect = r * 0.18
    banner_tip_y = cy + r * 0.58

    for py in range(int(banner_top - 4), int(banner_tip_y + 6)):
        for px in range(int(cx - banner_w - 6), int(cx + banner_w + 6)):
            dx = abs(px - cx)
            in_shield = False
            
            if py < banner_top + banner_h_rect:
                if dx <= banner_w:
                    in_shield = True
            else:
                # Triangular shield point
                t = (py - (banner_top + banner_h_rect)) / (banner_tip_y - (banner_top + banner_h_rect) + 0.001)
                max_dx = banner_w * (1.0 - t)
                if dx <= max_dx:
                    in_shield = True
            
            if in_shield:
                # Border vs Fill
                is_border = False
                if py <= banner_top + 4 or py >= banner_tip_y - 4:
                    is_border = True
                elif py < banner_top + banner_h_rect and dx >= banner_w - 4:
                    is_border = True
                elif py >= banner_top + banner_h_rect:
                    t = (py - (banner_top + banner_h_rect)) / (banner_tip_y - (banner_top + banner_h_rect) + 0.001)
                    if dx >= banner_w * (1.0 - t) - 4:
                        is_border = True

                if is_border:
                    set_px(px, py, 255, 255, 255, 255)
                else:
                    set_px(px, py, 24, 24, 27, 255)

    # 7. Draw Stars on Shield Tip
    def draw_star(sx, sy, sr):
        for py in range(int(sy - sr), int(sy + sr + 1)):
            for px in range(int(sx - sr), int(sx + sr + 1)):
                if math.hypot(px - sx, py - sy) <= sr * 0.85:
                    set_px(px, py, 255, 255, 255, 255)

    draw_star(cx, banner_top + r * 0.30, r * 0.045)
    draw_star(cx - r * 0.16, banner_top + r * 0.26, r * 0.032)
    draw_star(cx + r * 0.16, banner_top + r * 0.26, r * 0.032)

    # Downsample
    out_pixels = []
    for oy in range(output_size):
        for ox in range(output_size):
            r_acc, g_acc, b_acc, a_acc = 0, 0, 0, 0
            for sy in range(scale):
                for sx in range(scale):
                    idx = ((oy * scale + sy) * w + (ox * scale + sx)) * 4
                    r_acc += buf[idx]
                    g_acc += buf[idx + 1]
                    b_acc += buf[idx + 2]
                    a_acc += buf[idx + 3]
            count = scale * scale
            out_pixels.append((r_acc // count, g_acc // count, b_acc // count, a_acc // count))

    # PNG format
    def chunk(tag, data):
        return struct.pack('!I', len(data)) + tag + data + struct.pack('!I', zlib.crc32(tag + data) & 0xffffffff)
    
    raw = bytearray()
    for y in range(output_size):
        raw.append(0)
        for x in range(output_size):
            pr, pg, pb, pa = out_pixels[y * output_size + x]
            raw.extend((pr, pg, pb, pa))
    
    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('!IIBBBBB', output_size, output_size, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
    png += chunk(b'IEND', b'')

    return png

# Generate PNGs for drawables & mipmaps
sizes = [
    ('app/src/main/res/drawable/ic_vr_logo_official.png', 512),
    ('app/src/main/res/mipmap-mdpi/ic_launcher.png', 48),
    ('app/src/main/res/mipmap-mdpi/ic_launcher_round.png', 48),
    ('app/src/main/res/mipmap-hdpi/ic_launcher.png', 72),
    ('app/src/main/res/mipmap-hdpi/ic_launcher_round.png', 72),
    ('app/src/main/res/mipmap-xhdpi/ic_launcher.png', 96),
    ('app/src/main/res/mipmap-xhdpi/ic_launcher_round.png', 96),
    ('app/src/main/res/mipmap-xxhdpi/ic_launcher.png', 144),
    ('app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png', 144),
    ('app/src/main/res/mipmap-xxxhdpi/ic_launcher.png', 192),
    ('app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png', 192),
]

for path, size in sizes:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    png_data = render_logo(size)
    with open(path, 'wb') as f:
        f.write(png_data)
    print(f"Generated {path} ({size}x{size})")
