package org.apache.zeppelin.service.bde.hera;

import org.apache.zeppelin.conf.ZeppelinConfiguration;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DirectoryCopy {

    ZeppelinConfiguration zeppelinConf = ZeppelinConfiguration.create();

    private static final Pattern REMOVE_PATTERN = Pattern.compile("parallel_([A-Za-z0-9]+)_index:(\\d+)_ref:([A-Za-z0-9]+)");

    public void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
        removeMatchingFiles(target);
    }
    private static void removeMatchingFiles(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();
                Matcher matcher = REMOVE_PATTERN.matcher(fileName);
                if (matcher.find()) {
                    Files.deleteIfExists(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
    public void backupNotebook() throws IOException {
        String localPath = zeppelinConf.getNotebookDir();
        Date now = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        String formattedDate = formatter.format(now);

        Path sourceDir = Paths.get(localPath);
        Path targetDir = Paths.get(localPath + "_" + formattedDate);
        copyDirectory(sourceDir, targetDir);
    }

}

