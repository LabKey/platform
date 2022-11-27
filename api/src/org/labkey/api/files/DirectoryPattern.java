/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.files;

import org.json.old.JSONObject;
import org.labkey.api.module.Module;


public class DirectoryPattern
{
    /**
    *Regular expression for Directory name including the extension.
    * */
    String _ext;
    DirectoryPattern _subDirectory;
    String _fileExt;
    Module _module;

    public DirectoryPattern(Module module)
    {
        this._module = module;
    }

    public String getExt()
    {
        return _ext;
    }

    public void setExt(String ext)
    {
        _ext = ext;
    }

    public DirectoryPattern getSubDirectory()
    {
        return _subDirectory;
    }

    public void setSubDirectory(DirectoryPattern subDirectory)
    {
        _subDirectory = subDirectory;
    }

    public String getFileExt()
    {
        return _fileExt;
    }

    public void setFileExt(String fileExt)
    {
        _fileExt = fileExt;
    }

    public Module getModule()
    {
        return _module;
    }

    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();

        json.put("DirectoryName", this.getExt());
        if(this.getSubDirectory() != null)
        {
            json.put("SubDirectory", this.getSubDirectory().toJSON());
        }

        if(this.getFileExt() != null)
        {
            json.put("File", this.getFileExt());
        }

        return json;
    }
}