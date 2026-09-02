/*
 * Copyright (c) 2026 LabKey Corporation. All rights reserved. No portion of this work may be reproduced
 * in any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.api.util;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tar a working directory and untar it again. Equivalent to the archive handling inside DockerServiceImpl, lifted out
 * so remote script execution can reuse it over transports other than the Docker API.
 */
public class DirectoryArchive
{
    private DirectoryArchive()
    {
    }

    /** Tar {@code directory} into {@code target}. Entries are named relative to {@code parentName}. */
    public static void create(File directory, @Nullable FileFilter filter, String parentName, File target) throws IOException
    {
        try (OutputStream fos = new FileOutputStream(target);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(fos))
        {
            // Long working-directory paths are routine, and the default format silently truncates past 100 chars.
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            addDirectory(tar, directory, filter, trimTrailingSlash(parentName));
            tar.finish();
        }
    }

    private static void addDirectory(TarArchiveOutputStream tar, File dir, @Nullable FileFilter filter, String prefix) throws IOException
    {
        File[] children = dir.listFiles();
        if (children == null)
            return;

        for (File child : children)
        {
            String entryName = prefix + "/" + child.getName();
            if (child.isDirectory())
            {
                addDirectory(tar, child, filter, entryName);
                continue;
            }
            if (filter != null && !filter.accept(child))
                continue;

            TarArchiveEntry entry = new TarArchiveEntry(child, entryName);
            tar.putArchiveEntry(entry);
            try (InputStream in = new FileInputStream(child))
            {
                IOUtils.copy(in, tar);
            }
            tar.closeArchiveEntry();
        }
    }

    /**
     * Untar {@code in} beneath {@code destination}, stripping the leading path component so a tar made with
     * {@code parentName} unpacks flat into the destination.
     *
     * Entries resolving outside the destination are rejected. The tar arrives from a remote runner that executed
     * customer script code, so its entry names are untrusted input.
     */
    public static void extract(InputStream in, File destination) throws IOException
    {
        Path root = destination.toPath().toAbsolutePath().normalize();

        try (TarArchiveInputStream tar = new TarArchiveInputStream(new BufferedInputStream(in)))
        {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null)
            {
                String name = stripLeadingComponent(entry.getName());
                if (name.isEmpty())
                    continue;

                Path resolved = root.resolve(name).normalize();
                if (!resolved.startsWith(root))
                    throw new IOException("Refusing to extract entry outside the destination directory: " + entry.getName());

                File target = resolved.toFile();
                if (entry.isDirectory())
                {
                    FileUtil.mkdirs(target);
                    continue;
                }

                FileUtil.mkdirs(target.getParentFile());
                try (OutputStream out = new FileOutputStream(target))
                {
                    IOUtils.copy(tar, out);
                }
            }
        }
    }

    private static String stripLeadingComponent(String entryName)
    {
        String name = entryName.startsWith("/") ? entryName.substring(1) : entryName;
        int slash = name.indexOf('/');
        return slash < 0 ? "" : name.substring(slash + 1);
    }

    private static String trimTrailingSlash(String s)
    {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    public static class TestCase extends org.junit.Assert
    {
        @org.junit.Test
        public void roundTripPreservesContentAndBinary() throws IOException
        {
            File src = FileUtil.createTempDirectory("arch-src").toFile();
            File dest = FileUtil.createTempDirectory("arch-dest").toFile();
            try
            {
                Files.writeString(new File(src, "script.R").toPath(), "cat(\"hi\")");
                File nested = new File(src, "sub");
                FileUtil.mkdirs(nested);
                byte[] binary = new byte[]{0, 1, 2, (byte) 0xFF, 0x7F};
                Files.write(new File(nested, "plot.png").toPath(), binary);

                File tar = File.createTempFile("arch", ".tar");
                create(src, null, "/work", tar);
                try (InputStream in = new FileInputStream(tar))
                {
                    extract(in, dest);
                }

                assertEquals("cat(\"hi\")", Files.readString(new File(dest, "script.R").toPath()));
                assertArrayEquals(binary, Files.readAllBytes(new File(dest, "sub/plot.png").toPath()));
            }
            finally
            {
                FileUtil.deleteDir(src);
                FileUtil.deleteDir(dest);
            }
        }

        @org.junit.Test
        public void filterExcludesUnwantedFiles() throws IOException
        {
            File src = FileUtil.createTempDirectory("arch-filter").toFile();
            File dest = FileUtil.createTempDirectory("arch-filter-out").toFile();
            try
            {
                Files.writeString(new File(src, "keep.R").toPath(), "keep");
                Files.writeString(new File(src, "drop.tmp").toPath(), "drop");

                File tar = File.createTempFile("arch", ".tar");
                create(src, f -> f.getName().endsWith(".R"), "/work", tar);
                try (InputStream in = new FileInputStream(tar))
                {
                    extract(in, dest);
                }

                assertTrue(new File(dest, "keep.R").exists());
                assertFalse(new File(dest, "drop.tmp").exists());
            }
            finally
            {
                FileUtil.deleteDir(src);
                FileUtil.deleteDir(dest);
            }
        }

        /** The result tar comes back from a container that ran customer script code. */
        @org.junit.Test
        public void rejectsPathTraversal() throws IOException
        {
            File dest = FileUtil.createTempDirectory("arch-traversal").toFile();
            File tar = File.createTempFile("evil", ".tar");
            try
            {
                try (OutputStream fos = new FileOutputStream(tar);
                     TarArchiveOutputStream out = new TarArchiveOutputStream(fos))
                {
                    TarArchiveEntry entry = new TarArchiveEntry("work/../../escaped.txt");
                    byte[] payload = "pwned".getBytes();
                    entry.setSize(payload.length);
                    out.putArchiveEntry(entry);
                    out.write(payload);
                    out.closeArchiveEntry();
                    out.finish();
                }

                try (InputStream in = new FileInputStream(tar))
                {
                    extract(in, dest);
                    fail("Expected traversal entry to be rejected");
                }
                catch (IOException expected)
                {
                    assertTrue(expected.getMessage().contains("outside the destination"));
                }
            }
            finally
            {
                FileUtil.deleteDir(dest);
            }
        }
    }
}
