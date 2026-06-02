/*
 * Copyright (c) 2020-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.experiment.api.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.util.ArrayList;
import java.util.List;

public class LineageHelper
{
    static @Nullable ExpRunItem getStart(String lsid)
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

        List<String> lsidsToInvestigate = new ArrayList<>();
        lsidsToInvestigate.add(start.getLSID());
        lsidsToInvestigate.addAll(svc.collectRunsToInvestigate(start, options));

        return svc.generateExperimentTreeSQLLsidSeeds(lsidsToInvestigate, options);
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
