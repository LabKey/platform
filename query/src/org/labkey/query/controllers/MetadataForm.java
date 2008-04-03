package org.labkey.query.controllers;

import org.labkey.api.query.QueryForm;

public class MetadataForm extends QueryForm
{
    public String ff_metadataText;

    public void setFf_metadataText(String text)
    {
        ff_metadataText = text;
    }
}
