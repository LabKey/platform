package org.labkey.api.query.column;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerDisplayColumn;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.WrappedColumnInfo;
import org.labkey.api.query.UserSchema;

public class ContainerIdColumnInfoTransformer implements ConceptURIColumnInfoTransformer
{
    @Override
    public @NotNull String getConceptURI()
    {
        return BuiltInColumnTypes.Container.conceptURI;
    }

    @Override
    public ColumnInfo apply(ColumnInfo column)
    {
        return applyMutable(WrappedColumnInfo.wrap(column));
    }

    @Override
    public MutableColumnInfo applyMutable(MutableColumnInfo column)
    {
        if (column.getJdbcType() != JdbcType.GUID && column.getJdbcType() != JdbcType.VARCHAR)
        {
            Logger.getLogger(UserIdColumnDecorator.class).warn("Column is not of type GUID: " + column.getName());
            return column;
        }

        UserSchema schema = column.getParentTable().getUserSchema();

        if (null == column.getFk() && null != schema && schema.getDbSchema().getScope().isLabKeyScope())
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