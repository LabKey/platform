/*
 * Copyright (c) 2008 LabKey Software Foundation
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
package org.labkey.api.pipeline.cmd;

import org.labkey.api.util.FileType;

/**
 * <code>TaskPath</code>
 */
public class TaskPath
{
    private FileType _type;
    private String _name;
    private boolean _copyInput;

    public TaskPath()
    {
    }

    public TaskPath(FileType type)
    {
        _type = type;
    }

    public TaskPath(String ext)
    {
        _type = new FileType(ext);
    }

    public FileType getType()
    {
        return _type;
    }

    public void setType(FileType type)
    {
        _type = type;
    }

    public void setExtension(String ext)
    {
        _type = new FileType(ext);
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public boolean isCopyInput()
    {
        return _copyInput;
    }

    public void setCopyInput(boolean copyInput)
    {
        _copyInput = copyInput;
    }
}
