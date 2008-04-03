package org.labkey.query.controllers;

public class InternalSourceViewForm extends InternalViewForm
{
    public String ff_columnList;
    public String ff_filter;

    public void setFf_columnList(String str)
    {
        ff_columnList = str;
    }

    public void setFf_filter(String str)
    {
        ff_filter = str;
    }
}
