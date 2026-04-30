package org.labkey.query.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class MetadataTableJSONMixin
{
    MetadataTableJSONMixin(@JsonProperty("URL") String url)
    { }

    @JsonProperty("URL")
    abstract void setURL(String url); // rename property on deserialize
}
