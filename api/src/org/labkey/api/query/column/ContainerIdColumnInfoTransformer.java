package org.labkey.api.query.column;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ContainerDisplayColumn;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.query.UserSchema;

public class ContainerIdColumnInfoTransformer implements ConceptURIColumnInfoTransformer
{
    @Override
    public @NotNull String getConceptURI()
    {
        return BuiltInColumnTypes.Container.conceptURI;
    }

    @Override
    public MutableColumnInfo apply(MutableColumnInfo column)
    {
        if (column.getJdbcType() != JdbcType.GUID && column.getJdbcType() != JdbcType.VARCHAR)
        {
            Logger.getLogger(UserIdColumnInfoTransformer.class).warn("Column is not of type GUID: " + column.getName());
            return column;
        }

        UserSchema schema = column.getParentTable().getUserSchema();

        // override SchemaForeignKey, but not explicit QFK
        if ((null == column.getFk() || column.getFk() instanceof BaseColumnInfo.SchemaForeignKey) && null != schema && schema.getDbSchema().getScope().isLabKeyScope())
            column.setFk(new ContainerForeignKey(schema));
        column.setDisplayColumnFactory(ContainerDisplayColumn.FACTORY);

        if (null != BuiltInColumnTypes.findBuiltInType(column))
        {
            column.setUserEditable(false);
            column.setShownInInsertView(false);
            column.setShownInUpdateView(false);
            column.setReadOnly(true);
        }
        return column;
    }

}