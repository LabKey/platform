package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.inventory.InventoryService;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.List;
import java.util.stream.Collectors;

import static org.labkey.api.exp.api.ExperimentJSONConverter.ROW_ID;

public class ExpMaterialUniqueIdUnionTableInfo extends VirtualTable
{
    public static final String UNIQUE_ID_COL_NAME = "UniqueId";
    public static final String NAME = "MaterialUniqueId";

    private final Container _container;
    private final User _user;
    private SQLFragment _unionSql;

    public ExpMaterialUniqueIdUnionTableInfo(Container container, User user)
    {
        super(ExperimentService.get().getSchema(), NAME, ExperimentService.get().getTinfoMaterial().getUserSchema());
        _user = user;
        _container = container;
        init();
    }

    public void init()
    {
        UserSchema samplesUserSchema = QueryService.get().getUserSchema(_user, _container, SamplesSchema.SCHEMA_NAME);
        List<ExpSampleTypeImpl> sampleTypes = SampleTypeServiceImpl.get().getSampleTypes(_container, _user, true);
        SqlDialect dialect = getSchema().getSqlDialect();
        String unionAll = "";
        SQLFragment query = new SQLFragment();

        for (ExpSampleTypeImpl type : sampleTypes)
        {
            TableInfo tableInfo = samplesUserSchema.getTable(type.getName());
            if (tableInfo == null)
                continue;
            List<ColumnInfo> uniqueIdCols = tableInfo.getColumns().stream().filter(ColumnInfo::isUniqueIdField).collect(Collectors.toList());
            for (ColumnInfo col : uniqueIdCols)
            {
               query.append(unionAll);
               query.append("(SELECT RowId, RowId.Name, RowId.SampleSet.Name as SampleSet, RowId.IsAliquot, RowId.Created, RowId.CreatedBy ");
               if (InventoryService.isFreezerManagementEnabled(_container)) {
                   query.append(", RowId.SampleSet.LabelColor, RowId.StoredAmount, RowId.Units, RowId.FreezeThawCount, RowId.StorageStatus, RowId.CheckedOutBy, RowId.StorageLocation, RowId.StorageRow, RowId.StorageCol");
               }
               query.append(", ");
               query.append(col.getName()).append(" AS ").append(UNIQUE_ID_COL_NAME);
               query.append(" FROM samples.").append(dialect.quoteIdentifier(tableInfo.getName()));
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
