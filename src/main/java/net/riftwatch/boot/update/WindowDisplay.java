package net.riftwatch.boot.update;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;

final class WindowDisplay implements Display {
    @Override
    public void message(String text) {
        StartupNotificationManager.addModMessage(text);
    }

    @Override
    public Bar bar(String label, int steps) {
        ProgressMeter meter = StartupNotificationManager.prependProgressBar(label, steps);
        return new MeterBar(meter);
    }

    @Override
    public void pump() {
        Runnable tick = FMLLoader.progressWindowTick;
        if (tick != null) {
            tick.run();
        }
    }

    private record MeterBar(ProgressMeter meter) implements Bar {
        @Override
        public void label(String text) {
            meter.label(text);
        }

        @Override
        public void progress(int done) {
            meter.setAbsolute(done);
        }

        @Override
        public void complete() {
            meter.complete();
        }
    }
}
