package io.github.gasmorais.pdfviewer.file;

import android.content.ContentResolver;
import android.net.Uri;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class TempFile {

    private static final String PREFIX = "document";
    private static final String EXTENSION = ".pdf";

    private TempFile() {
    }

    public static File copy(ContentResolver resolver, Uri source, File directory) throws IOException {
        prepareDirectory(directory);
        File target = File.createTempFile(PREFIX, EXTENSION, directory);
        try (InputStream input = resolver.openInputStream(source)) {
            if (input == null) {
                throw new FileNotFoundException(source.toString());
            }
            Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException | RuntimeException e) {
            target.delete();
            throw e;
        }
    }

    public static void clear(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            file.delete();
        }
    }

    private static void prepareDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException(directory.toString());
        }
        clear(directory);
    }
}
