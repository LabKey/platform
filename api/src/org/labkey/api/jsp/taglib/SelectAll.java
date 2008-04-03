package org.labkey.api.jsp.taglib;

public class SelectAll extends ButtonTag
{
    public SelectAll()
    {
        setText("Select All");
        setOnclick("setAllCheckboxes(this.form, true);return false;");
    }
}
