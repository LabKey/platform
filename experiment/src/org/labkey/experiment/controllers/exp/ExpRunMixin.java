package org.labkey.experiment.controllers.exp;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


/**
 * Configures the fields that are not returned when serializing a ExpRun.
 * Used in ExperimentController.LoadAssayRunAction.
 */
@JsonIgnoreProperties({
        "protocol",
        "container",
        "createdBy",
        "modifiedBy",
        "protocolApplications",
        "inputProtocolApplication",
        "outputProtocolApplication",
        "dataObject"
})
public interface ExpRunMixin
{
}
