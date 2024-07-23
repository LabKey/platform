package org.labkey.api.resource;

import org.apache.commons.io.IOUtils;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;

import java.io.IOException;
import java.io.InputStream;

// Minimal Resource for testing purposes
public class TestResource extends AbstractResource
{
    private final String _contents;

    public TestResource(String name, String contents)
    {
        super(new Path(name), null);
        _contents = contents;
    }

    @Override
    public boolean exists()
    {
        return true;
    }

    @Override
    public Resource parent()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getInputStream() throws IOException
    {
        return IOUtils.toInputStream(_contents, StringUtilsLabKey.DEFAULT_CHARSET);
    }
}
