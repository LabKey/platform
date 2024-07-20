package org.labkey.api.resource;

import org.apache.commons.io.IOUtils;
import org.labkey.api.util.Path;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
        return IOUtils.toInputStream(_contents, StandardCharsets.UTF_8);
    }
}
