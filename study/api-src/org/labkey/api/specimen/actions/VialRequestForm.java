package org.labkey.api.specimen.actions;

public class VialRequestForm extends RequestIdForm
{
    public enum IdTypes
    {
        GlobalUniqueId,
        SpecimenHash,
        RowId
    }

    private String _idType;
    private String[] _vialIds;

    public String[] getVialIds()
    {
        return _vialIds;
    }

    public void setVialIds(String[] vialIds)
    {
        _vialIds = vialIds;
    }

    public String getIdType()
    {
        return _idType;
    }

    public void setIdType(String idType)
    {
        _idType = idType;
    }
}
