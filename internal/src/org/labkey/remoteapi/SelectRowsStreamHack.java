package org.labkey.remoteapi;

import org.apache.tika.io.IOUtils;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.WrapperDataIterator;
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
