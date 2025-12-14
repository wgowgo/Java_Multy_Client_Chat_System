import java.util.*;

public final class Kvp {
    private Kvp() {}

   
    private static String enc(String s) {
        if (s == null) return "";
        return s.replace("%", "%25").replace("\n", "%0A").replace("=", "%3D");
    }

    private static String dec(String s) {
        if (s == null) return "";
     
        return s.replace("%3D", "=").replace("%0A", "\n").replace("%25", "%");
    }

    public static String encode(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        for (var e : map.entrySet()) {
            sb.append(enc(e.getKey())).append("=").append(enc(e.getValue())).append("\n");
        }
        return sb.toString();
    }

    public static Map<String, String> decode(String text) {
        Map<String, String> map = new HashMap<>();
        if (text == null || text.isEmpty()) return map;

        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.isEmpty()) continue;
            int idx = line.indexOf('=');
            if (idx <= 0) continue;
            String k = dec(line.substring(0, idx));
            String v = dec(line.substring(idx + 1));
            map.put(k, v);
        }
        return map;
    }

    public static Map<String, String> kv(String... pairs) {
       
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
        return m;
    }
}
