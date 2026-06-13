import os
import re

keys_to_remove = [
    "title_seekbar_long_press_flat_region",
    "summary_seekbar_long_press_flat_region",
    "suffix_seekbar_flat_region_pixels",
    "title_seekbar_keyboard_toggle_fingers_native_touch",
    "summary_seekbar_keyboard_toggle_fingers_native_touch",
    "suffix_seekbar_keyboard_toggle_fingers_native_touch",
    "category_enhanced_touch_settings",
    "title_checkbox_enable_enhanced_touch",
    "summary_checkbox_enable_enhanced_touch",
    "title_checkbox_enable_keyboard_toggle_in_native_touch",
    "summary_checkbox_enable_keyboard_toggle_in_native_touch",
    "title_checkbox_sync_touch_event_with_display",
    "summary_checkbox_sync_touch_event_with_display",
    "title_checkbox_enhanced_touch_on_which_side",
    "summary_checkbox_enhanced_touch_on_which_side",
    "title_enhanced_touch_zone_divider",
    "summary_enhanced_touch_zone_divider",
    "dialog_title_enhanced_touch_zone_divider",
    "suffix_enhanced_touch_zone_divider",
    "title_pointer_velocity_factor",
    "summary_pointer_velocity_factor",
    "dialog_title_pointer_velocity_factor",
    "suffix_pointer_velocity_factor",
    "title_fixed_x_velocity",
    "summary_fixed_x_velocity",
    "dialog_title_fixed_x_velocity",
    "suffix_fixed_x_velocity"
]

res_dir = 'app/src/main/res'
for root, dirs, files in os.walk(res_dir):
    if 'strings.xml' in files:
        file_path = os.path.join(root, 'strings.xml')
        with open(file_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        new_lines = []
        skip = False
        for line in lines:
            if any(f'name="{key}"' in line for key in keys_to_remove):
                if not '</string>' in line:
                    skip = True
                continue
            if skip:
                if '</string>' in line:
                    skip = False
                continue
            new_lines.append(line)
        
        with open(file_path, 'w', encoding='utf-8') as f:
            f.writelines(new_lines)
print("Cleaned!")
