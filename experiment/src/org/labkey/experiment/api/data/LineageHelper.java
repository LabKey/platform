package org.labkey.experiment.api.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.util.List;

public class LineageHelper
{
    static ExpRunItem getStart(String lsid)
    {
        if (lsid == null)
            return null;

        ExperimentServiceImpl svc = ExperimentServiceImpl.get();
        ExpRunItem start = svc.getExpMaterial(lsid);
        if (start == null)
            start = svc.getExpData(lsid);

        if (start == null || svc.isUnknownMaterial(start))
            return null;

        return start;
    }

    static @Nullable SQLFragment createExperimentTreeSQLLsidSeeds(ExpRunItem start, ExpLineageOptions options)
    {
        if (start == null)
            return null;

        ExperimentServiceImpl svc = ExperimentServiceImpl.get();
        List<String> runsToInvestigate = svc.collectRunsToInvestigate(start, options);
        if (runsToInvestigate.isEmpty())
            return null;

        return svc.generateExperimentTreeSQLLsidSeeds(runsToInvestigate, options);
    }

    static ExpLineageOptions createChildOfOptions(int depth)
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setForLookup(true);
        options.setParents(false);
        options.setChildren(true);
        options.setDepth(depth);
        return options;
    }

    static ExpLineageOptions createParentOfOptions(int depth)
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setForLookup(true);
        options.setParents(true);
        options.setChildren(false);
        options.setDepth(depth);
        return options;
    }

    static SQLFragment createInSQL(SQLFragment fieldKeyFrag, String lsidStr, ExpLineageOptions options)
    {
        ExpRunItem start = getStart(lsidStr);
        SQLFragment tree = createExperimentTreeSQLLsidSeeds(start, options);

        if (tree == null)
            return new SQLFragment("(1 = 2)");

        SQLFragment sql = new SQLFragment();
        sql.append("(").append(fieldKeyFrag).append(") IN (");
        sql.append("SELECT ").append(getLsidColumn()).append(" FROM (");
        sql.append(tree);
        sql.append(") AS X)");

        return sql;
    }

    static String getLsidColumn()
    {
        return "lsid";
    }

}
