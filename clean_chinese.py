import os
import re

def clean_file(path):
    if not os.path.exists(path): return
    with open(path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    new_lines = []
    for line in lines:
        if re.search(r'[\u4e00-\u9fff]', line):
            # If it's a comment line, we can just skip it
            if line.strip().startswith('//') or line.strip().startswith('*') or line.strip().startswith('/*') or line.strip().startswith('Log.'):
                continue
            # If there's inline comment with Chinese, strip it
            line = re.sub(r'//.*[\u4e00-\u9fff].*$', '', line)
            line = re.sub(r'/\*.*[\u4e00-\u9fff].*\*/', '', line)
            if re.search(r'[\u4e00-\u9fff]', line):
                # if still has Chinese (maybe a string), let's replace it with English or empty
                line = re.sub(r'"[^"]*[\u4e00-\u9fff][^"]*"', '""', line)
        new_lines.append(line)
        
    with open(path, 'w', encoding='utf-8') as f:
        f.writelines(new_lines)

files_to_clean = [
    'library-attect/src/main/java/studio/attect/limelight/GameAttectActivity.kt',
    'library-attect/src/main/java/studio/attect/limelight/ui/TouchDragListener.kt',
    'app/src/main/java/com/limelight/Game.java',
]

for f in files_to_clean:
    clean_file(f)

