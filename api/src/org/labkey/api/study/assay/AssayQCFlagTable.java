package org.labkey.api.study.assay;

import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;

/**
 * User: jeckels
 * Date: Dec 12, 2011
 */
public class AssayQCFlagTable extends FilteredTable
{
    private final UserSchema _schema;

    public AssayQCFlagTable(UserSchema schema)
    {
        this(schema, null);
    }

    public AssayQCFlagTable(UserSchema schema, final ExpProtocol assayProtocol)
    {
        super(ExperimentService.get().getTinfoAssayQCFlag(), schema.getContainer());
        _schema = schema;

        wrapAllColumns(true);
        getColumn("RunId").setLabel("Run");
        if (assayProtocol == null)
        {
            getColumn("RunId").setFk(new ExpSchema(_schema.getUser(), _schema.getContainer()).getRunIdForeignKey());
        }
        else
        {
            getColumn("RunId").setFk(new LookupForeignKey("RowId")
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    return AssayService.get().createSchema(_schema.getUser(), _schema.getContainer()).getTable(AssayService.get().getRunsTableName(assayProtocol));
                }
            });
            SQLFragment protocolSQL = new SQLFragment("RunId IN (SELECT er.RowId FROM ");
            protocolSQL.append(ExperimentService.get().getTinfoExperimentRun(), "er");
            protocolSQL.append(" WHERE ProtocolLSID = ?)");
            protocolSQL.add(assayProtocol.getLSID());
            addCondition(protocolSQL, "ProtocolLSID");
        }
        getColumn("CreatedBy").setFk(new UserIdForeignKey());
        getColumn("ModifiedBy").setFk(new UserIdForeignKey());
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        clearConditions("Container");
        SQLFragment sql = new SQLFragment("RunId IN (SELECT er.RowId FROM ");
        sql.append(ExperimentService.get().getTinfoExperimentRun(), "er");
        sql.append(" WHERE ");
        sql.append(filter.getSQLFragment(getSchema(), "er.Container", getContainer()));
        sql.append(")");

        addCondition(sql, "Container");
    }

    @Override
    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new UpdateService(this);
    }

    public class UpdateService extends DefaultQueryUpdateService
    {
        public UpdateService(TableInfo queryTable)
        {
            super(queryTable, ExperimentService.get().getTinfoAssayQCFlag());
        }
    }
}
