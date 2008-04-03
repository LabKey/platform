/**
 * ExperimentWebService.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package org.labkey.experiment.ws;

import org.apache.axis.MessageContext;
import org.apache.axis.transport.http.HTTPConstants;
import org.fhcrc.cpas.exp.xml.ExperimentArchiveDocument;
import org.labkey.api.data.*;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.ACL;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.experiment.DataURLRelativizer;
import org.labkey.experiment.LSIDRelativizer;
import org.labkey.experiment.XarExporter;
import org.labkey.experiment.api.Experiment;
import org.labkey.experiment.api.ExperimentRun;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.Protocol;
import org.labkey.experiment.ws.experimentQuery.Predicate;
import org.labkey.experiment.ws.experimentQuery.Property;
import org.labkey.experiment.ws.experimentQuery.Target;
import org.labkey.experimentQuery.xml.Folder;
import org.labkey.experimentQuery.xml.FolderList;

import javax.servlet.http.HttpServletRequest;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ExperimentWebService implements java.rmi.Remote
{

    private static final String USER_PROPERTY_NAME = "remoteAPI.UserProperty";

    private User getUser()
    {
        MessageContext context = MessageContext.getCurrentContext();
        User result = (User)context.getProperty(USER_PROPERTY_NAME);
        if (result == null)
        {
            HttpServletRequest req = (HttpServletRequest) context.getProperty(HTTPConstants.MC_HTTP_SERVLETREQUEST);
            result = SecurityManager.getAuthenticatedUser(req);
            if (result == null)
            {
                result = UserManager.getGuestUser();
            }
            context.setProperty(USER_PROPERTY_NAME, result);
        }
        return result;
    }

    public FolderList getFolders(Target queryObject) throws java.rmi.RemoteException
    {
        checkQueryObject(queryObject, "Folder");

        Property[] props = queryObject.getProperty();
        Property parentPathProperty = getProperty(props, "ParentPath");
        if (parentPathProperty == null)
        {
            throw new IllegalArgumentException("Must specify a parent path");
        }
        if (!parentPathProperty.getPredicate().equals(Predicate.equal))
        {
            throw new IllegalArgumentException("Only exact matches are supported for ParentPath");
        }

        String name = getPropertyValue(props, "Name");


        StringMatcher nameMatcher = StringMatcher.createMatcher(name);
        List<Container> containers = new ArrayList<Container>();

        Container parentContainer = ContainerManager.getForPath(parentPathProperty.getValue());
        if (parentContainer != null)
        {
            for (Container child : parentContainer.getChildren())
            {
                if (hasPermission(child) &&
                    nameMatcher.matches(child.getName()))
                {
                    containers.add(child);
                }
            }
        }

        FolderList result = FolderList.Factory.newInstance();
        for (Container c : containers)
        {
            Folder folder = result.addNewFolder();
            folder.setParentPath(c.getParent().getPath());
            folder.setName(c.getName());
        }
        return result;
    }

    public ExperimentArchiveDocument getProtocols(Target queryObject) throws java.rmi.RemoteException
    {
        try
        {
            Container c = getContainer(queryObject, "Protocol");
            SelectInfo selectInfo = new SelectInfo(ExperimentServiceImpl.get().getTinfoProtocol(), queryObject.getProperty());
            selectInfo.addClause("Container", c.getId(), false);
            Protocol[] protocols = Table.executeQuery(ExperimentService.get().getSchema(), "SELECT * FROM " + ExperimentServiceImpl.get().getTinfoProtocol() + " WHERE " + selectInfo.getWhereClause(), selectInfo.getParams(), Protocol.class);
            XarExporter exporter = createXARExporter();
            for (Protocol protocol : protocols)
            {
                exporter.addProtocol(protocol, false);
            }
            return exporter.getXMLBean();
        }
        catch (SQLException e)
        {
            throw new RemoteException("SQL Problem", e);
        }
        catch (ExperimentException e)
        {
            throw new RemoteException("Experiment Problem", e);
        }

    }

    public Property getProperty(Property[] properties, String propertyName)
    {
        for (Property property : properties)
        {
            if (property.getName().equals(propertyName))
            {
                return property;
            }
        }
        return null;
    }

    private String getPropertyValue(Property[] properties, String propertyName)
    {
        Property property = getProperty(properties, propertyName);
        if (property != null)
        {
            return property.getValue();
        }
        return null;
    }

    private Container getContainer(Target queryObject, String expectedType) throws RemoteException
    {
        checkQueryObject(queryObject, expectedType);
        String folderPath = null;
        if (queryObject != null)
        {
            Property[] properties = queryObject.getProperty();
            folderPath = getPropertyValue(properties, "FolderPath");
        }
        if (folderPath == null)
        {
            throw new IllegalArgumentException("FolderPath is a required field");
        }
        Container c = ContainerManager.getForPath(folderPath);
        validatePermissions(c);

        return c;
    }

    private void checkQueryObject(Target queryObject, String expectedType) throws RemoteException
    {
        if (queryObject == null)
        {
            throw new IllegalArgumentException("Must specify a queryObject");
        }

        if (queryObject.getAPIVersion() != 1.0f)
        {
            throw new RemoteException("API Version " + queryObject.getAPIVersion() + " is not supported. This server handles requests from version 1.0.");
        }

        if (!expectedType.equals(queryObject.getName()))
        {
            throw new RemoteException("Expected to get a request for type " + expectedType + " but got a request for " + queryObject.getName());
        }
    }

    public ExperimentArchiveDocument getExperiments(Target queryObject) throws java.rmi.RemoteException
    {
        Container c = getContainer(queryObject, "Experiment");
        SelectInfo selectInfo = new SelectInfo(ExperimentServiceImpl.get().getTinfoExperiment(), queryObject.getProperty());
        selectInfo.addClause("Container", c.getId(), false);
        try
        {
            Experiment[] experiments = Table.executeQuery(ExperimentServiceImpl.get().getSchema(), "SELECT * FROM " + ExperimentServiceImpl.get().getTinfoExperiment() + " WHERE " + selectInfo.getWhereClause(), selectInfo.getParams(), Experiment.class);
            XarExporter exporter = createXARExporter();
            for (Experiment experiment : experiments)
            {
                exporter.addExperiment(experiment);
            }
            return exporter.getXMLBean();
        }
        catch (SQLException e)
        {
            throw new RemoteException("SQL problem", e);
        }
        catch (ExperimentException e)
        {
            throw new RemoteException("Experiment problem", e);
        }
    }

    private boolean hasPermission(Container c)
    {
        return c.hasPermission(getUser(), ACL.PERM_READ);
    }

    private void validatePermissions(Container c)
    {
        if (c == null)
        {
            throw new IllegalArgumentException("Unable to find container");
        }
        if (!hasPermission(c))
        {
            throw new IllegalArgumentException("No permission to access container " + c);
        }
    }

    private static class SelectInfo
    {
        private StringBuilder _whereClause = new StringBuilder();
        private List<Object> _params = new ArrayList<Object>();
        private TableInfo _tinfo;

        public SelectInfo(TableInfo tinfo, Property[] properties)
        {
            _tinfo = tinfo;

            if (properties != null)
            {
                for (Property prop : properties)
                {
                    if (!prop.getName().equals("FolderPath") && !prop.getName().equals("ExperimentLSID"))
                    {
                        addClause(prop.getName(), prop.getValue(), prop.getPredicate() == Predicate.like);
                    }
                }
            }
        }

        private void addClause(String name, Object value, boolean likeClause)
        {
            if (_whereClause.length() > 0)
            {
                _whereClause.append(" AND ");
            }
            _whereClause.append(name);
            if (value == null)
            {
                _whereClause.append(" IS NULL");
            }
            else
            {
                if (!likeClause)
                {
                    ColumnInfo col = _tinfo.getColumn(name);

                    if (col != null)
                    {
                        Object paramVal = CompareType.convertParamValue(col, value);

                        if (!(paramVal instanceof String))
                        {
                            _whereClause.append(" = ?");
                            _params.add(paramVal);
                            return;
                        }
                    }
                }

                _whereClause.append(" LIKE ?");
                // Todo - escape '%' and '_'
                String s = value.toString().replace('*', '%');
                _params.add(s);
            }
        }

        public String getWhereClause()
        {
            return _whereClause.toString();
        }

        public Object[] getParams()
        {
            return _params.toArray();
        }
    }

    private XarExporter createXARExporter()
    {
        return new XarExporter(LSIDRelativizer.ABSOLUTE, DataURLRelativizer.WEB_ADDRESSABLE);
    }

    public ExperimentArchiveDocument getExperimentRuns(Target queryObject) throws java.rmi.RemoteException
    {
        checkQueryObject(queryObject, "ExperimentRun");

        try
        {
            Property experimentLSIDProperty = getProperty(queryObject.getProperty(), "ExperimentLSID");
            if (experimentLSIDProperty == null || experimentLSIDProperty.getPredicate().equals(Predicate.like))
            {
                throw new RemoteException("ExperimentLSID must be set and cannot be a wildcard value");
            }

            XarExporter exporter = createXARExporter();

            String experimentLSID = experimentLSIDProperty.getValue();
            ExpExperiment experiment = ExperimentService.get().getExpExperiment(experimentLSID);
            if (experiment != null)
            {
                validatePermissions(experiment.getContainer());

                SelectInfo selectInfo = new SelectInfo(ExperimentServiceImpl.get().getTinfoExperimentRun(), queryObject.getProperty());
                Object[] params = new Object[(selectInfo.getParams()).length + 1];
                params[0] = experiment.getRowId();
                for (int i=1; i<params.length; i++)
                    params[i] = selectInfo.getParams()[i-1];

                String sql = "SELECT * FROM " + ExperimentServiceImpl.get().getTinfoExperimentRun()
                    + " WHERE RowId IN ( SELECT ExperimentRunId FROM exp.RunList WHERE ExperimentId = ? ) "
                    + (selectInfo.getWhereClause().length() >0 ? " AND " + selectInfo.getWhereClause() : "");


                ExperimentRun[] runs = Table.executeQuery(ExperimentServiceImpl.get().getSchema(), sql, params, ExperimentRun.class);
                for (ExperimentRun run : runs)
                {
                    exporter.addExperimentRun(run, experiment);
                }
            }
            return exporter.getXMLBean();
        }
        catch (SQLException e)
        {
            throw new RemoteException("SQL problem", e);
        }
        catch (ExperimentException e)
        {
            throw new RemoteException("Experiment problem", e);
        }
    }

}
