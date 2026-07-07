import re
import json
import os

java_file_path = "/home/sh20fk/minecraft-mod-build/src/main/java/com/project3/achievement/AchievementManager.java"
json_dir = "/home/sh20fk/minecraft-mod-build/src/main/resources/data/p3/advancement/"

with open(java_file_path, "r", encoding="utf-8") as f:
    content = f.read()

# We can parse the lines inside registerDefaultAchievements
lines = content.splitlines()
in_register = False

achievements = {}

for line in lines:
    if "private void registerDefaultAchievements()" in line:
        in_register = True
        continue
    if in_register and "}" in line and line.strip() == "}":
        in_register = False
        break
    if in_register:
        # Match addAchievement("id", "parent", "title", "desc", ...) or addAchievement("id", "title", "desc", ...)
        # Let's extract string literals
        strings = re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', line)
        if len(strings) >= 3:
            ach_id = strings[0]
            if strings[1].startswith("ach_"):
                title = strings[2]
                desc = strings[3]
            else:
                title = strings[1]
                desc = strings[2]
            # Unescape any quotes
            title = title.replace('\\"', '"')
            desc = desc.replace('\\"', '"')
            achievements[ach_id] = (title, desc)

print(f"Found {len(achievements)} achievements in java file.")

# Now let's update the JSON files
updated_count = 0
for ach_id, (title, desc) in achievements.items():
    json_path = os.path.join(json_dir, f"{ach_id}.json")
    if os.path.exists(json_path):
        with open(json_path, "r", encoding="utf-8") as f:
            data = json.load(f)
        
        # Update display
        if "display" in data:
            if "title" in data["display"]:
                data["display"]["title"]["text"] = title
            if "description" in data["display"]:
                data["display"]["description"]["text"] = desc
                
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        updated_count += 1
    else:
        print(f"File {ach_id}.json not found!")

print(f"Successfully synchronized {updated_count} JSON files.")
