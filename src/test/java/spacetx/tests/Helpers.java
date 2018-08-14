package spacetx.tests;


import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Various static methods for use in tests.
 */
public class Helpers {

    public static Path fake(String...args) {
        Assertions.assertTrue(args.length %2 == 0);
        Map<String, String> options = new HashMap<String, String>();
        for (int i = 0; i < args.length; i += 2) {
            options.put(args[i], args[i+1]);
        }
        return fake(options);
    }

    public static Path fake(Map<String, String> options) {
        StringBuilder sb = new StringBuilder();
        sb.append("image");
        if (options != null) {
            for (Map.Entry<String, String> kv : options.entrySet()) {
                sb.append("&");
                sb.append(kv.getKey());
                sb.append("=");
                sb.append(kv.getValue());
            }
        }
        sb.append("&");
        try {
            Path fake = Files.createTempFile(sb.toString(), ".fake");
            Files.write(fake, new byte[]{});
            fake.toFile().deleteOnExit();
            return fake;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Return the number of files in a directory matching '*glob'.
     *
     * @param glob Suffix to match without the starting '*'.
     * @param location Most likely the temporary directory created on setup.
     * @return The number of files that match.
     */
    public static int matches(String glob, Path location) {

        final AtomicInteger matches = new AtomicInteger(0);

        final PathMatcher pathMatcher = location.getFileSystem().getPathMatcher("glob:*" + glob);

        try {
            Files.walkFileTree(location, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path path,
                                                 BasicFileAttributes attrs) throws IOException {
                    if (pathMatcher.matches(path.getFileName())) {
                        matches.incrementAndGet();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return -1;
        }

        return matches.get();
    }

}
