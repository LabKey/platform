package org.labkey.api.files;

import org.json.JSONObject;
import org.labkey.api.module.Module;

import java.util.ArrayList;
import java.util.List;

public class DirectoryPattern
{
    /**
    *Regular expression for Directory name including the extension.
    * */
    String ext;
    List<DirectoryPattern> subDirectories;
    FilePattern _filePattern;
    Module module;

    public DirectoryPattern(Module module)
    {
        this.module = module;
    }

    public String getExt()
    {
        return ext;
    }
    /**
     * This should be set as an actual JAVA Regular Expression.
    * */
    public void setExt(String ext)
    {
        this.ext = ext;
    }

    public List<DirectoryPattern> getSubDirectories()
    {
        return subDirectories;
    }

    public void setSubDirectories(List<DirectoryPattern> subDirectories)
    {
        this.subDirectories = subDirectories;
    }

    public FilePattern getFilePattern()
    {
        return _filePattern;
    }

    public void setFilePattern(FilePattern filePattern)
    {
        this._filePattern = filePattern;
    }

    public Module getModule()
    {
        return module;
    }

    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();
        List<JSONObject> subDirs = new ArrayList<>();

        json.put("DirectoryName", ext);

        if(subDirectories != null)
        {
            for (DirectoryPattern dir : subDirectories)
            {
                subDirs.add(dir.toJSON());
            }
        }

        json.put("SubDirectory", subDirs);
        json.put("File", _filePattern.toJSON());

        return json;
    }
}