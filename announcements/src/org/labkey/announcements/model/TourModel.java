package org.labkey.announcements.model;

import org.labkey.api.data.Entity;

import java.io.Serializable;

/**
 * Created by Marty on 1/15/2015.
 *
 * Bean class for comm.Tours
 */
public class TourModel extends Entity implements Serializable
{
    private Integer _RowId;
    private String _Title;
    private String _Description;
    private String _Json;
    private Integer _Mode;

    public TourModel()
    {
    }

    public Integer getRowId()
    {
        return _RowId;
    }

    public void setRowId(Integer id)
    {
        _RowId = id;
    }

    public String getTitle()
    {
        return _Title;
    }

    public void setTitle(String title)
    {
        _Title = title;
    }

    public String getDescription()
    {
        return _Description;
    }

    public void setDescription(String description)
    {
        _Description = description;
    }

    public String getJson()
    {
        return _Json;
    }

    public void setJson(String json)
    {
        _Json = json;
    }

    public Integer getMode()
    {
        return _Mode;
    }

    public void setMode(Integer mode)
    {
        _Mode = mode;
    }
}
