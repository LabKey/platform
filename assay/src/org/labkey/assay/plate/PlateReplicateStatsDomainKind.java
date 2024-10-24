package org.labkey.assay.plate;

import org.labkey.api.assay.AbstractTsvAssayProvider;
import org.labkey.api.assay.AssayDomainKind;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

import java.util.List;
import java.util.Set;

public class PlateReplicateStatsDomainKind extends AssayDomainKind
{
    public static final String NAME = "AssayPlateReplicateStats";
    public static final String ASSAY_PLATE_REPLICATE = ExpProtocol.ASSAY_DOMAIN_PREFIX + NAME;
    public static final String KIND_NAME = NAME + "DomainKind";

    // replicate stats columns suffixes
    public static final String REPLICATE_MEAN_SUFFIX = "_Mean";
    public static final String REPLICATE_STD_DEV_SUFFIX = "_Standard_Deviation";

    public PlateReplicateStatsDomainKind()
    {
        super(ASSAY_PLATE_REPLICATE);
    }

    public enum Column
    {
        Lsid,
        Run
    }

    @Override
    public String getKindName()
    {
        return KIND_NAME;
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain, User user)
    {
        return getAssayReservedPropertyNames();
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties(Domain domain)
    {
        return Set.of(
            new PropertyStorageSpec(Column.Lsid.name(), JdbcType.VARCHAR, 300).setNullable(false).setPrimaryKey(true),
            new PropertyStorageSpec(Column.Run.name(), JdbcType.INTEGER).setNullable(false)
        );
    }

    @Override
    public DbScope getScope()
    {
        return getSchema().getScope();
    }

    @Override
    public String getStorageSchemaName()
    {
        return AbstractTsvAssayProvider.ASSAY_SCHEMA_NAME;
    }

    private DbSchema getSchema()
    {
        return DbSchema.get(getStorageSchemaName(), getSchemaType());
    }

    @Override
    public DbSchemaType getSchemaType()
    {
        return DbSchemaType.Provisioned;
    }

    @Override
    public String generateDomainURI(String schemaName, String tableName, Container c, User u)
    {
        return getDomainURI(schemaName, tableName, ASSAY_PLATE_REPLICATE, c, u);
    }

    public static String getDomainURI(String schemaName, String tableName, String namespacePrefix, Container c, User u)
    {
        try
        {
            XarContext xc = new XarContext("Domains", c, u);
            xc.addSubstitution("SchemaName", schemaName);
            xc.addSubstitution("TableName", PageFlowUtil.encode(tableName));

            String template = String.format("%s-${SchemaName}", namespacePrefix);
            return LsidUtils.resolveLsidFromTemplate("${FolderLSIDBase}:${TableName}", xc, template);
        }
        catch (XarFormatException xfe)
        {
            return null;
        }
    }

    public static Lsid generateReplicateLsid(Container container, ExpRun run, PlateSet plateSet, WellGroup wellGroup)
    {
        if (plateSet != null)
        {
            String object = String.format("%d-PS-%d-WG-%s", run.getRowId(), plateSet.getRowId(), wellGroup.getName());
            return new Lsid("Replicate", "Folder-" + container.getRowId(), object);
        }
        return null;
    }

    /**
     * Returns the list of replicate stats field names corresponding
     * to the specified measure name.
     */
    public static List<String> getStatsFieldNames(String colName)
    {
        return List.of(colName + REPLICATE_MEAN_SUFFIX, colName + REPLICATE_STD_DEV_SUFFIX);
    }
}
