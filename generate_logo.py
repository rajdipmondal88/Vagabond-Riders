import math
import struct
import zlib
import os

def create_vr_logo_png(output_path, size=512):
    # 2x supersampling for ultra crisp anti-aliased edges
    scale = 2
    W = size * scale
    H = size * scale
    CX = W / 2.0
    CY = H / 2.0
    R = (W / 2.0) - (4 * scale)

    # RGBA buffer
    buffer = bytearray(W * H * 4)

    def blend_pixel(x, y, r, g, b, a):
        if x < 0 or x >= W or y < 0 or y >= H:
            return
        idx = (y * W + x) * 4
        src_a = a / 255.0
        inv_a = 1.0 - src_a

        dst_r = buffer[idx]
        dst_g = buffer[idx + 1]
        dst_b = buffer[idx + 2]
        dst_a = buffer[idx + 3] / 255.0

        out_a = src_a + dst_a * inv_a
        if out_a > 0:
            out_r = (r * src_a + dst_r * dst_a * inv_a) / out_a
            out_g = (g * src_a + dst_g * dst_a * inv_a) / out_a
            out_b = (b * src_a + dst_b * dst_a * inv_a) / out_a
        else:
            out_r, out_g, out_b = 0, 0, 0

        buffer[idx] = int(min(255, max(0, out_r)))
        buffer[idx + 1] = int(min(255, max(0, out_g)))
        buffer[idx + 2] = int(min(255, max(0, out_b)))
        buffer[idx + 3] = int(min(255, max(0, out_a * 255)))

    # Rasterize geometry
    # 1. Background / Shadow
    # 2. Orange 3D Ring
    # 3. White Separator Ring
    # 4. Lime Green Disc
    # 5. Sticker Silhouette (White)
    # 6. Mechanical Gear (Black)
    # 7. Red Fuel Tank wings
    # 8. Front suspension forks
    # 9. Swept Handlebars & Mirrors
    # 10. Turn signals (Red)
    # 11. Headlight (Black housing, Chrome lens, fluting lines)
    # 12. Shield Banner (Black with White border)
    # 13. Text & Stars
    
    # Distance helpers
    for y in range(H):
        dy = y - CY
        for x in range(W):
            dx = x - CX
            dist = math.hypot(dx, dy)

            # Outer boundary anti-aliased
            if dist <= R + 1:
                alpha = 1.0
                if dist > R - 1:
                    alpha = (R + 1 - dist) / 2.0
                
                # 3D Orange Donut Bezel calculation
                # Highlight from top-left (-0.35, -0.45)
                # Outer radius R, inner radius R * 0.77
                r_inner_orange = R * 0.77
                if dist >= r_inner_orange:
                    # Light normal based on torus curvature
                    norm_r = (dist - (R + r_inner_orange)/2.0) / ((R - r_inner_orange)/2.0) # -1 to +1
                    # Angle light
                    light_dx = dx / (dist + 0.001) - (-0.3)
                    light_dy = dy / (dist + 0.001) - (-0.4)
                    light_val = math.exp(-(light_dx*light_dx + light_dy*light_dy)*0.8)
                    
                    # Gradient across angle and radius
                    base_r = 250 - int(norm_r * 25) + int(light_val * 45)
                    base_g = 105 - int(norm_r * 20) + int(light_val * 50)
                    base_b = 8 + int(light_val * 20)
                    
                    base_r = min(255, max(180, base_r))
                    base_g = min(200, max(50, base_g))
                    base_b = min(120, max(0, base_b))

                    # Edge smooth to inner white ring
                    if dist < r_inner_orange + 2:
                        blend_w = (dist - r_inner_orange) / 2.0
                        blend_pixel(x, y, base_r, base_g, base_b, int(255 * alpha * blend_w))
                    else:
                        blend_pixel(x, y, base_r, base_g, base_b, int(255 * alpha))

                # White Ring (R * 0.77 to R * 0.73)
                r_white_inner = R * 0.73
                if r_white_inner <= dist < r_inner_orange + 1:
                    blend_pixel(x, y, 255, 255, 255, int(255 * alpha))

                # Lime Green Disc (0 to R * 0.73)
                if dist < r_white_inner + 1:
                    # Lime green radial gradient
                    norm_g = dist / r_white_inner
                    g_r = int(120 - norm_g * 30 + (1.0 - (dy/R + 0.3)) * 15)
                    g_g = int(195 - norm_g * 35)
                    g_b = int(55 - norm_g * 20)
                    g_r = min(255, max(60, g_r))
                    g_g = min(255, max(130, g_g))
                    g_b = min(255, max(20, g_b))
                    blend_pixel(x, y, g_r, g_g, g_b, int(255 * alpha))

    # Downsample buffer from W x H to target size x size
    out_w = size
    out_h = size
    out_pixels = []
    
    for oy in range(out_h):
        for ox in range(out_w):
            r_acc, g_acc, b_acc, a_acc = 0, 0, 0, 0
            for sy in range(scale):
                for sx in range(scale):
                    idx = ((oy * scale + sy) * W + (ox * scale + sx)) * 4
                    r_acc += buffer[idx]
                    g_acc += buffer[idx + 1]
                    b_acc += buffer[idx + 2]
                    a_acc += buffer[idx + 3]
            count = scale * scale
            out_pixels.append((r_acc // count, g_acc // count, b_acc // count, a_acc // count))

    # Write PNG
    def chunk(tag, data):
        return struct.pack('!I', len(data)) + tag + data + struct.pack('!I', zlib.crc32(tag + data) & 0xffffffff)
    
    raw = bytearray()
    for y in range(out_h):
        raw.append(0)
        for x in range(out_w):
            r, g, b, a = out_pixels[y * out_w + x]
            raw.extend((r, g, b, a))
    
    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('!IIBBBBB', out_w, out_h, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
    png += chunk(b'IEND', b'')

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, 'wb') as f:
        f.write(png)
    print(f"Generated PNG: {output_path} ({len(png)} bytes)")

if __name__ == '__main__':
    create_vr_logo_png('test_logo.png', 512)
