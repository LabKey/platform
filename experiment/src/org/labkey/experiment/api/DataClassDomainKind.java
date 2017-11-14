/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.experiment.api;

import com.google.common.collect.Sets;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.query.DataClassUserSchema;
import org.labkey.api.exp.query.ExpDataClassDataTable;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.writer.ContainerUser;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DomainKind implementation for data classes, allowing customizable fields to be attached to the based set of columns.
 * User: kevink
 * Date: 9/15/15
 */
public class DataClassDomainKind extends AbstractDomainKind
{
    public static final String PROVISIONED_SCHEMA_NAME = "expdataclass";

    private static final Set<PropertyStorageSpec> BASE_PROPERTIES;
    private static final Set<PropertyStorageSpec.Index> INDEXES;
    private static final Set<String> RESERVED_NAMES;
    private static final Set<PropertyStorageSpec.ForeignKey> FOREIGN_KEYS;

    static {
        BASE_PROPERTIES = Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(
                new PropertyStorageSpec("genId", JdbcType.INTEGER),
                new PropertyStorageSpec("lsid", JdbcType.VARCHAR, 300).setNullable(false)
        )));


        RESERVED_NAMES = BASE_PROPERTIES.stream().map(PropertyStorageSpec::getName).collect(Collectors.toSet());
        RESERVED_NAMES.addAll(Arrays.asList(ExpDataClassDataTable.Column.values()).stream().map(ExpDataClassDataTable.Column::name).collect(Collectors.toList()));

        FOREIGN_KEYS = Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(
                // NOTE: We join to exp.data using LSID instead of rowid for insert performance -- we will generate
                // the LSID once on the server and insert into exp.object, exp.data, and the provisioned table at the same time.
                new PropertyStorageSpec.ForeignKey("lsid", "exp", "Data", "LSID", null, false)
        )));

        INDEXES = Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(
                new PropertyStorageSpec.Index(true, "lsid")
        )));
    }

    public DataClassDomainKind()
    {
    }

    @Override
    public String getKindName()
    {
        return "DataClass";
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    @Override
    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        return null;
    }

    @Override
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        ExpDataClass dataClass = getDataClass(domain);
        if (dataClass == null)
            return null;

        return dataClass.detailsURL();
    }

    private ExpDataClass getDataClass(Domain domain)
    {
        return ExperimentService.get().getDataClass(domain.getTypeURI());
    }

    @Nullable
    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        return PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(containerUser.getContainer(), domain.getTypeURI(), true, false, false);
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties(Domain domain)
    {
        return BASE_PROPERTIES;
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return RESERVED_NAMES;
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices(Domain domain)
    {
        return INDEXES;
    }

    @Override
    public Set<PropertyStorageSpec.ForeignKey> getPropertyForeignKeys(Container container)
    {
        return FOREIGN_KEYS;
    }

    @Nullable
    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return getKindName().equals(lsid.getNamespacePrefix()) ? Priority.MEDIUM : null;
    }

    @Override
    public DbSchemaType getSchemaType()
    {
        return DbSchemaType.Provisioned;
    }

    @Override
    public String getStorageSchemaName()
    {
        return PROVISIONED_SCHEMA_NAME;
    }

    @Override
    public DbScope getScope()
    {
        return ExperimentService.get().getSchema().getScope();
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return container.hasPermission(user, AdminPermission.class);
    }


    @Override
    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user, TemplateInfo templateInfo)
    {
        String name = domain.getName();
        if (name == null)
            throw new IllegalArgumentException("DataClass name required");

        String description = domain.getDescription();
        List<GWTPropertyDescriptor> properties = (List<GWTPropertyDescriptor>)domain.getFields();
        List<GWTIndex> indices = (List<GWTIndex>)domain.getIndices();

        String nameExpression = arguments.containsKey("nameExpression") ? String.valueOf(arguments.get("nameExpression")) : null;

        Integer sampleSetId = null;
        String sampleSet = arguments.containsKey("sampleSet") ? String.valueOf(arguments.get("sampleSet")) : null;
        if (sampleSet != null)
        {
            int id = 0;
            try { id = Integer.parseInt(sampleSet); } catch (NumberFormatException e) { }
            if (id > 0)
                sampleSetId = id;

            ExpSampleSet ss = ExperimentService.get().getSampleSet(container, sampleSet, false);
            if (ss != null)
                sampleSetId = ss.getRowId();
        }

        ExpDataClass dataClass;
        try
        {
            dataClass = ExperimentService.get().createDataClass(container, user, name, description, properties, indices, sampleSetId, nameExpression, templateInfo);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (ExperimentException e)
        {
            throw new RuntimeException(e);
        }
        return dataClass.getDomain();
    }


    @Override
    public void deleteDomain(User user, Domain domain)
    {
        ExpDataClass dc = ExperimentService.get().getDataClass(domain.getTypeURI());
        if (dc == null)
            throw new NotFoundException("DataClass not found: " + domain.getTypeURI());

        dc.delete(user);
    }

    @Override
    public TableInfo getTableInfo(User user, Container container, String name)
    {
        UserSchema schema = new DataClassUserSchema(container, user);
        return schema.getTable(name);
    }

    @Override
    public void invalidate(Domain domain)
    {
        super.invalidate(domain);

        ExpDataClass dc = ExperimentService.get().getDataClass(domain.getTypeURI());
        if (dc != null && dc.getDomain() != null && dc.getDomain().getStorageTableName() != null)
            ExperimentServiceImpl.get().indexDataClass(dc);
    }

}
