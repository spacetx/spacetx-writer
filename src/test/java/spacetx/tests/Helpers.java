package spacetx.tests;


import org.junit.jupiter.api.Assertions;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        return fake(options, null);
    }

    public static Path fake(Map<String, String> options, Map<Integer, Map<String, String>> series) {
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
            List<String> lines = new ArrayList<String>();
            if (series != null) {
                for (int s : series.keySet()) {
                    Map<String, String> seriesOptions = series.get(s);
                    lines.add(String.format("[series_%d]", s));
                    for (String key : seriesOptions.keySet()) {
                        lines.add(String.format("%s=%s", key, seriesOptions.get(key)));
                    }
                }
            }
            Path ini = Files.createTempFile(sb.toString(), ".fake.ini");
            File iniAsFile = ini.toFile();
            String iniPath = iniAsFile.getAbsolutePath();
            String fakePath = iniPath.substring(0, iniPath.length() - 4);
            Path fake = Paths.get(fakePath);
            File fakeAsFile = fake.toFile();
            Files.write(fake, new byte[]{});
            Files.write(ini, lines);
            iniAsFile.deleteOnExit();
            fakeAsFile.deleteOnExit();
            return ini;
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
        return find(glob, location).size();
    }

    /**
     * Return the files in a directory matching '*glob'.
     *
     * @param glob Suffix to match without the starting '*'.
     * @param location Most likely the temporary directory created on setup.
     * @return Paths for matching files.
     */
    public static List<Path> find(String glob, Path location) {

        final List<Path> found = new ArrayList<Path>();

        final PathMatcher pathMatcher = location.getFileSystem().getPathMatcher("glob:*" + glob);

        try {
            Files.walkFileTree(location, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path path,
                                                 BasicFileAttributes attrs) throws IOException {
                    if (pathMatcher.matches(path.getFileName())) {
                        found.add(path);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // pass
        }

        return found;
    }

    public static int grep(String glob, String text, Path dir) throws IOException {
        List<Path> paths = find(glob, dir);
        Assertions.assertEquals(1, paths.size(), paths.toString());
        Path path = paths.get(0);
        int found = 0;
        for (String line : Files.readAllLines(path)) {
            if (line.contains(text)) {
                found++;
            }
        }
        return found;
    }

}
