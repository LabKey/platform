package org.labkey.api.assay;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ExpQCFlag;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;

import java.util.List;

public class AssayDefaultFlagHandler implements AssayFlagHandler
{
    @Override
    public ColumnInfo createFlagColumn(TableInfo parent, String schemaName, boolean editable)
    {
        return new AssayQCFlagColumn(parent, schemaName, editable);
    }

    @Override
    public ColumnInfo createQCEnabledColumn(TableInfo parent, String schemaName)
    {
        ColumnInfo qcEnabled = new ExprColumn(parent, "QCFlagsEnabled", AssayQCFlagColumn.createSQLFragment(parent.getSqlDialect(), "Enabled"), JdbcType.VARCHAR);
        qcEnabled.setLabel("QC Flags Enabled State");
        qcEnabled.setHidden(true);

        return qcEnabled;
    }

    @Override
    public <FlagType extends ExpQCFlag> void saveFlag(FlagType flag, User user)
    {
        Table.insert(user, ExperimentService.get().getTinfoAssayQCFlag(), flag);
    }

    @Override
    public int deleteFlags(int runId, User user)
    {
        return Table.delete(ExperimentService.get().getTinfoAssayQCFlag(), new SimpleFilter(FieldKey.fromParts("runId"), runId));
    }

    @Override
    public <FlagType extends ExpQCFlag> List<FlagType> getFlags(int runId, Class<FlagType> cls)
    {
        return new TableSelector(ExperimentService.get().getTinfoAssayQCFlag(), new SimpleFilter(FieldKey.fromParts("runId"), runId), null).getArrayList(cls);
    }
}
