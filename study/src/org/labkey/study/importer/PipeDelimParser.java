/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.study.importer;

/**
 * User: brittp
 * Date: Jan 7, 2006
 * Time: 3:11:11 PM
 */
class PipeDelimParser
{
    private int _left = 0;
    private int _right = 0;
    private String _data;

    public PipeDelimParser(String data)
    {
        _data = data;
        _right = _data.indexOf('|');
    }

    public boolean hasNext()
    {
        return _left <= _data.length();
    }

    public String next()
    {
        if (!hasNext())
            throw new IllegalStateException("Expected field not found.  Invalid visit map format?");
        String value;
        if (_right >= 0)
        {
            value = _data.substring(_left, _right);
            _left = _right + 1;
            _right = _data.indexOf('|', _left);
        }
        else
        {
            if (_left == _data.length())
                value = "";
            else
                value = _data.substring(_left);
            _left = _data.length() + 1;
        }
        return value.trim();
    }
}
