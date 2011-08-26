package org.labkey.study.model;

import org.apache.commons.beanutils.BeanUtils;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Aug 25, 2011
 * Time: 3:51:31 PM
 */
public class EmphasisStudyDefinition
{
    private String _name;
    private String _description;
    private String _srcPath;
    private String _dstPath;
    private int[] _datasets;

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getSrcPath()
    {
        return _srcPath;
    }

    public void setSrcPath(String srcPath)
    {
        _srcPath = srcPath;
    }

    public String getDstPath()
    {
        return _dstPath;
    }

    public void setDstPath(String dstPath)
    {
        _dstPath = dstPath;
    }

    public int[] getDatasets()
    {
        return _datasets;
    }

    public void setDatasets(JSONArray datasets)
    {
        if (datasets != null)
        {
            _datasets = new int[datasets.length()];

            for (int i=0; i < datasets.length(); i++)
            {
                _datasets[i] = datasets.getInt(i);
            }
        }
    }

    public static EmphasisStudyDefinition fromJSON(JSONObject json)
    {
        try
        {
            EmphasisStudyDefinition sample = new EmphasisStudyDefinition();
            BeanUtils.populate(sample, json);

            JSONArray dsa = json.getJSONArray("datasets");
            sample.setDatasets(dsa);

            return sample;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
