package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.List;
import java.util.stream.Collectors;

public class FindMaterialByUniqueIdHelper
{
    public static final String UNIQUE_ID_COL_NAME = "UniqueId";
    public static final String NAME = "MaterialUniqueId";

    private final Container _container;
    private final User _user;
    private SQLFragment _unionSql;
    private int _numUniqueIdCols = 0;

    public FindMaterialByUniqueIdHelper(Container container, User user)
    {
        _user = user;
        _container = container;
        init();
    }

    public void init()
    {
        DbSchema dbSchema = ExperimentService.get().getSchema();
        SqlDialect dialect = dbSchema.getSqlDialect();
        UserSchema samplesUserSchema = QueryService.get().getUserSchema(_user, _container, SamplesSchema.SCHEMA_NAME);
        List<ExpSampleTypeImpl> sampleTypes = SampleTypeServiceImpl.get().getSampleTypes(_container, _user, true);

        String unionAll = "";
        SQLFragment query = new SQLFragment();

        for (ExpSampleTypeImpl type : sampleTypes)
        {
            TableInfo tableInfo = samplesUserSchema.getTable(type.getName());
            if (tableInfo == null)
                continue;
            List<ColumnInfo> uniqueIdCols = tableInfo.getColumns().stream().filter(ColumnInfo::isScannableField).collect(Collectors.toList());
            _numUniqueIdCols += uniqueIdCols.size();
            for (ColumnInfo col : uniqueIdCols)
            {
               query.append(unionAll);
               query.append("(SELECT RowId, ")
                       .append("CAST (").append(dialect.quoteIdentifier(col.getName())).append(" AS VARCHAR)")
                       .append(" AS ").append(UNIQUE_ID_COL_NAME);
               query.append(" FROM samples.").append(dialect.quoteIdentifier(tableInfo.getName()));
               unionAll = ") UNION ALL\n";
            }
        }
        query.append(")");

        _unionSql = new SQLFragment();
        _unionSql.appendComment("<ExpMaterialUniqueIdUnionTableInfo>", dialect);
        _unionSql.append(query);
        _unionSql.appendComment("</ExpMaterialUniqueIdUnionTableInfo>", dialect);
    }

    public int getNumUniqueIdCols()
    {
        return _numUniqueIdCols;
    }

    @NotNull
    public SQLFragment getSQL()
    {
        return _unionSql;
    }

}
