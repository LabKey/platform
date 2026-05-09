/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.remoteapi;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.JSONDataLoader;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.remoteapi.query.SelectRowsCommand;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * NOTE: This class only exists to use internal classes of the remoteapi package that we haven't exposed to the public yet.
 *
 * SelectRowsStreamHack bridges the remoteapi SelectRowsCommand with DataIterator and will close the underlying HttpClient's
 * connection when the DataIterator has been closed.
 *
 * User: kevink
 * Date: 10/21/13
 */
public class SelectRowsStreamHack
{
    private static final Logger _log = LogHelper.getLogger(SelectRowsStreamHack.class, "Information related to streaming of SelectRows results");

    private static final String TEMP_FILE_PREFIX = "SelectRows-";
    private static final String TEMP_FILE_SUFFIX = ".remote.tmp.gz";

    public static DataIteratorBuilder go(Connection cn, String container, SelectRowsCommand cmd, Container targetContainer) throws IOException, CommandException
    {
        return  go(cn, container, cmd, targetContainer, null);
    }

    // This private method and alternateInputStream are only intended for testing purposes:
    private static DataIteratorBuilder go(Connection cn, String container, SelectRowsCommand cmd, Container targetContainer, @Nullable InputStream alternateInputStream) throws IOException, CommandException
    {
        return new DataIteratorBuilder()
        {
            @Override
            public DataIterator getDataIterator(DataIteratorContext context)
            {
                Command.Response response;
                try
                {
                    // Execute the request when we're creating the DataIterator so that it can be reliably closed.
                    // When we did it early as part of creating the DataIteratorBuilder, it could lead to a HTTP
                    // connection leak. See issue 44390
                    response = cmd._execute(cn, container);
                }
                catch (CommandException | IOException e)
                {
                    throw new RuntimeException("Failed to execute remote query", e);
                }

                try
                {
                    final File tempFile = FileUtil.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
                    tempFile.deleteOnExit();

                    try (OutputStream os = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tempFile))); InputStream ris = (alternateInputStream == null ? response.getInputStream() : alternateInputStream))
                    {
                        _log.debug("Downloading SelectRows JSON to file: {}", tempFile);
                        IOUtils.copy(ris, os);
                        _log.debug("Finished saving SelectRows results");
                    }
                    catch (Exception e)
                    {
                        _log.error("Error loading SelectRows results", e);
                        ensureTempFileDeleted(tempFile);

                        throw e;
                    }

                    final InputStream is = getFileDeletingInputStream(tempFile);

                    final JSONDataLoader loader = new JSONDataLoader(is, targetContainer);
                    WrapperDataIterator wrapper = new WrapperDataIterator(loader.getDataIterator(context))
                    {
                        @Override
                        public void close() throws IOException
                        {
                            // close the InputStream and http connection
                            IOUtils.closeQuietly(is);
                            response.close();

                            // close the JSONDataLoader
                            super.close();

                            ensureTempFileDeleted(tempFile);
                        }
                    };
                    wrapper.setDebugName("SelectRows:JSONDataLoader");
                    return wrapper;
                }
                catch (IOException e)
                {
                    // NOTE: jackson throws JsonParseExceptions which extend IOException
                    context.getErrors().addRowError(new ValidationException("Error: " + e.getMessage()));
                    return null;
                }
            }

            private static InputStream getFileDeletingInputStream(final File tempFile) throws IOException
            {
                return new BufferedInputStream(new GZIPInputStream(new FileInputStream(tempFile)))
                {
                    @Override
                    public void close() throws IOException
                    {
                        super.close();

                        ensureTempFileDeleted(tempFile);
                    }
                };
            }

            private static void ensureTempFileDeleted(@Nullable File tempFile)
            {
                if (tempFile == null)
                {
                    return;
                }

                if (tempFile.exists())
                {
                    _log.debug("Deleting temporary file used to download SelectRows results: {}", tempFile);
                    if (!tempFile.delete())
                    {
                        _log.error("Unable to delete SelectRowsStreamHack temp file: {}", tempFile);
                    }
                }
            }
        };
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testFileDeletion() throws Exception
        {
            try (var session = SecurityManager.createTransformSession(TestContext.get().getRequest(), TestContext.get().getRequest().getSession()))
            {
                String baseURL = AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath();
                Connection cn = new Connection(baseURL, new ApiKeyCredentialsProvider(session.getApiKey()));

                SelectRowsCommand cmd = new SelectRowsCommand("core", "containers");
                Container targetContainer = ContainerManager.getHomeContainer();
                final long expectedRows = cmd.execute(cn, ContainerManager.getHomeContainer().getPath()).getRowCount().longValue();

                Set<String> preexistingTempFiles = getMatchingTempFileNames();

                DataIteratorBuilder dib = SelectRowsStreamHack.go(cn, ContainerManager.getHomeContainer().getPath(), cmd, targetContainer);
                DataIteratorContext dic = new DataIteratorContext();

                DataIterator di = dib.getDataIterator(dic);
                
                // This should iterate and close the stream:
                long actualCount = di.stream().count();
                assertEquals("Incorrect row count", expectedRows, actualCount);

                // The file should be deleted now:
                Set<String> actualTempFiles = getMatchingTempFileNames();
                actualTempFiles.removeAll(preexistingTempFiles);
                assertEquals("Temp files were not deleted, found: " + actualTempFiles.size(), 0, actualTempFiles.size());

                // Now try this using an InputStream that will fail:
                preexistingTempFiles = getMatchingTempFileNames();

                SelfDestructiveInputStream sdic = new SelfDestructiveInputStream(100, 10);
                try
                {
                    dib = SelectRowsStreamHack.go(cn, ContainerManager.getHomeContainer().getPath(), cmd, targetContainer, sdic);
                    @SuppressWarnings("unused") List<Map<String, Object>> results = dib.getDataIterator(dic).stream().toList();
                    fail("SelfDestructiveInputStream did not throw an exception!");
                }
                catch (Exception ignored) {}

                assertTrue("SelfDestructiveInputStream was not closed!", sdic.isCloseCalled());

                actualTempFiles = getMatchingTempFileNames();
                actualTempFiles.removeAll(preexistingTempFiles);
                assertEquals("Temp files were not deleted, found: " + actualTempFiles.size(), 0, actualTempFiles.size());
            }
        }

        private Set<String> getMatchingTempFileNames()
        {
            return Arrays.stream(Objects.requireNonNull(new File(System.getProperty("java.io.tmpdir")).
                    list((dir, name) -> name.startsWith(TEMP_FILE_PREFIX) &&
                            name.endsWith(TEMP_FILE_SUFFIX)))).collect(Collectors.toSet());
        }

        private static class SelfDestructiveInputStream extends InputStream
        {
            private final long _maxReads;
            private final long _delayMs;

            private long _count = 0;
            private boolean _closeCalled = false;

            public SelfDestructiveInputStream(long maxReads, long delayMs)
            {
                _maxReads = maxReads;
                _delayMs = delayMs;
            }

            @Override
            public int read() throws IOException
            {
                try
                {
                    Thread.sleep(_delayMs);
                }
                catch (InterruptedException e)
                {
                    throw new IOException(e);
                }

                _count++;
                if (_count >= _maxReads)
                {
                    throw new IOException("I'm throwing an IOException!");
                }

                return 0;
            }

            @Override
            public void close() throws IOException
            {
                _closeCalled = true;
                super.close();
            }

            public boolean isCloseCalled()
            {
                return _closeCalled;
            }
        }
    }
}
