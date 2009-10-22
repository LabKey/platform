/*
 * Copyright (c) 2008 LabKey Corporation
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
    }

    protected abstract FieldKey getComparisonColumn();

    public GWTComparisonResult createComparisonResult()
            throws SQLException, IOException, ServletException
    {
        List<FieldKey> cols = new ArrayList<FieldKey>();
        CrosstabTableInfo table = (CrosstabTableInfo) getTable();
        for (CrosstabDimension dim : table.getSettings().getRowAxis().getDimensions())
        {
            cols.add(FieldKey.fromString(dim.getSourceColumn().getName().replace('/', '_')));
        }
        cols.add(getComparisonColumn());

        _columns = cols;

        StringBuilder sb = new StringBuilder();

        TSVGridWriter tsvWriter = getTsvWriter();
        try
        {
            tsvWriter.setCaptionRowVisible(false);
            tsvWriter.write(sb);

            StringTokenizer lines = new StringTokenizer(sb.toString(), "\n");
            int rowCount = lines.countTokens();
            
            List<CrosstabMember> members = table.getColMembers();

            boolean[][] hits = new boolean[members.size()][];
            for (int i = 0; i < members.size(); i++)
            {
                hits[i] = new boolean[rowCount];
            }

            int resultsIndex = 0;
            while (lines.hasMoreTokens())
            {
                String line = lines.nextToken();
                String[] values = line.split("\\t");
                for (int i = 0; i < members.size() && i + 1 < values.length ; i++)
                {
                    hits[i][resultsIndex] = !"".equals(values[i + table.getSettings().getRowAxis().getDimensions().size()].trim());
                }
                resultsIndex++;
            }

            return createComparisonResult(hits, table);
        }
        finally
        {
            tsvWriter.close();
        }
    }

    protected GWTComparisonResult createComparisonResult(boolean[][] hits, CrosstabTableInfo table)
    {
        List<CrosstabMember> members = table.getColMembers();
        GWTComparisonMember[] gwtMembers = new GWTComparisonMember[members.size()];
        for (int index = 0; index < members.size(); index++)
        {
            CrosstabMember member = members.get(index);
            GWTComparisonMember gwtMember = new GWTComparisonMember(member.getCaption(), hits[index]);
            gwtMembers[index] = gwtMember;
            if (member.getDimension().getUrl() != null)
            {
                gwtMembers[index].setUrl(member.getDimension().getMemberUrl(member));
            }
        }

        return new GWTComparisonResult(gwtMembers, new GWTComparisonGroup[0], hits[0].length, table.getSettings().getColumnAxis().getCaption());
    }
}
