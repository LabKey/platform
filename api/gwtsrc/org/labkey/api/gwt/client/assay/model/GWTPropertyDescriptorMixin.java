package org.labkey.api.gwt.client.assay.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.labkey.api.gwt.client.util.StringProperty;

/**
 * Configures the fields that are not returned when serializing a GWTPropertyDescriptor.
 * Ideally we would just add the @JsonIgnore annotations to GWTPropertyDescriptor directly,
 * but the GWT compiler would need to have jackson on the classpath which isn't
 * necessary.
 */
@JsonIgnoreProperties({
        "setMeasure",
        "setDimension",
        "setExcludeFromShifting",
        "lookupDescription",
        "fileType",
        "updatedField",
        "newField"
})
public abstract class GWTPropertyDescriptorMixin
{
    GWTPropertyDescriptorMixin(@JsonProperty("PHI") StringProperty phi, @JsonProperty("URL") StringProperty url)
    { }
    @JsonProperty("PHI")
    abstract void setPHI(String phi); // rename property on deserialize
    @JsonProperty("URL")
    abstract void setURL(String url); // rename property on deserialize
}
