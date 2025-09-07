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
import org.labkey.api.data.Container;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.JSONDataLoader;
import org.labkey.api.util.FileUtil;
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

    public static DataIteratorBuilder go(Connection cn, String container, SelectRowsCommand cmd, Container targetContainer) throws IOException, CommandException
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

                // NOTE: this is just a placeholder for a configurable parameter. Or maybe it should just be the default?
                boolean saveResponseToTempFile = true;

                try
                {
                    final InputStream is;
                    if (saveResponseToTempFile)
                    {
                        final File tempFile = FileUtil.createTempFile("SelectRows-", ".tmp.gz");
                        _log.debug("Downloading SelectRows JSON to file: " + tempFile);

                        try (OutputStream os = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tempFile))); InputStream ris = new BufferedInputStream(response.getInputStream()))
                        {
                            IOUtils.copy(ris, os);
                        }

                        is = new BufferedInputStream(new GZIPInputStream(new FileInputStream(tempFile)))
                        {
                            @Override
                            public void close() throws IOException
                            {
                                super.close();

                                if (tempFile.exists())
                                {
                                    _log.debug("Deleting temporary file used to download SelectRows results: " + tempFile);
                                    if (!tempFile.delete())
                                    {
                                        _log.warn("Unable to delete SelectRowsStreamHack temp file: " + tempFile);
                                    }
                                }
                            }
                        };
                        _log.debug("Finished saving SelectRows results");
                    }
                    else
                    {
                        is = response.getInputStream();
                    }

                    final JSONDataLoader loader = new JSONDataLoader(is, targetContainer);
                    WrapperDataIterator wrapper = new WrapperDataIterator(loader.getDataIterator(context))
                    {
                        @Override
                        public void close() throws IOException
                        {
                            // close the InputStream and http connection
                            if (is != null)
                                IOUtils.closeQuietly(is);
                            response.close();

                            // close the JSONDataLoader
                            super.close();
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
        };
    }
}
