package org.labkey.experiment.api.data;

import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AbstractMethodInfo;
import org.labkey.api.sql.LabKeySql;

public class ParentOfMethod extends AbstractMethodInfo
{
    public static final String NAME = "ExpParentOf";

    public ParentOfMethod()
    {
        super(JdbcType.BOOLEAN);
    }

    @Override
    public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
    {
        SQLFragment fieldKeyFrag = arguments[0];
        SQLFragment lsidFrag = arguments[1];
        int depth = 0;
        if (arguments.length > 2)
            depth = Integer.parseInt(arguments[2].getRawSQL());

        return LineageHelper.createInSQL(fieldKeyFrag, LabKeySql.unquoteString(lsidFrag.getRawSQL()), LineageHelper.createParentOfOptions(depth));
    }

}
