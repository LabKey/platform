package org.labkey.query.reports.getdata;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.Map;

/**
 * Builder to receive JSON deserialization for a data source for the GetData API and create the real data source.
 *
 * Subclasses are serialized in JSON based on the "type" propery, which chooses the appropriate subclass.
 * User: jeckels
 * Date: 5/20/13
 */
@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, include=JsonTypeInfo.As.PROPERTY, property="type")
@JsonSubTypes({
        @JsonSubTypes.Type(value=LabKeySQLReportDataSourceBuilder.class),
        @JsonSubTypes.Type(value=SchemaQueryReportDataSourceBuilder.class)})
public abstract class AbstractReportDataSourceBuilder
{
    private SchemaKey _schemaKey;
    private String _containerFilterName;
    private Map<String, String> _parameters = Collections.emptyMap();

    public SchemaKey getSchemaKey()
    {
        return _schemaKey;
    }

    public void setSchemaName(SchemaKey schemaKey)
    {
        _schemaKey = schemaKey;
    }

    public ContainerFilter getContainerFilter(User user)
    {
        if (_containerFilterName == null)
        {
            return null;
        }
        return ContainerFilter.getContainerFilterByName(_containerFilterName, user);
    }

    public void setContainerFilter(String containerFilterName)
    {
        _containerFilterName = containerFilterName;
    }

    public Map<String, String> getParameters()
    {
        return _parameters;
    }

    public void setParameters(Map<String, String> parameters)
    {
        _parameters = Collections.unmodifiableMap(parameters);
    }

    public abstract QueryReportDataSource create(User user, Container container);
}
