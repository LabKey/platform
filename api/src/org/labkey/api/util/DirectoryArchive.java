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
import java.nio.file.FileSystemException;
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

    /** Tar {@code directory} into {@code target}, with entries named relative to it. */
    public static void create(File directory, @Nullable FileFilter filter, File target) throws IOException
    {
        try (OutputStream fos = new FileOutputStream(target);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(fos))
        {
            // Long working-directory paths are routine, and the default format silently truncates past 100 chars.
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            addDirectory(tar, directory, filter, "");
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
            // Top level is unprefixed; joining unconditionally would name it "/script.r", which extract rejects as an
            // absolute path. The prefix only accumulates as recursion descends into subdirectories.
            String entryName = prefix.isEmpty() ? child.getName() : prefix + "/" + child.getName();
            boolean link = Files.isSymbolicLink(child.toPath());

            if (child.isDirectory())
            {
                // File.isDirectory() follows the link, so recursing here would walk out of the working directory --
                // and a link to any ancestor would recurse until the stack or the disk gave out.
                if (link)
                    continue;
                addDirectory(tar, child, filter, entryName);
                continue;
            }
            // A link to a file is still dereferenced below, which preserves the content the script expects. A broken
            // one resolves to nothing, and opening it would fail the whole job.
            if (link && !child.isFile())
                continue;
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
     * The script author decides how much the run writes, so the archive is bounded here rather than trusted. Without
     * a cap one report can fill the appserver's disk, which takes down every application on the host. Override for a
     * deployment that legitimately produces more.
     */
    public static final long MAX_EXTRACTED_BYTES = Long.getLong("labkey.directoryArchive.maxBytes", 1L << 30);
    public static final int MAX_EXTRACTED_ENTRIES = Integer.getInteger("labkey.directoryArchive.maxEntries", 10_000);

    /**
     * Untar {@code in} beneath {@code destination}, reproducing the archived directory.
     *
     * Entries resolving outside the destination are rejected, absolute names and links included, and the total is
     * capped. The tar arrives from a remote runner that executed customer script code, so both its entry names and
     * its size are untrusted input.
     */
    public static void extract(InputStream in, File destination) throws IOException
    {
        Path root = destination.toPath().toAbsolutePath().normalize();
        long budget = MAX_EXTRACTED_BYTES;
        int entries = 0;

        try (TarArchiveInputStream tar = new TarArchiveInputStream(new BufferedInputStream(in)))
        {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null)
            {
                String name = entry.getName();
                if (name.isEmpty())
                    continue;

                if (++entries > MAX_EXTRACTED_ENTRIES)
                    throw new IOException("Refusing to extract more than " + MAX_EXTRACTED_ENTRIES + " entries");

                // A link would let a later entry write through it to anywhere the appserver can reach.
                if (entry.isSymbolicLink() || entry.isLink())
                    throw new IOException("Refusing to extract link entry: " + name);

                Path resolved = root.resolve(name).normalize();
                if (!resolved.startsWith(root))
                    throw new IOException("Refusing to extract entry outside the destination directory: " + name);

                File target = resolved.toFile();
                if (entry.isDirectory())
                {
                    FileUtil.mkdirs(target);
                    continue;
                }

                FileUtil.mkdirs(target.getParentFile());
                // normalize() above is lexical, so it cannot see a symlink already sitting in the destination.
                // Re-checking the created parent's real path closes that, and costs one stat per entry.
                requireInside(root, target.getParentFile().toPath());

                try (OutputStream out = new FileOutputStream(target))
                {
                    budget -= copyBounded(tar, out, budget);
                }
            }
        }
    }

    private static void requireInside(Path root, Path parent) throws IOException
    {
        Path real = parent.toRealPath();
        if (!real.startsWith(root.toRealPath()))
            throw new IOException("Refusing to write through a link out of the destination directory: " + parent);
    }

    /** Copies at most {@code budget} bytes, failing rather than truncating so a clipped result is never mistaken for a whole one. */
    private static long copyBounded(InputStream in, OutputStream out, long budget) throws IOException
    {
        byte[] buffer = new byte[8192];
        long written = 0;
        int read;
        while ((read = in.read(buffer)) != -1)
        {
            written += read;
            if (written > budget)
                throw new IOException("Refusing to extract more than " + MAX_EXTRACTED_BYTES + " bytes");
            out.write(buffer, 0, read);
        }
        return written;
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
                create(src, null, tar);
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
                create(src, f -> f.getName().endsWith(".R"), tar);
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

        /** The runner returns a flat tar. Skipping those entries loses every result without reporting anything. */
        @org.junit.Test
        public void extractsFlatEntriesFromAForeignTar() throws IOException
        {
            File dest = FileUtil.createTempDirectory("arch-flat").toFile();
            File tar = File.createTempFile("flat", ".tar");
            try
            {
                writeSingleEntryTar(tar, "script.r.Rout", "console output");

                try (InputStream in = new FileInputStream(tar))
                {
                    extract(in, dest);
                }

                assertEquals("console output", Files.readString(new File(dest, "script.r.Rout").toPath()));
            }
            finally
            {
                FileUtil.deleteDir(dest);
            }
        }

        /** An absolute entry would otherwise escape the destination entirely. */
        @org.junit.Test
        public void rejectsAbsoluteEntry() throws IOException
        {
            File dest = FileUtil.createTempDirectory("arch-absolute").toFile();
            File tar = File.createTempFile("absolute", ".tar");
            try
            {
                writeSingleEntryTar(tar, "/etc/passwd", "pwned");

                try (InputStream in = new FileInputStream(tar))
                {
                    extract(in, dest);
                    fail("Expected absolute entry to be rejected");
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

        private static void writeSingleEntryTar(File tar, String entryName, String content) throws IOException
        {
            try (OutputStream fos = new FileOutputStream(tar);
                 TarArchiveOutputStream out = new TarArchiveOutputStream(fos))
            {
                out.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                // preserveAbsolutePath, or the constructor strips the leading slash and the absolute case cannot be
                // written at all. Python's tarfile preserves it, so the runner can produce one.
                TarArchiveEntry entry = new TarArchiveEntry(entryName, true);
                byte[] payload = content.getBytes();
                entry.setSize(payload.length);
                out.putArchiveEntry(entry);
                out.write(payload);
                out.closeArchiveEntry();
                out.finish();
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

        /** A link entry would let a later entry write through it to anywhere the appserver can reach. */
        @org.junit.Test
        public void rejectsLinkEntry() throws IOException
        {
            File dest = FileUtil.createTempDirectory("arch-link").toFile();
            File tar = File.createTempFile("link", ".tar");
            try
            {
                try (OutputStream fos = new FileOutputStream(tar);
                     TarArchiveOutputStream out = new TarArchiveOutputStream(fos))
                {
                    TarArchiveEntry entry = new TarArchiveEntry("escape", TarArchiveEntry.LF_SYMLINK);
                    entry.setLinkName("/etc");
                    out.putArchiveEntry(entry);
                    out.closeArchiveEntry();
                    out.finish();
                }

                try (InputStream in = new FileInputStream(tar))
                {
                    extract(in, dest);
                    fail("Expected a symlink entry to be rejected");
                }
                catch (IOException expected)
                {
                    assertTrue(expected.getMessage().contains("link entry"));
                }
            }
            finally
            {
                FileUtil.deleteDir(dest);
            }
        }

        /**
         * The script author decides how much the run writes, so an unbounded extract lets one report fill the disk
         * and take down every application on the host.
         */
        @org.junit.Test
        public void rejectsContentOverTheByteCap() throws IOException
        {
            // MAX_EXTRACTED_BYTES is read into a static at class load, so the budget is exercised directly rather
            // than by building a gigabyte of tar.
            try (InputStream in = new java.io.ByteArrayInputStream("x".repeat(4096).getBytes());
                 OutputStream sink = OutputStream.nullOutputStream())
            {
                copyBounded(in, sink, 100);
                fail("Expected the byte cap to be enforced");
            }
            catch (IOException expected)
            {
                assertTrue(expected.getMessage().contains("Refusing to extract more than"));
            }
        }

        /**
         * File.isDirectory() follows a link, so without the guard the cyclic link below recurses until the stack or
         * the disk gives out. A link to a file still round-trips as that file's content.
         */
        @org.junit.Test
        public void doesNotFollowSymlinksOutOfTheTree() throws IOException
        {
            File src = FileUtil.createTempDirectory("arch-symlink-src").toFile();
            File dest = FileUtil.createTempDirectory("arch-symlink-dest").toFile();
            File tar = File.createTempFile("symlink", ".tar");
            try
            {
                Files.writeString(new File(src, "real.txt").toPath(), "content");
                Files.createSymbolicLink(new File(src, "link.txt").toPath(), new File(src, "real.txt").toPath());
                Files.createSymbolicLink(new File(src, "loop").toPath(), src.toPath());
                Files.createSymbolicLink(new File(src, "broken").toPath(), new File(src, "gone").toPath());

                create(src, null, tar);

                try (InputStream in = new FileInputStream(tar))
                {
                    extract(in, dest);
                }

                assertEquals("content", Files.readString(new File(dest, "real.txt").toPath()));
                assertEquals("a link to a file should arrive as that file's content",
                        "content", Files.readString(new File(dest, "link.txt").toPath()));
                assertFalse("a link to a directory must not be followed", new File(dest, "loop").exists());
                assertFalse("a broken link must not fail the archive", new File(dest, "broken").exists());
            }
            catch (UnsupportedOperationException | FileSystemException e)
            {
                // Creating symlinks needs a privilege this filesystem does not grant; nothing to assert.
            }
            finally
            {
                FileUtil.deleteDir(src);
                FileUtil.deleteDir(dest);
                tar.delete();
            }
        }

        @org.junit.Test
        public void copiesUpToTheBudget() throws IOException
        {
            byte[] payload = "hello".getBytes();
            try (InputStream in = new java.io.ByteArrayInputStream(payload);
                 OutputStream sink = OutputStream.nullOutputStream())
            {
                assertEquals(payload.length, copyBounded(in, sink, payload.length));
            }
        }
    }
}
