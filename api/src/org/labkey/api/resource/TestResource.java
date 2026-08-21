/*
 * Copyright (c) 2024-2026 LabKey Corporation
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

    @Override
    public String toString()
    {
        return getPath().toString();
    }
}
