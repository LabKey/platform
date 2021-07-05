package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.List;
import java.util.stream.Collectors;

import static org.labkey.api.exp.api.ExperimentJSONConverter.ROW_ID;

public class ExpMaterialUniqueIdUnionTableInfo extends VirtualTable
{
    public static final String UNIQUE_ID_COL_NAME = "UniqueId";

    private final Container _container;
    private final User _user;
    private SQLFragment _unionSql;

    public ExpMaterialUniqueIdUnionTableInfo(Container container, User user)
    {
        super(ExperimentService.get().getSchema(), "MaterialUniqueId", ExperimentService.get().getTinfoMaterial().getUserSchema());
        _user = user;
        _container = container;
        init();
    }

    public void init()
    {
        UserSchema samplesUserSchema = QueryService.get().getUserSchema(_user, _container, SamplesSchema.SCHEMA_NAME);
        List<ExpSampleTypeImpl> sampleTypes = SampleTypeServiceImpl.get().getSampleTypes(_container, _user, true);

        String unionAll = "";
        SQLFragment query = new SQLFragment();

        for (ExpSampleTypeImpl type : sampleTypes)
        {
            TableInfo tableInfo = samplesUserSchema.getTable(type.getName());
            if (tableInfo == null)
                continue;
            ColumnInfo rowCol = tableInfo.getColumn(FieldKey.fromParts(ROW_ID));
            List<ColumnInfo> uniqueIdCols = tableInfo.getColumns().stream().filter(ColumnInfo::isUniqueIdField).collect(Collectors.toList());
            for (ColumnInfo col : uniqueIdCols)
            {
               query.append(unionAll);
               query.append("(SELECT ");
               query.append(rowCol.getValueSql("S"));
               query.append(", ");
               query.append(col.getValueSql("S")).append(" AS ").append(UNIQUE_ID_COL_NAME);
               query.append(" FROM ").append(tableInfo, "S");
               unionAll = ") UNION ALL\n";
            }
        }
        query.append(")");

        _unionSql = new SQLFragment();
        _unionSql.appendComment("<ExpMaterialUniqueIdUnionTableInfo>", getSchema().getSqlDialect());
        _unionSql.append(query);
        _unionSql.appendComment("</ExpMaterialUniqueIdUnionTableInfo>", getSchema().getSqlDialect());
        makeColumnInfos();
    }

    private void makeColumnInfos()
    {
        // TODO add inventory columns if available, otherwise add additional material columns
        addColumn(new BaseColumnInfo(ROW_ID, this, JdbcType.VARCHAR));
        addColumn(new BaseColumnInfo(UNIQUE_ID_COL_NAME, this, JdbcType.VARCHAR));
    }

    @Override
    public String getSelectName()
    {
        return null;
    }


    @NotNull
    @Override
    public SQLFragment getFromSQL()
    {
        return _unionSql;
    }


    @Override
    public String toString()
    {
        return "Exp.Materials Unique Id UNION table";
    }
}
