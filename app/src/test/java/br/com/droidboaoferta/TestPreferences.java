package br.com.droidboaoferta;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

final class TestPreferences {
    static SharedPreferences create() {
        Map<String, Object> values = new HashMap<>();
        return (SharedPreferences) Proxy.newProxyInstance(TestPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                    synchronized (values) {
                        String name = method.getName();
                        if (name.equals("getAll")) return new HashMap<>(values);
                        if (name.equals("contains")) return values.containsKey(args[0]);
                        if (name.startsWith("get")) return values.getOrDefault(args[0], args[1]);
                        if (name.equals("edit")) {
                            Map<String, Object> pending = new HashMap<>();
                            boolean[] clear = {false};
                            return Proxy.newProxyInstance(TestPreferences.class.getClassLoader(),
                                    new Class<?>[]{SharedPreferences.Editor.class}, (editor, operation, parameters) -> {
                                        String action = operation.getName();
                                        if (action.startsWith("put")) pending.put((String) parameters[0], parameters[1]);
                                        else if (action.equals("remove")) pending.put((String) parameters[0], null);
                                        else if (action.equals("clear")) clear[0] = true;
                                        else if (action.equals("apply") || action.equals("commit")) {
                                            synchronized (values) {
                                                if (clear[0]) values.clear();
                                                pending.forEach((key, value) -> {
                                                    if (value == null) values.remove(key); else values.put(key, value);
                                                });
                                            }
                                            return action.equals("commit") ? true : null;
                                        }
                                        return editor;
                                    });
                        }
                        return null;
                    }
                });
    }
}
