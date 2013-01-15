package org.labkey.survey.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.module.SimpleModule;
import org.labkey.api.query.SimpleTableDomainKind;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 1/14/13
 */
public class SurveyTableDomainKind extends SimpleTableDomainKind
{
    public static final String NAME = "SurveyTable";

    @Override
    public String getKindName()
    {
        return NAME;
    }

    public static Container getDomainContainer(Container c)
    {
        if (c != null)
            return c.getProject();

        return c;
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    @Override
    public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user)
    {
        return super.urlCreateDefinition(schemaName, queryName, getDomainContainer(container), user);
    }

    @Override
    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user)
    {
        return super.createDomain(domain, arguments, getDomainContainer(container), user);
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        String namespacePrefix = lsid.getNamespacePrefix();
        String objectId = lsid.getObjectId();
        if (namespacePrefix == null || objectId == null)
        {
            return null;
        }
        return (namespacePrefix.equalsIgnoreCase(SimpleModule.NAMESPACE_PREFIX + "-survey") && objectId.equalsIgnoreCase("users")) ? Handler.Priority.MEDIUM : null;
    }


}
