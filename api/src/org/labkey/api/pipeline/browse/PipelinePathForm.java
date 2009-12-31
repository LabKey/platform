/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
package org.labkey.api.pipeline.browse;

/**
 * <code>PipelinePathForm</code>
 * 
 * Form bean class for pipeline root navigation.
 */
public class PipelinePathForm
{
    private String _path;
    private String[] _file = new String[0];

    public String getPath()
    {
        return _path;
    }

    public void setPath(String path)
    {
        _path = path;
    }

    public String[] getFile()
    {
        return _file;
    }

    public void setFile(String[] file)
    {
        for (String s : file)
        {
            if (s != null)
            {
                if (s.indexOf("..") != -1 || s.indexOf("/") != -1 || s.indexOf("\\") != -1)
                {
                    throw new IllegalArgumentException("File names should not include any path information");
                }
            }
        }
        _file = file;
    }
}
