package org.labkey.assay.plate;

import org.labkey.api.assay.AbstractTsvAssayProvider;
import org.labkey.api.assay.AssayDomainKind;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

import java.util.Collections;
import java.util.Set;

/**
 * Domain kind to store plate metadata properties
 */
public class AssayPlateDataDomainKind extends AssayDomainKind
{
    public static final String ASSAY_PLATE_DATA = ExpProtocol.ASSAY_DOMAIN_PREFIX + "AssayPlateData";
    public static final String KIND_NAME = "AssayPlateDataDomainKind";

    private static String XAR_SUBSTITUTION_SCHEMA_NAME = "SchemaName";
    private static String XAR_SUBSTITUTION_TABLE_NAME = "TableName";

    private static String DOMAIN_NAMESPACE_PREFIX_TEMPLATE = "%s-${SchemaName}";
    private static String DOMAIN_LSID_TEMPLATE = "${FolderLSIDBase}:${TableName}";

    public AssayPlateDataDomainKind()
    {
        super(ASSAY_PLATE_DATA);
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
        return Collections.singleton(new PropertyStorageSpec("Lsid", JdbcType.VARCHAR, 300).setNullable(false));
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
        return getDomainURI(schemaName, tableName, ASSAY_PLATE_DATA, c, u);
    }

    public static String getDomainURI(String schemaName, String tableName, String namespacePrefix, Container c, User u)
    {
        try
        {
            XarContext xc = new XarContext("Domains", c, u);
            xc.addSubstitution(XAR_SUBSTITUTION_SCHEMA_NAME, schemaName);
            xc.addSubstitution(XAR_SUBSTITUTION_TABLE_NAME, PageFlowUtil.encode(tableName));

            String template = String.format(DOMAIN_NAMESPACE_PREFIX_TEMPLATE, namespacePrefix);
            return LsidUtils.resolveLsidFromTemplate(DOMAIN_LSID_TEMPLATE, xc, template);
        }
        catch (XarFormatException xfe)
        {
            return null;
        }
    }
}
