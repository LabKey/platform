package org.labkey.api.files;

import org.json.JSONObject;

public class FilePattern
{
    /**
     *Regular expression for Directory name including the extension.
     * */
    String ext;

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

    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();
        json.put("ext", ext);
        return json;
    }
}

