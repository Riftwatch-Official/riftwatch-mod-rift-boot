package net.riftwatch.boot.update;

interface Display {
    void message(String text);

    Bar bar(String label, int steps);

    void pump();

    interface Bar {
        void label(String text);

        void progress(int done);

        void complete();
    }
}
