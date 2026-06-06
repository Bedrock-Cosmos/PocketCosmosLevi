package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.view.MotionEvent;

import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.core.mods.inbuilt.model.ModIds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InbuiltOverlayManager {
    private static volatile InbuiltOverlayManager instance;
    private final Activity activity;
    private final List<BaseOverlayButton> overlays = new ArrayList<>();
    private final Map<String, Boolean> modActiveStates = new HashMap<>();
    private final Map<String, BaseOverlayButton> modOverlayMap = new HashMap<>();
    private final Map<String, Integer> modPositionMap = new HashMap<>();
    private ChickPetOverlay chickPetOverlay;
    private ZoomOverlay zoomOverlay;
    private SnaplookOverlay snaplookOverlay;
    private FpsDisplayOverlay fpsDisplayOverlay;
    private CpsDisplayOverlay cpsDisplayOverlay;
    private ModMenuButton modMenuButton;
    private HttpInterceptorOverlay httpInterceptorOverlay;
    private int baseY = 150;
    private static final int SPACING = 70;
    private static final int START_X = 50;
    private boolean isModMenuMode = false;

    public InbuiltOverlayManager(Activity activity) {
        this.activity = activity;
        instance = this;
    }

    public static InbuiltOverlayManager getInstance() {
        return instance;
    }

    public void showEnabledOverlays() {
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        int nextY = baseY;

        isModMenuMode = manager.isModMenuEnabled();

        if (isModMenuMode) {
            nextY = showModMenuMode(manager, nextY);
        } else {
            nextY = showIndividualOverlays(manager, nextY);
        }

    }

    private int showModMenuMode(InbuiltModManager manager, int nextY) {
        modActiveStates.put(ModIds.QUICK_DROP, false);
        modActiveStates.put(ModIds.CAMERA_PERSPECTIVE, false);
        modActiveStates.put(ModIds.TOGGLE_HUD, false);
        modActiveStates.put(ModIds.AUTO_SPRINT, false);
        modActiveStates.put(ModIds.CHICK_PET, false);
        modActiveStates.put(ModIds.ZOOM, false);
        modActiveStates.put(ModIds.FPS_DISPLAY, false);
        modActiveStates.put(ModIds.CPS_DISPLAY, false);
        modActiveStates.put(ModIds.SNAPLOOK, false);
        modActiveStates.put(ModIds.VIRTUAL_CURSOR, false);
        modActiveStates.put(ModIds.HTTP_INTERCEPTOR, true);

        modPositionMap.put(ModIds.QUICK_DROP, nextY + SPACING);
        modPositionMap.put(ModIds.CAMERA_PERSPECTIVE, nextY + SPACING * 2);
        modPositionMap.put(ModIds.TOGGLE_HUD, nextY + SPACING * 3);
        modPositionMap.put(ModIds.AUTO_SPRINT, nextY + SPACING * 4);
        modPositionMap.put(ModIds.ZOOM, nextY + SPACING * 5);
        modPositionMap.put(ModIds.FPS_DISPLAY, nextY + SPACING * 6);
        modPositionMap.put(ModIds.CPS_DISPLAY, nextY + SPACING * 7);
        modPositionMap.put(ModIds.SNAPLOOK, nextY + SPACING * 8);
        modPositionMap.put(ModIds.VIRTUAL_CURSOR, nextY + SPACING * 9);

        if (zoomOverlay == null) {
            zoomOverlay = new ZoomOverlay(activity);
            zoomOverlay.initializeForKeyboard();
        }

        if (snaplookOverlay == null) {
            snaplookOverlay = new SnaplookOverlay(activity);
            snaplookOverlay.initializeForKeyboard();
        }

        modMenuButton = new ModMenuButton(activity);
        modMenuButton.show(START_X, nextY);
        showModOverlay(ModIds.HTTP_INTERCEPTOR);
        return nextY + SPACING;
    }

    public void handleModToggle(String modId, boolean enabled) {
        boolean wasEnabled = modActiveStates.getOrDefault(modId, false);
        modActiveStates.put(modId, enabled);
        
        if (enabled && !wasEnabled) {
            showModOverlay(modId);
        } else if (!enabled && wasEnabled) {
            hideModOverlay(modId);
        }
    }

    private void showModOverlay(String modId) {
        if (modOverlayMap.containsKey(modId)) {
            return;
        }

        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        int posY = modPositionMap.getOrDefault(modId, baseY + SPACING);

        int savedX = manager.getOverlayPositionX(modId, START_X);
        int savedY = manager.getOverlayPositionY(modId, posY);

        switch (modId) {
            case ModIds.QUICK_DROP:
                QuickDropOverlay quickDrop = new QuickDropOverlay(activity);
                quickDrop.show(savedX, savedY);
                overlays.add(quickDrop);
                modOverlayMap.put(modId, quickDrop);
                break;
            case ModIds.CAMERA_PERSPECTIVE:
                CameraPerspectiveOverlay camera = new CameraPerspectiveOverlay(activity);
                camera.show(savedX, savedY);
                overlays.add(camera);
                modOverlayMap.put(modId, camera);
                break;
            case ModIds.TOGGLE_HUD:
                ToggleHudOverlay hud = new ToggleHudOverlay(activity);
                hud.show(savedX, savedY);
                overlays.add(hud);
                modOverlayMap.put(modId, hud);
                break;
            case ModIds.AUTO_SPRINT:
                AutoSprintOverlay sprint = new AutoSprintOverlay(activity, manager.getAutoSprintKeybind());
                sprint.show(savedX, savedY);
                overlays.add(sprint);
                modOverlayMap.put(modId, sprint);
                break;
            case ModIds.CHICK_PET:
                if (chickPetOverlay == null) {
                    chickPetOverlay = new ChickPetOverlay(activity);
                    chickPetOverlay.show();
                }
                break;
            case ModIds.ZOOM:
                if (zoomOverlay == null) {
                    zoomOverlay = new ZoomOverlay(activity);
                }
                zoomOverlay.show(savedX, savedY);
                overlays.add(zoomOverlay);
                modOverlayMap.put(modId, zoomOverlay);
                break;
            case ModIds.FPS_DISPLAY:
                if (fpsDisplayOverlay == null) {
                    fpsDisplayOverlay = new FpsDisplayOverlay(activity);
                    fpsDisplayOverlay.show(savedX, savedY);
                }
                break;
            case ModIds.CPS_DISPLAY:
                if (cpsDisplayOverlay == null) {
                    cpsDisplayOverlay = new CpsDisplayOverlay(activity);
                    cpsDisplayOverlay.show(savedX, savedY);
                }
                break;
            case ModIds.SNAPLOOK:
                if (snaplookOverlay == null) {
                    snaplookOverlay = new SnaplookOverlay(activity);
                }
                snaplookOverlay.show(savedX, savedY);
                overlays.add(snaplookOverlay);
                modOverlayMap.put(modId, snaplookOverlay);
                break;
            case ModIds.VIRTUAL_CURSOR:
                VirtualCursorOverlay cursorOverlay = new VirtualCursorOverlay(activity);
                cursorOverlay.show(savedX, savedY);
                overlays.add(cursorOverlay);
                modOverlayMap.put(modId, cursorOverlay);
                break;
            case ModIds.HTTP_INTERCEPTOR:
                if (httpInterceptorOverlay == null) {
                    httpInterceptorOverlay = new HttpInterceptorOverlay(activity);
                    httpInterceptorOverlay.show(0, 0);
                }
                break;
        }
    }

    private void hideModOverlay(String modId) {
        if (modId.equals(ModIds.CHICK_PET)) {
            if (chickPetOverlay != null) {
                chickPetOverlay.hide();
                chickPetOverlay = null;
            }
            return;
        }
        
        if (modId.equals(ModIds.ZOOM)) {
            if (zoomOverlay != null) {
                zoomOverlay.hide();
                overlays.remove(zoomOverlay);
                modOverlayMap.remove(modId);

                if (!isModMenuMode) {
                    zoomOverlay = null;
                }
            }
            return;
        }

        if (modId.equals(ModIds.FPS_DISPLAY)) {
            if (fpsDisplayOverlay != null) {
                fpsDisplayOverlay.hide();
                fpsDisplayOverlay = null;
            }
            return;
        }

        if (modId.equals(ModIds.CPS_DISPLAY)) {
            if (cpsDisplayOverlay != null) {
                cpsDisplayOverlay.hide();
                cpsDisplayOverlay = null;
            }
            return;
        }

        if (modId.equals(ModIds.SNAPLOOK)) {
            if (snaplookOverlay != null) {
                snaplookOverlay.hide();
                overlays.remove(snaplookOverlay);
                modOverlayMap.remove(modId);

                if (!isModMenuMode) {
                    snaplookOverlay = null;
                }
            }
            return;
        }
        
        BaseOverlayButton overlay = modOverlayMap.get(modId);
        if (overlay != null) {
            overlay.hide();
            overlays.remove(overlay);
            modOverlayMap.remove(modId);
        }
    }

    public boolean isModActive(String modId) {
        return modActiveStates.getOrDefault(modId, false);
    }

    private int showIndividualOverlays(InbuiltModManager manager, int nextY) {
        if (manager.isModAdded(ModIds.QUICK_DROP)) {
            int x = manager.getOverlayPositionX(ModIds.QUICK_DROP, START_X);
            int y = manager.getOverlayPositionY(ModIds.QUICK_DROP, nextY);
            QuickDropOverlay overlay = new QuickDropOverlay(activity);
            overlay.show(x, y);
            overlays.add(overlay);
            nextY += SPACING;
        }
        if (manager.isModAdded(ModIds.CAMERA_PERSPECTIVE)) {
            int x = manager.getOverlayPositionX(ModIds.CAMERA_PERSPECTIVE, START_X);
            int y = manager.getOverlayPositionY(ModIds.CAMERA_PERSPECTIVE, nextY);
            CameraPerspectiveOverlay overlay = new CameraPerspectiveOverlay(activity);
            overlay.show(x, y);
            overlays.add(overlay);
            nextY += SPACING;
        }
        if (manager.isModAdded(ModIds.TOGGLE_HUD)) {
            int x = manager.getOverlayPositionX(ModIds.TOGGLE_HUD, START_X);
            int y = manager.getOverlayPositionY(ModIds.TOGGLE_HUD, nextY);
            ToggleHudOverlay overlay = new ToggleHudOverlay(activity);
            overlay.show(x, y);
            overlays.add(overlay);
            nextY += SPACING;
        }
        if (manager.isModAdded(ModIds.AUTO_SPRINT)) {
            int x = manager.getOverlayPositionX(ModIds.AUTO_SPRINT, START_X);
            int y = manager.getOverlayPositionY(ModIds.AUTO_SPRINT, nextY);
            AutoSprintOverlay overlay = new AutoSprintOverlay(activity, manager.getAutoSprintKeybind());
            overlay.show(x, y);
            overlays.add(overlay);
            nextY += SPACING;
        }

        if (manager.isModAdded(ModIds.CHICK_PET)) {
            chickPetOverlay = new ChickPetOverlay(activity);
            chickPetOverlay.show();
        }

        if (manager.isModAdded(ModIds.ZOOM)) {
            int x = manager.getOverlayPositionX(ModIds.ZOOM, START_X);
            int y = manager.getOverlayPositionY(ModIds.ZOOM, nextY);
            zoomOverlay = new ZoomOverlay(activity);
            zoomOverlay.show(x, y);
            overlays.add(zoomOverlay);
            modOverlayMap.put(ModIds.ZOOM, zoomOverlay);
            nextY += SPACING;
        }

        if (manager.isModAdded(ModIds.FPS_DISPLAY)) {
            int x = manager.getOverlayPositionX(ModIds.FPS_DISPLAY, START_X);
            int y = manager.getOverlayPositionY(ModIds.FPS_DISPLAY, nextY);
            fpsDisplayOverlay = new FpsDisplayOverlay(activity);
            fpsDisplayOverlay.show(x, y);
            nextY += SPACING;
        }

        if (manager.isModAdded(ModIds.CPS_DISPLAY)) {
            int x = manager.getOverlayPositionX(ModIds.CPS_DISPLAY, START_X);
            int y = manager.getOverlayPositionY(ModIds.CPS_DISPLAY, nextY);
            cpsDisplayOverlay = new CpsDisplayOverlay(activity);
            cpsDisplayOverlay.show(x, y);
            nextY += SPACING;
        }

        if (manager.isModAdded(ModIds.HTTP_INTERCEPTOR)) {
            httpInterceptorOverlay = new HttpInterceptorOverlay(activity);
            httpInterceptorOverlay.show(0, 0);
        }

        if (manager.isModAdded(ModIds.SNAPLOOK)) {
            int x = manager.getOverlayPositionX(ModIds.SNAPLOOK, START_X);
            int y = manager.getOverlayPositionY(ModIds.SNAPLOOK, nextY);
            snaplookOverlay = new SnaplookOverlay(activity);
            snaplookOverlay.show(x, y);
            overlays.add(snaplookOverlay);
            modOverlayMap.put(ModIds.SNAPLOOK, snaplookOverlay);
            nextY += SPACING;
        }
        if (manager.isModAdded(ModIds.VIRTUAL_CURSOR)) {
            int x = manager.getOverlayPositionX(ModIds.VIRTUAL_CURSOR, START_X);
            int y = manager.getOverlayPositionY(ModIds.VIRTUAL_CURSOR, nextY);
            VirtualCursorOverlay overlay = new VirtualCursorOverlay(activity);
            overlay.show(x, y);
            overlays.add(overlay);
            nextY += SPACING;
        }
        return nextY;
    }


    public void hideAllOverlays() {
        for (BaseOverlayButton overlay : overlays) {
            overlay.hide();
        }
        overlays.clear();
        modOverlayMap.clear();
        modActiveStates.clear();
        modPositionMap.clear();
        if (chickPetOverlay != null) {
            chickPetOverlay.hide();
            chickPetOverlay = null;
        }
        if (zoomOverlay != null) {
            zoomOverlay.hide();
            zoomOverlay = null;
        }
        if (fpsDisplayOverlay != null) {
            fpsDisplayOverlay.hide();
            fpsDisplayOverlay = null;
        }
        if (cpsDisplayOverlay != null) {
            cpsDisplayOverlay.hide();
            cpsDisplayOverlay = null;
        }
        if (snaplookOverlay != null) {
            snaplookOverlay.hide();
            snaplookOverlay = null;
        }
        if (modMenuButton != null) {
            modMenuButton.hide();
            modMenuButton = null;
        }
        if (httpInterceptorOverlay != null) {
            httpInterceptorOverlay.hide();
            httpInterceptorOverlay = null;
        }
        instance = null;
    }

    public boolean handleKeyEvent(int keyCode, int action) {
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        
        boolean zoomEnabled = isModMenuMode 
            ? modActiveStates.getOrDefault(ModIds.ZOOM, false)
            : manager.isModAdded(ModIds.ZOOM);
        
        int zoomKeybind = manager.getZoomKeybind();
        if (zoomEnabled && keyCode == zoomKeybind) {
            if (zoomOverlay != null) {
                if (action == android.view.KeyEvent.ACTION_DOWN) {
                    zoomOverlay.onKeyDown();
                    return true;
                } else if (action == android.view.KeyEvent.ACTION_UP) {
                    zoomOverlay.onKeyUp();
                    return true;
                }
            }
        }

        boolean snaplookEnabled = isModMenuMode
            ? modActiveStates.getOrDefault(ModIds.SNAPLOOK, false)
            : manager.isModAdded(ModIds.SNAPLOOK);

        if (snaplookEnabled && keyCode == android.view.KeyEvent.KEYCODE_X) {
            if (snaplookOverlay != null) {
                if (action == android.view.KeyEvent.ACTION_DOWN) {
                    snaplookOverlay.onKeyDown();
                    return true;
                } else if (action == android.view.KeyEvent.ACTION_UP) {
                    snaplookOverlay.onKeyUp();
                    return true;
                }
            }
        }

        return false;
    }

    public boolean handleScrollEvent(float scrollDelta) {
        if (zoomOverlay != null && zoomOverlay.isZooming()) {
            zoomOverlay.onScroll(scrollDelta);
            return true;
        }
        return false;
    }

    public boolean handleTouchEvent(MotionEvent event) {
        if (cpsDisplayOverlay != null) {
            return cpsDisplayOverlay.handleTouchEvent(event);
        }
        return false;
    }

    public boolean handleMouseEvent(MotionEvent event) {
        if (cpsDisplayOverlay != null) {
            return cpsDisplayOverlay.handleMouseEvent(event);
        }
        return false;
    }

    public void applyConfigurationChanges(String modId) {
        BaseOverlayButton overlay = modOverlayMap.get(modId);
        if (overlay != null) {
            overlay.applyConfigurationChanges();
        }

        if (modId.equals(ModIds.ZOOM) && zoomOverlay != null) {
            zoomOverlay.applyConfigurationChanges();
        }
        if (modId.equals(ModIds.SNAPLOOK) && snaplookOverlay != null) {
            snaplookOverlay.applyConfigurationChanges();
        }
        if (modId.equals(ModIds.FPS_DISPLAY) && fpsDisplayOverlay != null) {
            fpsDisplayOverlay.applyConfigurationChanges();
        }
        if (modId.equals(ModIds.CPS_DISPLAY) && cpsDisplayOverlay != null) {
            cpsDisplayOverlay.applyConfigurationChanges();
        }
    }

    public void tick() {
        for (BaseOverlayButton overlay : overlays) {
            overlay.tick();
        }
    }
}
