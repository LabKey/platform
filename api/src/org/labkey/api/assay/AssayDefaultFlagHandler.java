package org.labkey.api.assay;

import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ExpQCFlag;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AssayDefaultFlagHandler implements AssayFlagHandler
{
    @Override
    public BaseColumnInfo createFlagColumn(ExpProtocol protocol, TableInfo parent, String schemaName, boolean editable)
    {
        return new AssayQCFlagColumn(parent, schemaName, editable);
    }

    @Override
    public BaseColumnInfo createQCEnabledColumn(ExpProtocol protocol, TableInfo parent, String schemaName)
    {
        ExprColumn qcEnabled = new ExprColumn(parent, "QCFlagsEnabled", AssayQCFlagColumn.createSQLFragment(parent.getSqlDialect(), "Enabled"), JdbcType.VARCHAR);
        qcEnabled.setLabel("QC Flags Enabled State");
        qcEnabled.setHidden(true);

        return qcEnabled;
    }

    @Override
    public <FlagType extends ExpQCFlag> void saveFlag(Container container, User user, FlagType flag)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, container, new SchemaKey(null, "exp"));
        TableInfo tableInfo = schema.getTable(ExpSchema.TableType.QCFlags.name());
        if (tableInfo != null)
        {
            try
            {
                QueryUpdateService qus = tableInfo.getUpdateService();
                ObjectFactory<FlagType> f = ObjectFactory.Registry.getFactory((Class<FlagType>)flag.getClass());

                Map<String, Object> row = f.toMap(flag, null);
                BatchValidationException errors = new BatchValidationException();
                qus.insertRows(user, container, Collections.singletonList(row), errors, null, null);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public int deleteFlags(Container container, User user, int runId)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, container, new SchemaKey(null, "exp"));
        TableInfo tableInfo = schema.getTable(ExpSchema.TableType.QCFlags.name());
        if (tableInfo != null)
        {
            try
            {
                List<ExpQCFlag> flags = new TableSelector(ExperimentService.get().getTinfoAssayQCFlag(), new SimpleFilter(FieldKey.fromParts("runId"), runId), null).getArrayList(ExpQCFlag.class);
                List<Map<String, Object>> rows = flags.stream().
                        map(f -> Collections.singletonMap("RowId", (Object)f.getRowId())).
                        collect(Collectors.toList());

                if (!rows.isEmpty())
                {
                    QueryUpdateService qus = tableInfo.getUpdateService();
                    return qus.deleteRows(user, container, rows, null, null).size();
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        return 0;
    }

    @Override
    public <FlagType extends ExpQCFlag> List<FlagType> getFlags(int runId, Class<FlagType> cls)
    {
        return new TableSelector(ExperimentService.get().getTinfoAssayQCFlag(), new SimpleFilter(FieldKey.fromParts("runId"), runId), null).getArrayList(cls);
    }
}
