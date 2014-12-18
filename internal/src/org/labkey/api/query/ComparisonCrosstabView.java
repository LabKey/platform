/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

package org.labkey.api.query;

import org.labkey.api.data.*;
import org.labkey.api.gwt.client.model.GWTComparisonResult;
import org.labkey.api.gwt.client.model.GWTComparisonMember;
import org.labkey.api.gwt.client.model.GWTComparisonGroup;
import org.labkey.api.reports.ReportService;

import javax.servlet.ServletException;
import java.sql.SQLException;
import java.io.IOException;
import java.util.*;

/**
 * User: jeckels
 * Date: Feb 2, 2008
 */
public abstract class ComparisonCrosstabView extends CrosstabView
{
    public ComparisonCrosstabView(UserSchema schema)
    {
        super(schema);
        // Don't allow users to create R views or other reports, since they won't know how to create the special
        // context with the run list to be compared, etc
        setViewItemFilter(ReportService.EMPTY_ITEM_LIST);
    }

    protected abstract FieldKey getComparisonColumn();

    public GWTComparisonResult createComparisonResult()
            throws SQLException, IOException, ServletException
    {
        List<FieldKey> cols = new ArrayList<>();
        CrosstabTableInfo table = (CrosstabTableInfo) getTable();
        CrosstabDimension dimension = table.getSettings().getRowAxis().getDimensions().get(0);
        cols.add(FieldKey.fromString(dimension.getSourceColumn().getName().replace('/', '_')));
        cols.add(getComparisonColumn());

        _columns = cols;

        StringBuilder sb = new StringBuilder();

        try (TSVGridWriter tsvWriter = getTsvWriter())
        {
            tsvWriter.write(sb);

            StringTokenizer lines = new StringTokenizer(sb.toString(), "\n");
            int rowCount = lines.countTokens();

            List<CrosstabMember> members = table.getColMembers();

            boolean[][] hits = new boolean[members.size()][];
            for (int i = 0; i < members.size(); i++)
            {
                hits[i] = new boolean[rowCount];
            }

            String headers = lines.nextToken();

            int resultsIndex = 0;
            while (lines.hasMoreTokens())
            {
                String line = lines.nextToken();
                String[] values = line.split("\\t");
                for (int i = 0; i < members.size() && i + 1 < values.length; i++)
                {
                    hits[i][resultsIndex] = !"".equals(values[i + 1].trim());
                }
                resultsIndex++;
            }

            return createComparisonResult(hits, table);
        }
    }

    protected GWTComparisonResult createComparisonResult(boolean[][] hits, CrosstabTableInfo table)
    {
        CrosstabAxis colAxis = table.getSettings().getColumnAxis();
        List<CrosstabMember> members = table.getColMembers();
        GWTComparisonMember[] gwtMembers = new GWTComparisonMember[members.size()];
        for (int index = 0; index < members.size(); index++)
        {
            CrosstabMember member = members.get(index);
            GWTComparisonMember gwtMember = new GWTComparisonMember(member.getCaption(), hits[index]);
            gwtMembers[index] = gwtMember;

            CrosstabDimension dim = colAxis.getDimension(member.getDimensionFieldKey());
            if (dim != null && dim.getUrl() != null)
            {
                gwtMembers[index].setUrl(dim.getMemberUrl(member));
            }
        }

        return new GWTComparisonResult(gwtMembers, new GWTComparisonGroup[0], hits[0].length, table.getSettings().getColumnAxis().getCaption());
    }
}
