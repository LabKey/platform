/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.api.thumbnail;

import java.io.InputStream;

/**
 * User: adam
 * Date: 10/8/11
 * Time: 7:01 AM
 */
public class Thumbnail
{
    private final InputStream _is;
    private final String _contentType;

    public Thumbnail(InputStream is, String contentType)
    {
        assert null != is : "InputStream should not be null!";
        _is = is;
        _contentType = contentType;
    }

    public InputStream getInputStream()
    {
        return _is;
    }

    public String getContentType()
    {
        return _contentType;
    }
}
