package top.niunaijun.blackbox.core.system.pm.split;

import java.io.File;
import java.net.URISyntaxException;

final class Fixtures {
    private Fixtures() {
    }

    static File apk(String name) {
        try {
            java.net.URL url = Fixtures.class.getClassLoader().getResource("apks/" + name);
            if (url == null) throw new IllegalStateException("Falta el fixture " + name);
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    static SplitInfo info(String name) throws Exception {
        return SplitManifestReader.read(apk(name));
    }
}
