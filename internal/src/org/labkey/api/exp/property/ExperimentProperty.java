package org.labkey.api.exp.property;

import org.fhcrc.cpas.exp.xml.SimpleTypeNames;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.data.Container;

import java.util.Map;
import java.util.HashMap;
import java.sql.SQLException;

public class ExperimentProperty
{
    static private String EXPERIMENT_PROPERTY_URIBASE = "urn:exp.labkey.org/#";

    static public SystemProperty COMMENT = new SystemProperty(EXPERIMENT_PROPERTY_URIBASE + "Comment", PropertyType.STRING);
    static public SystemProperty LOGTEXT = new SystemProperty(EXPERIMENT_PROPERTY_URIBASE + "LogText", PropertyType.STRING);
    static public SystemProperty PROTOCOLIMPLEMENTATION = new SystemProperty(EXPERIMENT_PROPERTY_URIBASE + "ProtocolImplementation", PropertyType.STRING);
    static public SystemProperty SampleSetLSID = new SystemProperty(EXPERIMENT_PROPERTY_URIBASE + "SampleSetLSID", PropertyType.STRING);
    static public void register()
    {
        // do nothing
    }
}
