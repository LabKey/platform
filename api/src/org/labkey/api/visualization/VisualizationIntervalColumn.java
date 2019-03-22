/*
 * Copyright (c) 2011-2014 LabKey Corporation
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
package org.labkey.api.visualization;

import org.labkey.api.data.ColumnInfo;

/**
 * User: brittp
 * Date: Feb 4, 2011 4:52:11 PM
 */
public class VisualizationIntervalColumn
{
    private enum Interval
    {
        DAY("Days") {
            @Override
            public String getSQL(VisualizationSourceColumn startCol, VisualizationSourceColumn endCol, boolean isVisitTagQuery)
            {
                if (isVisitTagQuery)
                    return "(" + endCol.getSQLOther() + " - " + (startCol == null ? "0" : startCol.getSQLAlias()) + ")";
                else
                    return "TIMESTAMPDIFF(SQL_TSI_DAY, " + startCol.getSQLAlias() + ", " + endCol.getSQLOther() + ")";
            }},
        WEEK("Weeks") {
            @Override
            public String getSQL(VisualizationSourceColumn startCol, VisualizationSourceColumn endCol, boolean isVisitTagQuery)
            {
                return "CAST(FLOOR((" + Interval.DAY.getSQL(startCol, endCol, isVisitTagQuery) + ")/7) AS Integer)";
            }},
        MONTH("Months") {
            @Override
            public String getSQL(VisualizationSourceColumn startCol, VisualizationSourceColumn endCol, boolean isVisitTagQuery)
            {
                if (isVisitTagQuery)
                    return "CAST(FLOOR((" + Interval.DAY.getSQL(startCol, endCol, isVisitTagQuery) + ")/(365.25/12)) AS Integer)";
                else
                    return "AGE(" + startCol.getSQLAlias() + ", " + endCol.getSQLOther() + ", SQL_TSI_MONTH)";
            }},
        YEAR("Years") {
            @Override
            public String getSQL(VisualizationSourceColumn startCol, VisualizationSourceColumn endCol, boolean isVisitTagQuery)
            {
                if (isVisitTagQuery)
                    return "CAST(FLOOR((" + Interval.DAY.getSQL(startCol, endCol, isVisitTagQuery) + ")/365.25) AS Integer)";
                else
                    return "AGE(" + startCol.getSQLAlias() + ", " + endCol.getSQLOther() + ", SQL_TSI_YEAR)";
            }};

        private String _label;
        Interval(String label)
        {
            _label = label;
        }

        public String getLabel()
        {
            return _label;
        }

        public abstract String getSQL(VisualizationSourceColumn startCol, VisualizationSourceColumn endCol, boolean isVisitTagQuery);
    }

    private VisualizationSourceColumn _startCol;
    private VisualizationSourceColumn _endCol;
    private Interval _interval;
    private boolean _isVisitTagQuery;

    public VisualizationIntervalColumn(VisualizationSourceColumn startCol, VisualizationSourceColumn endCol, String interval, boolean isVisitTagQuery)
    {
        _startCol = startCol;
        _endCol = endCol;
        _interval = Interval.valueOf(interval.toUpperCase());
        _isVisitTagQuery = isVisitTagQuery;
    }

    public VisualizationSourceColumn getEndCol()
    {
        return _endCol;
    }

    public String getLabel()
    {
        return _interval.getLabel();
    }

    public Interval getInterval()
    {
        return _interval;
    }

    public VisualizationSourceColumn getStartCol()
    {
        return _startCol;
    }

    public String getSQL()
    {
        return _interval.getSQL(_startCol, _endCol, _isVisitTagQuery);
    }


    public String getSQLAlias(int intervals)
    {
        return "\"" + ((intervals > 1) ? getFullAlias() : getSimpleAlias()) + "\"";
    }


    public String getSimpleAlias()
    {
        return _interval.getLabel();
    }

    public String getFullAlias()
    {
        return ColumnInfo.legalNameFromName(_endCol.getSchemaName() + "_" + _endCol.getQueryName() + "_" + _endCol.getOriginalName() + "_" + getSimpleAlias());
    }
}
