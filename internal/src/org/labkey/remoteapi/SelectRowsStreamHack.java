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
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.JSONDataLoader;
import org.labkey.remoteapi.query.SelectRowsCommand;

import java.io.IOException;
import java.io.InputStream;

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
    public static DataIteratorBuilder go(Connection cn, String container, SelectRowsCommand cmd) throws IOException, CommandException
    {
        final Command.Response response = cmd._execute(cn, container);
        return new DataIteratorBuilder()
        {
            @Override
            public DataIterator getDataIterator(DataIteratorContext context)
            {
                try
                {
                    final InputStream is = response.getInputStream();
                    final JSONDataLoader loader = new JSONDataLoader(is, false, null);
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
