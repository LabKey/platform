package org.labkey.experiment.api;

import org.labkey.api.exp.query.ExpInputTable;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.UserSchema;

import java.util.Collection;

/**
 * User: jeckels
 * Date: Jan 5, 2010
 */
public abstract class ExpInputTableImpl<C extends Enum> extends ExpTableImpl<C> implements ExpInputTable<C>
{
    private ExpRun _run;
    private ExpProtocol.ApplicationType _type;

    public ExpInputTableImpl(String name, TableInfo rootTable, UserSchema schema)
    {
        super(name, rootTable, schema);
    }

    public void setRun(ExpRun run, ExpProtocol.ApplicationType type)
    {
        _run = run;
        _type = type;
        applyFilters();
    }

    private void applyFilters()
    {
        clearConditions("FolderRunType");

        Collection<String> ids = getContainerFilter().getIds(getContainer());
        if (ids != null || _run != null || _type != null)
        {
            SQLFragment sqlFragment = new SQLFragment("(SELECT er.Container FROM " +
                ExperimentServiceImpl.get().getTinfoExperimentRun() + " er, " +
                ExperimentServiceImpl.get().getTinfoProtocolApplication() + " pa WHERE er.RowId = pa.RunId AND " +
                "pa.RowId = " + "TargetApplicationId");
            if (_run != null)
            {
                sqlFragment.append(" AND er.RowId = " + _run.getRowId());
            }
            if (_type != null)
            {
                sqlFragment.append(" AND pa.CpasType = ?");
                sqlFragment.add(_type.toString());
            }
            if (ids != null)
            {
                sqlFragment.append(") IN (");
                String separator = "";
                for (String id : ids)
                {
                    sqlFragment.append(separator);
                    separator = ",";
                    sqlFragment.append("'");
                    sqlFragment.append(id);
                    sqlFragment.append("'");
                }
                sqlFragment.append(")");
            }
            else
            {
                sqlFragment.append(") IS NOT NULL");
            }

            addCondition(sqlFragment, "FolderRunType");
        }
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        applyFilters();
    }
}
