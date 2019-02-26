package org.labkey.api.gwt.client.assay.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
        "fileType"
})
public interface GWTPropertyDescriptorMixin
{
}
