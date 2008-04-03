package org.labkey.api.jsp.taglib;

public class ClearAll extends ButtonTag
{
    public ClearAll()
    {
        setText("Clear All");
        setOnclick("setAllCheckboxes(this.form, false);return false;");
    }
}
