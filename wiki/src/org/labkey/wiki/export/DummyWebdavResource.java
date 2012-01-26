/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.wiki.export;

import org.labkey.api.security.User;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.Path;
import org.labkey.api.webdav.AbstractWebdavResource;

import java.io.IOException;
import java.io.InputStream;

/**
 * User: jeckels
 * Date: Jan 19, 2012
 */
public class DummyWebdavResource extends AbstractWebdavResource
{
    public DummyWebdavResource()
    {
        super(new Path("fakePath"));
    }

    @Override
    public InputStream getInputStream(User user) throws IOException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public long copyFrom(User user, FileStream in) throws IOException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getContentLength() throws IOException
    {
        throw new UnsupportedOperationException();
    }
}
