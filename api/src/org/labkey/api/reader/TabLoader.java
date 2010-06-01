/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.reader;

import org.labkey.api.data.Container;
import org.labkey.api.util.CloseableIterator;

import java.util.Map;
import java.io.File;
import java.io.IOException;
import java.io.Reader;

/**
 * User: adam
 * Date: May 5, 2009
 * Time: 7:12:58 PM
 */
public class TabLoader extends AbstractTabLoader<Map<String, Object>>
{
    // Infer whether there are headers
    public TabLoader(File inputFile) throws IOException
    {
        super(inputFile, null);
    }

    public TabLoader(File inputFile, boolean hasColumnHeaders) throws IOException
    {
        super(inputFile, hasColumnHeaders);
    }

    public TabLoader(File inputFile, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        super(inputFile, hasColumnHeaders, mvIndicatorContainer);
    }

    public TabLoader(Reader reader, boolean hasColumnHeaders) throws IOException
    {
        super(reader, hasColumnHeaders);
    }

    // Infer whether there are headers
    public TabLoader(CharSequence src) throws IOException
    {
        super(src, null);
    }

    public TabLoader(CharSequence src, boolean hasColumnHeaders) throws IOException
    {
        super(src, hasColumnHeaders);
    }

    public CloseableIterator<Map<String, Object>> iterator()
    {
        return mapIterator();
    }
}
