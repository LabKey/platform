package org.labkey.experiment.controllers.property;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Configures the fields to be returned when serializing a GWTDomain.
 * Ideally we would just add the @JsonIgnore annotations to GWTDomain directly,
 * but the GWT compiler would need to have jackson on the classpath which isn't
 * necessary.
 */
@JsonIgnoreProperties({
        "_Ts",
        "allowFileLinkProperties",
        "allowAttachmentProperties",
        "allowFlagProperties",

        // CONSIDER: Exclude mandatory and reserved names for now, but we may need it when re-implementing a domain designer
        "mandatoryFieldNames",
        "reservedFieldNames",
        "excludeFromExportFieldNames",
        "phiNotAllowedFieldNames",
        // The default value options and the default-default should not be on GWTDomain
        "defaultDefaultValueType",
        "defaultValueOptions",
        "defaultValuesURL",
        "provisioned",
})
public interface GWTDomainMixin
{
}
