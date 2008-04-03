/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
package org.labkey.pipeline.api;

import org.labkey.api.data.Entity;

/**
 */
public class PipelineRoot extends Entity
{
    public static final String PRIMARY_ROOT = "PRIMARY";

    int _pipelineRootId;
    String _path;
    String _type;

    public void setPipelineRootId(int id)
    {
        _pipelineRootId = id;
    }

    public int getPipelineRootId()
    {
        return _pipelineRootId;
    }

    public void setPath(String path)
    {
        // Pipeline roots must be a directory URI, so they must end with '/'
        _path = path;
        if (!_path.endsWith("/"))
            _path += "/";
    }

    public String getPath()
    {
        return _path;
    }

    public String getType()
    {
        if (_type == null)
            throw new IllegalStateException("Pipeline type was not specified.");
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }
}
