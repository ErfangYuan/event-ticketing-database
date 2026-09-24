package mytix.ui;

import mytix.util.ConsoleIO;

public interface MenuAction {
    String label();

    void run(ConsoleIO io);
}
