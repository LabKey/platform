package org.labkey.assay.plate.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.labkey.assay.plate.WellGroupImpl;

/**
 * Used to serialize to the WellGroup table
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WellGroupBean
{
    private Integer _rowId;
    private String _lsid;
    private Integer _plateId;
    private String _name;
    private Boolean _template;
    private String _typename;

    public static WellGroupBean from(WellGroupImpl wellGroup)
    {
        WellGroupBean bean = new WellGroupBean();

        bean.setRowId(wellGroup.getRowId());
        bean.setLsid(wellGroup.getLSID());
        bean.setPlateId(wellGroup.getPlateId());
        bean.setName(wellGroup.getName());
        bean.setTemplate(wellGroup.isTemplate());
        bean.setTypename(wellGroup.getTypeName());

        return bean;
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public String getLsid()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        _lsid = lsid;
    }

    public Integer getPlateId()
    {
        return _plateId;
    }

    public void setPlateId(Integer plateId)
    {
        _plateId = plateId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public Boolean getTemplate()
    {
        return _template;
    }

    public void setTemplate(Boolean template)
    {
        _template = template;
    }

    public String getTypename()
    {
        return _typename;
    }

    public void setTypename(String typename)
    {
        _typename = typename;
    }
}
