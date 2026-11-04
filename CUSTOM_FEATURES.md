# Custom features added on top of PojavLauncher

## Content browser (Modrinth + CurseForge)
Main menu -> "Get mods, packs and shaders". Chips switch between Modpacks, Mods, Resource packs and Shaders.
- Filters: Minecraft version and mod loader (Fabric / Forge / NeoForge / Quilt).
- Single files go to `mods/`, `resourcepacks/` or `shaderpacks/` of the **target profile** ("Install to: ..." / Change).
- Downloads go to a `.part` file first, then are moved into place (a half-downloaded jar can't crash the game).
- Modrinth mods: required dependencies are downloaded automatically (3 levels deep). CurseForge: not resolved.
- Modpacks work as before (creates a new profile).
- Code: `modloaders/modpacks/` (`ContentType`, `ContentTarget`, `api/ContentInstaller`, `api/ModrinthApi`, `api/CurseforgeApi`), `fragments/SearchModFragment`.

## Installed content manager
Main menu -> "Manage installed content": list, enable/disable (renames to `.disabled`), delete, and "Add from device".
Code: `fragments/InstalledContentFragment`.

## Cursor options
Settings -> Controls -> Virtual mouse: cursor style (default, arrow, dot, ring, crosshair, custom image) and **Dynamic cursor**.
Dynamic cursor changes the cursor *shape* with the context: when the game or a mod calls `glfwSetCursor`, the virtual mouse becomes a
hand, text I-beam, crosshair, resize arrows (horizontal / vertical / diagonal / all) or a "not allowed" sign. It also shrinks and glows while clicking.
The normal arrow shape uses the style you picked.

How it works: `GLFW.glfwSetCursor` (JRE stub) -> `CallbackBridge.nativeSetCursorShape` -> `input_bridge_v3.c` ->
Android `CallbackBridge.onCursorShapeChanged` -> `CursorState` -> `Touchpad`. Native part mirrors how grab state is already relayed.
Code: `customcontrols/mouse/{Touchpad,CursorStyle,CursorState}`, `jre_lwjgl3glfw/.../GLFW.java`, `jni/input_bridge_v3.c`.

Limit: vanilla Minecraft rarely asks for cursor shapes, so with no mods you will mostly see the arrow. Mods that add cursors
(e.g. Minecraft Cursor on Modrinth) will make the shapes change. The touch screen itself (tapping menus directly) has no cursor to change.
Needs the JRE glfw stub and native lib rebuilt (a normal full build does this).

## Look
New dark palette with green accent, rounded cards/buttons (`colors.xml`, `styles.xml`, `drawable/bg_*`).

## Not verified
None of this has been compiled or run (no Android SDK in the authoring environment). Build with Android Studio / `./gradlew assembleDebug`
and test on a device. Things worth testing first: installing a mod, a resource pack and a shader; the dynamic cursor with a cursor mod installed (also confirm the native build succeeds); the new menu layout on a small phone.

## Known limits
- Mod search with "Any" loader can offer versions for a loader your profile doesn't use. Pick a loader in the filter.
- CurseForge files whose authors disabled third-party downloads are hidden (no download URL).
