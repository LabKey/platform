/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.experiment;

import org.apache.commons.io.FileUtils;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.exp.api.ExpData;

import java.io.IOException;
import java.io.Writer;

/**
 * User: bbimber
 * Date: 7/3/12
 * Time: 7:09 AM
 */
public class DataFileSizeDisplayColumn extends SimpleDisplayColumn
{
    private ExpData _data;

    public DataFileSizeDisplayColumn(ExpData data, String name)
    {
        _data = data;
        setCaption(name);
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        if (_data == null)
        {
            out.write("");
        }
        else if (_data.getFile() == null || !_data.getFile().exists())
        {
            out.write("File not found");
        }
        else
        {
            long size = _data.getFile().length();
            out.write(FileUtils.byteCountToDisplaySize(size));
        }
    }

}
