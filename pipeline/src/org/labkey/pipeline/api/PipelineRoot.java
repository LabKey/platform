/*
 * Copyright (c) 2005-2016 LabKey Corporation
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
 * Serialization bean for round-tripping pipeline root configuration to/from the database.
 */
public class PipelineRoot extends Entity
{
    int _pipelineRootId;
    String _path;
    String _supplementalPath;
    String _type;
    
    boolean _searchable = true;

    public PipelineRoot()
    {
    }

    public PipelineRoot(PipelineRoot root)
    {
        // Entity
        root.copyTo(this);

        // PipelineRoot
        this._pipelineRootId = root._pipelineRootId;
        this._path = root._path;
        this._supplementalPath = root._supplementalPath;
        this._type = root._type;
        this._searchable = root._searchable;
    }

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
        _path = normalizePath(path);
    }

    /** Pipeline roots must be a directory URI, so they must end with '/' */
    private String normalizePath(String path)
    {
        if (path != null && !path.endsWith("/"))
        {
            return path + "/";
        }
        return path;
    }

    public String getPath()
    {
        return _path;
    }

    public void setSupplementalPath(String supplementalPath)
    {
        _supplementalPath = normalizePath(supplementalPath);
    }

    public String getSupplementalPath()
    {
        return _supplementalPath;
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

    public boolean isSearchable()
    {
        return _searchable;
    }

    public void setSearchable(boolean searchable)
    {
        _searchable = searchable;
    }
}
