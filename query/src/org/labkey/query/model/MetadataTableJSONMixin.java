package org.labkey.query.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.labkey.api.gwt.client.util.StringProperty;

public abstract class MetadataTableJSONMixin
{
    MetadataTableJSONMixin(@JsonProperty("URL") StringProperty url)
    { }

    @JsonProperty("URL")
    abstract void setURL(String url); // rename property on deserialize
}
