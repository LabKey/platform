package org.labkey.assay.plate;

import org.labkey.api.assay.plate.PlateType;

/**
 * Plate layouts encompass plate types but also include assay specific information
 */
public class PlateLayout
{
    private PlateType _plateType;
    private String _assayType;
    private String _description;
    private String _name;

    public PlateLayout(String name, PlateType type, String assayType, String description)
    {
        _name = name;
        _plateType = type;
        _assayType = assayType;
        _description = description;
    }

    public String getName()
    {
        return _name;
    }

    public PlateType getPlateType()
    {
        return _plateType;
    }

    public String getAssayType()
    {
        return _assayType;
    }

    public String getDescription()
    {
        return _description;
    }
}
