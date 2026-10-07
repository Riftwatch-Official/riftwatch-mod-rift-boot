package net.riftwatch.boot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.neoforged.fml.earlydisplay.RenderElement;

final class SceneElements extends ArrayList<RenderElement> {
    private static final long serialVersionUID = 1L;

    private final transient RiftwatchWindow window;
    private final transient List<RenderElement> original;
    private final transient BootScene scene;
    private boolean failed;

    SceneElements(RiftwatchWindow window, List<RenderElement> original, BootScene scene) {
        this.window = window;
        this.original = original;
        this.scene = scene;
    }

    @Override
    public boolean add(RenderElement element) {
        return original.add(element);
    }

    @Override
    public void add(int index, RenderElement element) {
        original.add(index, element);
    }

    @Override
    public boolean removeIf(Predicate<? super RenderElement> filter) {
        if (!failed) {
            try {
                window.fitFramebuffer();
                scene.paint();
                return false;
            } catch (Throwable t) {
                failed = true;
                RiftwatchWindow.LOGGER.error("Riftwatch boot screen failed, NeoForge's screen takes over", t);
                window.restoreFramebuffer();
            }
        }
        return original.removeIf(filter);
    }
}
