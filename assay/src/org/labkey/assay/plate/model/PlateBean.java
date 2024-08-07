package org.labkey.assay.plate.model;

import org.labkey.api.data.Entity;
import org.labkey.assay.plate.PlateImpl;

/**
 * Serializes a row in the assay.plate table.
 */
public class PlateBean extends Entity
{
    private Boolean _archived;
    private Integer _rowId;
    private String _lsid;
    private String _name;
    private Boolean _template;
    private String _dataFileId;
    private String _assayType;
    private Integer _plateSet;
    private Integer _plateType;
    private String _plateId;
    private String _description;
    private String _barcode;

    public static PlateBean from(PlateImpl plate)
    {
        PlateBean bean = new PlateBean();

        bean.setRowId(plate.getRowId());
        bean.setArchived(plate.isArchived());
        bean.setLsid(plate.getLSID());
        bean.setName(plate.getName());
        bean.setTemplate(plate.isTemplate());
        bean.setDataFileId(plate.getDataFileId());
        bean.setAssayType(plate.getAssayType());
        bean.setPlateSet(plate.getPlateSet() != null ? plate.getPlateSet().getRowId() : null);
        bean.setPlateType(plate.getPlateType().getRowId());
        bean.setPlateId(plate.getPlateId());
        bean.setDescription(plate.getDescription());
        bean.setBarcode(plate.getBarcode());

        return bean;
    }

    public Boolean getArchived()
    {
        return _archived;
    }

    public void setArchived(Boolean archived)
    {
        _archived = archived;
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

    public String getDataFileId()
    {
        return _dataFileId;
    }

    public void setDataFileId(String dataFileId)
    {
        _dataFileId = dataFileId;
    }

    public String getAssayType()
    {
        return _assayType;
    }

    public void setAssayType(String assayType)
    {
        _assayType = assayType;
    }

    public Integer getPlateSet()
    {
        return _plateSet;
    }

    public void setPlateSet(Integer plateSet)
    {
        _plateSet = plateSet;
    }

    public Integer getPlateType()
    {
        return _plateType;
    }

    public void setPlateType(Integer plateType)
    {
        _plateType = plateType;
    }

    public String getPlateId()
    {
        return _plateId;
    }

    public void setPlateId(String plateId)
    {
        _plateId = plateId;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getBarcode()
    {
        return _barcode;
    }

    public void setBarcode(String barcode)
    {
        _barcode = barcode;
    }
}
