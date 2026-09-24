package mytix.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import mytix.util.ConsoleIO;

public abstract class AbstractMenu {

    protected final ConsoleIO io;
    private final String title;
    private final String backKey;
    private final String backLabel;

    protected AbstractMenu(ConsoleIO io, String title, String backKey, String backLabel) {
        this.io = io;
        this.title = title;
        this.backKey = backKey;
        this.backLabel = backLabel;
    }

    protected abstract Map<String, MenuAction> actions();

    public void loop() {
        while (true) {
            System.out.println();
            System.out.println("==== " + title + " ====");
            Map<String, MenuAction> map = actions();
            for (Map.Entry<String, MenuAction> e : map.entrySet()) {
                System.out.println("  " + e.getKey() + ". " + e.getValue().label());
            }
            System.out.println("  " + backKey + ". " + backLabel);
            String choice = io.prompt("Select: ");
            if (io.reachedEof() || choice.equalsIgnoreCase(backKey)) {
                return;
            }
            MenuAction action = map.get(choice);
            if (action == null) {
                System.out.println("Invalid option.");
                continue;
            }
            action.run(io);
            if (io.reachedEof()) {
                return;
            }
        }
    }

    protected static Map<String, MenuAction> mapOf(MenuAction... actions) {
        Map<String, MenuAction> m = new LinkedHashMap<>();
        int i = 1;
        for (MenuAction a : actions) {
            m.put(String.valueOf(i++), a);
            
        }
        return m;
    }
}
