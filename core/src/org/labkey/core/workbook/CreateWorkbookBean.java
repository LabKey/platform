package org.labkey.core.workbook;

/**
 * Created by IntelliJ IDEA.
 * User: labkey
 * Date: Jan 6, 2010
 * Time: 12:17:45 PM
 */
public class CreateWorkbookBean
{
    private String _name;
    private String _description;

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
}
