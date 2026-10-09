/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.module;

import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.bootstrap.Log4JLogger;
import org.labkey.bootstrap.ModuleArchive;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

/** GH Issue 1693: re-extracting a .module must never leave stale jars behind */
public class ModuleArchiveTestCase extends Assert
{
    private static final Logger LOG = LogHelper.getLogger(ModuleArchiveTestCase.class, "Module archive extraction tests");
    // .module archives stamp every entry with this fixed time
    private static final long ENTRY_TIME = new GregorianCalendar(1980, Calendar.FEBRUARY, 1).getTimeInMillis();

    private Path _dir;

    @Before
    public void setUp() throws IOException
    {
        _dir = Files.createTempDirectory("moduleArchiveTest");
    }

    @After
    public void tearDown() throws IOException
    {
        FileUtil.deleteDir(_dir);
    }

    @Test
    public void testSameSizeJarIsReplaced() throws IOException
    {
        long now = System.currentTimeMillis();
        ModuleArchive v1 = writeArchive(now - 60_000, Map.of("dep.jar", "1"));
        File exploded = v1.extractAll();
        File dep = new File(exploded, "lib/dep.jar");
        assertEquals("1", readVersion(dep));

        ModuleArchive v2 = writeArchive(now, Map.of("dep.jar", "2"));
        assertTrue(v2.isModified());
        v2.extractAll();
        assertEquals("2", readVersion(dep));
        assertFalse(v2.isModified());
    }

    @Test
    public void testHeldJarsAreReplacedOrReported() throws IOException
    {
        long now = System.currentTimeMillis();
        ModuleArchive v1 = writeArchive(now - 60_000, Map.of("dep.jar", "1", "old-1.jar", "1"));
        File exploded = v1.extractAll();
        File dep = new File(exploded, "lib/dep.jar");
        File old = new File(exploded, "lib/old-1.jar");

        ModuleArchive v2 = writeArchive(now, Map.of("dep.jar", "2", "old-2.jar", "2"));
        try (JarFile heldDep = new JarFile(dep); JarFile heldOld = new JarFile(old))
        {
            heldDep.getEntry("version.txt");
            heldOld.getEntry("version.txt");

            try
            {
                v2.extractAll();
            }
            catch (ModuleArchive.StaleFilesException e)
            {
                // Windows can't delete or replace open files; both must be named and the extraction left for a retry
                assertTrue(e.getMessage(), e.getMessage().contains(dep.getPath()));
                assertTrue(e.getMessage(), e.getMessage().contains(old.getPath()));
                assertTrue(v2.isModified());
            }
        }

        if (v2.isModified())
            v2.extractAll();

        assertEquals("2", readVersion(dep));
        assertFalse(old.exists());
        assertEquals("2", readVersion(new File(exploded, "lib/old-2.jar")));
        assertFalse(v2.isModified());
    }

    private ModuleArchive writeArchive(long lastModified, Map<String, String> jarVersions) throws IOException
    {
        File archive = _dir.resolve("testmodule" + ModuleArchive.FILE_EXTENSION).toFile();
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(archive)))
        {
            for (Map.Entry<String, String> jarVersion : jarVersions.entrySet())
            {
                JarEntry entry = new JarEntry("lib/" + jarVersion.getKey());
                entry.setTime(ENTRY_TIME);
                out.putNextEntry(entry);
                out.write(innerJar(jarVersion.getValue()));
                out.closeEntry();
            }
        }
        assertTrue(archive.setLastModified(lastModified));
        return new ModuleArchive(archive, new Log4JLogger(LOG));
    }

    private static byte[] innerJar(String version) throws IOException
    {
        byte[] content = version.getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(content);

        // Stored, so versions of equal length produce jars of equal size
        JarEntry entry = new JarEntry("version.txt");
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(content.length);
        entry.setCrc(crc.getValue());
        entry.setTime(ENTRY_TIME);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream out = new JarOutputStream(bytes))
        {
            out.putNextEntry(entry);
            out.write(content);
            out.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static String readVersion(File jar) throws IOException
    {
        try (JarFile jarFile = new JarFile(jar); InputStream in = jarFile.getInputStream(jarFile.getEntry("version.txt")))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
