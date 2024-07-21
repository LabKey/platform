package org.labkey.assay.plate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.property.BaseAbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;
import org.labkey.assay.plate.query.AmountUnitsTable;
import org.labkey.assay.plate.query.ConcentrationUnitsTable;
import org.labkey.assay.plate.query.PlateSchema;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class PlateMetadataDomainKind extends BaseAbstractDomainKind
{
    public static final String KIND_NAME = "PlateMetadata";
    public static final String DOMAiN_NAME = "PlateMetadataDomain";
    private static final Set<String> RESERVED_NAMES;
    private static final Set<PropertyStorageSpec.Index> INDEXES;
    private static final Set<PropertyStorageSpec> REQUIRED_PROPS;
    private static final String PROVISIONED_SCHEMA_NAME = "assaywell";

    public enum Column
    {
        Amount,
        AmountUnits,
        Col,
        Concentration,
        ConcentrationUnits,
        Container,
        Created,
        CreatedBy,
        Dilution,
        Lsid,
        Modified,
        ModifiedBy,
        PlateId,
        Position,
        Row,
        RowId,
        SampleId,
        Type,
        Value,
        WellGroup
    }

    static
    {
        RESERVED_NAMES = new CaseInsensitiveHashSet(Arrays.stream(Column.values()).map(Enum::name).toList());
        INDEXES = Set.of(new PropertyStorageSpec.Index(true, Column.Lsid.name()));
        REQUIRED_PROPS = Set.of(
                new PropertyStorageSpec(Column.Amount.name(), JdbcType.DOUBLE),
                new PropertyStorageSpec(Column.AmountUnits.name(), JdbcType.VARCHAR, 60),
                new PropertyStorageSpec(Column.Concentration.name(), JdbcType.DOUBLE),
                new PropertyStorageSpec(Column.ConcentrationUnits.name(), JdbcType.VARCHAR, 60)
        );
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices(Domain domain)
    {
        return INDEXES;
    }

    @Override
    public String getKindName()
    {
        return KIND_NAME;
    }

    public static String generateDomainURI(@NotNull Container container)
    {
        return new Lsid(KIND_NAME + ".Folder-" + container.getRowId(), DOMAiN_NAME).toString();
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties(Domain domain)
    {
        return Set.of(
                new PropertyStorageSpec(Column.Lsid.name(), JdbcType.VARCHAR, 200).setNullable(false),
                new PropertyStorageSpec(Column.Amount.name(), JdbcType.DOUBLE),
                new PropertyStorageSpec(Column.AmountUnits.name(), JdbcType.VARCHAR, 60),
                new PropertyStorageSpec(Column.Concentration.name(), JdbcType.DOUBLE),
                new PropertyStorageSpec(Column.ConcentrationUnits.name(), JdbcType.VARCHAR, 60)
        );
    }


    @Override
    public Set<PropertyStorageSpec.ForeignKey> getPropertyForeignKeys(Container container)
    {
        return Set.of(
                new PropertyStorageSpec.ForeignKey(Column.AmountUnits.name(), PlateSchema.SCHEMA_NAME, AmountUnitsTable.NAME, "Value", null, false),
                new PropertyStorageSpec.ForeignKey(Column.ConcentrationUnits.name(), PlateSchema.SCHEMA_NAME, ConcentrationUnitsTable.NAME, "Value", null, false)
        );
    }

    @Override
    public Domain createDomain(GWTDomain<GWTPropertyDescriptor> domain, JSONObject arguments, Container container, User user, @Nullable TemplateInfo templateInfo)
    {
        try
        {
            String domainURI = generateDomainURI(container);
            Domain metadataDomain = PropertyService.get().createDomain(container, domainURI, domain.getName(), templateInfo);
            //ensureDomainProperties(metadataDomain, container);
            metadataDomain.save(user);

            return PropertyService.get().getDomain(container, domainURI);
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    private void ensureDomainProperties(Domain domain, Container container)
    {
        String typeUri = domain.getTypeURI();
        Map<String, PropertyStorageSpec.ForeignKey> foreignKeyMap = new CaseInsensitiveHashMap<>();

        for (PropertyStorageSpec.ForeignKey fk : getPropertyForeignKeys(container))
        {
            foreignKeyMap.put(fk.getColumnName(), fk);
        }

        for (PropertyStorageSpec spec : REQUIRED_PROPS)
        {
            DomainProperty prop = domain.addProperty();

            prop.setName(spec.getName());
            prop.setPropertyURI(typeUri + "#" + spec.getName());
            prop.setRangeURI(spec.getTypeURI());
            prop.setScale(spec.getSize());
            prop.setRequired(!spec.isNullable());

            if (foreignKeyMap.containsKey(spec.getName()))
            {
                PropertyStorageSpec.ForeignKey fk = foreignKeyMap.get(spec.getName());
                Lookup lookup = new Lookup(null, fk.getSchemaName(), fk.getTableName());

                prop.setLookup(lookup);
            }
        }
    }

    @Override
    public String getStorageSchemaName()
    {
        return PROVISIONED_SCHEMA_NAME;
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(PROVISIONED_SCHEMA_NAME, DbSchemaType.Provisioned);
    }

    @Override
    public DbScope getScope()
    {
        return getSchema().getScope();
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return container.hasPermission(user, AdminPermission.class);
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
    public @Nullable ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        return null;
    }

    @Override
    public @Nullable ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        return null;
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain, User user)
    {
        return RESERVED_NAMES;
    }

    @Override
    public @Nullable Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return getKindName().equals(lsid.getNamespacePrefix()) ? Priority.MEDIUM : null;
    }
}
