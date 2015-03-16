/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
package org.labkey.redcap;

import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;

import java.io.IOException;

/**
 * User: klum
 * Date: 4/16/13
 */
public class RedcapCommandResponse
{
    String _text;
    int _statusCode;

    public RedcapCommandResponse(String text, int statusCode)
    {
        _text = text;
        _statusCode = statusCode;
    }

    public DataLoader getLoader()
    {
        try {

            TabLoader loader = new TabLoader(_text, true);
            loader.setInferTypes(false);
            loader.parseAsCSV();

            return loader;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public String getText()
    {
        return _text;
    }

    public int getStatusCode()
    {
        return _statusCode;
    }
}
