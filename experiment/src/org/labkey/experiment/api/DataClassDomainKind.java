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
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
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
 * User: kevink
 * Date: 9/15/15
 */
public class DataClassDomainKind extends AbstractDomainKind
{
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
        return PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(containerUser.getContainer(), domain.getTypeURI(), true, true, false);
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties()
    {
        return BASE_PROPERTIES;
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return RESERVED_NAMES;
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices()
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
        return "expdataclass";
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
    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user)
    {
        String name = domain.getName();
        String description = domain.getDescription();
        List<GWTPropertyDescriptor> properties = (List<GWTPropertyDescriptor>)domain.getFields();

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
            dataClass = ExperimentService.get().createDataClass(container, user, name, description, properties, sampleSetId, nameExpression);
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
}
