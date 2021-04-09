package org.labkey.api.query.column;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;


public class UserIdColumnInfoTransformer implements ConceptURIColumnInfoTransformer
{
    @Override
    public @NotNull String getConceptURI()
    {
        return BuiltInColumnTypes.USERID_CONCEPT_URI;
    }

    @Override
    public MutableColumnInfo apply(MutableColumnInfo column)
    {
        if (column.getJdbcType() != JdbcType.INTEGER)
        {
            Logger.getLogger(UserIdColumnInfoTransformer.class).error("Column is not of type INT: " + column.getName());
            return column;
        }

        UserSchema schema = column.getParentTable().getUserSchema();
        BuiltInColumnTypes builtin = BuiltInColumnTypes.findBuiltInType(column);

        if (null == column.getFk() && null != schema && schema.getDbSchema().getScope().isLabKeyScope())
            column.setFk(new UserIdQueryForeignKey(schema, builtin!=null));
        column.setDisplayColumnFactory(UserIdQueryForeignKey._factoryBlank);

        if (null != builtin)
        {
            column.setUserEditable(false);
            column.setShownInInsertView(false);
            column.setShownInUpdateView(false);
            column.setReadOnly(true);
        }
        return column;
    }
}