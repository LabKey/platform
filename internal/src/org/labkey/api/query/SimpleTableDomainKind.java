/*
 * Copyright (c) 2010-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.query;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.module.SimpleModule;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.writer.ContainerUser;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: Sep 22, 2010
 *
 * A domain kind for {@link SimpleUserSchema.SimpleTable}s.
 */
public class SimpleTableDomainKind extends AbstractDomainKind
{
    private static String XAR_SUBSTITUTION_SCHEMA_NAME = "SchemaName";
    private static String XAR_SUBSTITUTION_TABLE_NAME = "TableName";
    private static String XAR_SUBSTITUTION_GUID = "GUID";

    public SimpleTableDomainKind()
    {
    }

    // uck. To get from a Domain to the SimpleTable we need to parse the Domain's type uri.
    public SimpleUserSchema.SimpleTable getTable(Domain domain)
    {
        String domainURI = domain.getTypeURI();
        if (domainURI == null)
            return null;

        Lsid lsid = new Lsid(domainURI);
        String prefix = lsid.getNamespacePrefix();
        if (prefix.startsWith(SimpleModule.NAMESPACE_PREFIX))
        {
            // XXX: need to unescape '-' (and '.' ?) in schema and table names
            String schemaName = prefix.substring(SimpleModule.NAMESPACE_PREFIX.length() + 1);
            String queryName = lsid.getObjectId();

            // HACK: have to reach out to get the user
            User user = HttpView.currentContext().getUser();
            UserSchema schema = QueryService.get().getUserSchema(user, domain.getContainer(), schemaName);
            if (schema != null)
            {
                TableInfo table = schema.getTable(queryName, true);
                if (table instanceof SimpleUserSchema.SimpleTable)
                    return (SimpleUserSchema.SimpleTable)table;
            }
        }

        return null;
    }

    @Override
    public String getKindName()
    {
        return SimpleModule.NAMESPACE_PREFIX;
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        return "Extensible Table '" + domain.getName() + "'";
    }

    @Override
    public Handler.Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return lsid.getNamespacePrefix() != null && lsid.getNamespacePrefix().startsWith(SimpleModule.NAMESPACE_PREFIX) ? Handler.Priority.MEDIUM : null;
    }

    public String generateDomainURI(String schemaName, String tableName, Container c, User u)
    {
        return getDomainURI(schemaName, tableName, c, u);
    }

    public static String getDomainURI(String schemaName, String tableName, Container c, User u)
    {
        try
        {
            XarContext xc = getXarContext(schemaName, tableName, getDomainContainer(c), u);
            return LsidUtils.resolveLsidFromTemplate(SimpleModule.DOMAIN_LSID_TEMPLATE, xc, SimpleModule.DOMAIN_NAMESPACE_PREFIX_TEMPLATE);
        }
        catch (XarFormatException xfe)
        {
            return null;
        }
    }

    protected static Container getDomainContainer(Container c)
    {
        return c.isWorkbook() ? c.getParent() : c;
    }

    public static String createPropertyURI(String schemaName, String tableName, Container c, User u)
    {
        try
        {
            XarContext xc = getXarContext(schemaName, tableName, c, u);
            xc.addSubstitution(XAR_SUBSTITUTION_GUID, GUID.makeGUID());
            return LsidUtils.resolveLsidFromTemplate(SimpleModule.PROPERTY_LSID_TEMPLATE, xc, SimpleModule.PROPERTY_NAMESPACE_PREFIX_TEMPLATE);
        }
        catch (XarFormatException xfe)
        {
            return null;
        }
    }

    protected static XarContext getXarContext(String schemaName, String tableName, Container c, User u)
    {
        // XXX: need to escape '-' (and '.' ?) in the schema and table names
        XarContext xc = new XarContext("Domains", c, u);
        xc.addSubstitution(XAR_SUBSTITUTION_SCHEMA_NAME, schemaName);
        xc.addSubstitution(XAR_SUBSTITUTION_TABLE_NAME, tableName);
        return xc;
    }

    @Override
    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        SimpleUserSchema.SimpleTable table = getTable(domain);
        if (table != null)
        {
            ColumnInfo objectUriColumn = table.getObjectUriColumn();
            if (objectUriColumn != null)
            {
                TableInfo schemaTable = table.getRealTable();

                SQLFragment sql = new SQLFragment();
                sql.append("SELECT o.ObjectId FROM " + schemaTable + " me, exp.object o WHERE me." + objectUriColumn.getSelectName() + " = o.ObjectURI AND me.Container = ?");
                sql.add(domain.getContainer().getId());
                return sql;
            }
        }
        return new SQLFragment("NULL");
    }

    @Override
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        SimpleUserSchema.SimpleTable table = getTable(domain);
        if (table == null)
            return null;

        // nasty
        QueryDefinition qdef = table._userSchema.getQueryDefForTable(table.getName());
        if (qdef != null)
            return table._userSchema.urlFor(QueryAction.executeQuery, qdef);

        return null;
    }

    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        if (containerUser.getContainer().isWorkbook())
            return null;

        return PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(containerUser.getContainer(), domain.getTypeURI(), true, true, true);
    }

    @Override
    public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user)
    {
        return super.urlCreateDefinition(schemaName, queryName, getDomainContainer(container), user);
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return container.hasPermission(user, AdminPermission.class) && !container.isWorkbook();
    }

    @Override
    public boolean canEditDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, AdminPermission.class);
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        SimpleUserSchema.SimpleTable table = getTable(domain);
        if (table != null)
        {
            // return the set of built-in column names.
            return Sets.newHashSet(Iterables.transform(table.getBuiltInColumns(),
                new Function<ColumnInfo, String>() {
                    public String apply(ColumnInfo col)
                    {
                        return col.getName();
                    }
                }
            ));
        }
        return Collections.emptySet();
    }

    @Override
    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user, TemplateInfo templateInfo)
    {
        String schemaName = (String)arguments.get("schemaName");
        String tableName = (String)arguments.get("tableName");
        if (schemaName == null || tableName == null)
            throw new IllegalArgumentException("schemaName and tableName are required");

        String domainURI = generateDomainURI(schemaName, tableName, container, user);
        return PropertyService.get().createDomain(container, domainURI, domain.getName(), templateInfo);
    }

}

