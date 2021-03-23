package org.labkey.api.query.column;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ContainerDisplayColumn;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.query.UserSchema;

public class ContainerIdColumnDecorator implements ConceptURIColumnDecorator
{
    @Override
    public @NotNull String getConceptURI()
    {
        return BuiltInColumnTypes.Container.conceptURI;
    }

    @Override
    public void apply(MutableColumnInfo column)
    {
        if (column.getJdbcType() != JdbcType.GUID && column.getJdbcType() != JdbcType.VARCHAR)
        {
            Logger.getLogger(UserIdColumnDecorator.class).warn("Column is not of type GUID: " + column.getName());
            return;
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
    }
}