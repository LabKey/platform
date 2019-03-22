/*
 * Copyright (c) 2005-2014 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;

import org.labkey.api.view.DisplayElement;

/**
 * Base class for a column to be rendered into some sort of output
 * User: arauch
 * Date: Feb 15, 2005
 */
public abstract class RenderColumn extends DisplayElement
{
    private String _formatString = null;
    protected String _name = this.getClass().getName() + this.hashCode();
    private String _tsvFormatString;

    public String getFormatString()
    {
        return _formatString;
    }

    public void setFormatString(String formatString)
    {
        _formatString = formatString;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    abstract public String getCaption();

    public void setTsvFormatString(String tsvFormatString)
    {
        _tsvFormatString = tsvFormatString;
    }

    public String getTsvFormatString()
    {
        return _tsvFormatString;
    }
}
