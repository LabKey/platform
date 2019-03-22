package org.labkey.api.files;

import org.json.JSONObject;
import org.labkey.api.module.Module;


public class DirectoryPattern
{
    /**
    *Regular expression for Directory name including the extension.
    * */
    String _ext;
    DirectoryPattern _subDirectory;
    String _fileExt;
    Module module;

    public DirectoryPattern(Module module)
    {
        this.module = module;
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
        return module;
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