package org.labkey.experiment.xar;

import org.apache.log4j.Logger;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.experiment.api.property.DomainImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.fhcrc.cpas.exp.xml.ExperimentArchiveType;
import org.fhcrc.cpas.exp.xml.DomainDescriptorType;
import org.fhcrc.cpas.exp.xml.PropertyDescriptorType;

import java.util.Map;
import java.util.HashMap;
import java.sql.SQLException;

/**
 * User: jeckels
 * Date: Jul 5, 2007
 */
public abstract class AbstractXarImporter
{
    protected final Logger _log;
    protected final XarSource _xarSource;
    protected final Container _container;
    protected ExperimentArchiveType _experimentArchive;
    protected final User _user;

    protected Map<String, Domain> _loadedDomains = new HashMap<String, Domain>();

    public AbstractXarImporter(XarSource source, Container targetContainer, Logger logger, User user)
    {
        _xarSource = source;
        _container = targetContainer;
        _log = logger;
        _user = user;
    }

    protected void checkDataCpasType(String declaredType)
    {
        if (declaredType != null && !"Data".equals(declaredType))
        {
            _log.warn("Unrecognized CpasType '" + declaredType + "' loaded for Data object.");
        }
    }

    protected void checkMaterialCpasType(String declaredType) throws SQLException, XarFormatException
    {
        if (declaredType != null && !"Material".equals(declaredType))
        {
            if (ExperimentService.get().getSampleSet(declaredType) != null)
            {
                return;
            }

            _log.warn("Unrecognized CpasType '" + declaredType + "' loaded for Material object.");
        }
    }

    protected void checkProtocolApplicationCpasType(String cpasType, Logger logger)
    {
        if (cpasType != null && !"ProtocolApplication".equals(cpasType) && !"ExperimentRun".equals(cpasType) && !"ExperimentRunOutput".equals(cpasType))
        {
            logger.warn("Unrecognized CpasType '" + cpasType + "' loaded for ProtocolApplication object.");
        }
    }
}
