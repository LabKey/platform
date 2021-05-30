package org.labkey.api.specimen.actions;

public class SpecimenWebPartForm
{
    private String[] _grouping1;
    private String[] _grouping2;
    private String[] _columns;

    public String[] getGrouping1()
    {
        return _grouping1;
    }

    public void setGrouping1(String[] grouping1)
    {
        _grouping1 = grouping1;
    }

    public String[] getGrouping2()
    {
        return _grouping2;
    }

    public void setGrouping2(String[] grouping2)
    {
        _grouping2 = grouping2;
    }

    public String[] getColumns()
    {
        return _columns;
    }

    public void setColumns(String[] columns)
    {
        _columns = columns;
    }
}
