/*
 * Copyright (c) 2015 LabKey Corporation
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class and related classes are the hard-typed Java bean representation
 * of the JSON request.
 *
 * Ideally, we can use Jackson to auto-serialize these objects (fingers crossed)
 *
 * In any event, I want to be able to construct a VisualizationSQLGenerator w/o having
 * to resort to writing JSON.
 *
 * Created by matthew on 6/3/15.
 */
public class VisDataRequest
{
    boolean metaDataOnly=false;
    boolean joinToFirst=false;
    List<MeasureInfo> measures = new ArrayList<>();
    List<Measure> sorts = new ArrayList<>();
    Integer limit;
    String filterUrl;
    String filterQuery;
    List<Measure> groupBys;

    public boolean isMetaDataOnly()
    {
        return metaDataOnly;
    }

    public VisDataRequest setMetaDataOnly(boolean metaDataOnly)
    {
        this.metaDataOnly = metaDataOnly;
        return this;
    }

    public boolean isJoinToFirst()
    {
        return joinToFirst;
    }

    public VisDataRequest setJoinToFirst(boolean joinToFirst)
    {
        this.joinToFirst = joinToFirst;
        return this;
    }

    public List<MeasureInfo> getMeasures()
    {
        return measures;
    }

    public VisDataRequest setMeasures(List<MeasureInfo> measures)
    {
        this.measures = measures;
        return this;
    }

    public VisDataRequest addMeasure(MeasureInfo mi)
    {
        this.measures.add(mi);
        return this;
    }

    public VisDataRequest addAll(Collection<MeasureInfo> c)
    {
        this.measures.addAll(c);
        return this;
    }

    public List<Measure> getSorts()
    {
        return sorts;
    }

    public VisDataRequest setSorts(List<Measure> sorts)
    {
        this.sorts = sorts;
        return this;
    }

    public VisDataRequest addSort(Measure m)
    {
        this.sorts.add(m);
        return this;
    }

    public Integer getLimit()
    {
        return limit;
    }

    public VisDataRequest setLimit(Integer limit)
    {
        this.limit = limit;
        return this;
    }

    public String getFilterUrl()
    {
        return filterUrl;
    }

    public VisDataRequest setFilterUrl(String filterUrl)
    {
        this.filterUrl = filterUrl;
        return this;
    }

    public String getFilterQuery()
    {
        return filterQuery;
    }

    public VisDataRequest setFilterQuery(String filterQuery)
    {
        this.filterQuery = filterQuery;
        return this;
    }

    public List<Measure> getGroupBys()
    {
        return groupBys;
    }

    public VisDataRequest setGroupBys(List<Measure> groupBys)
    {
        this.groupBys = groupBys;
        return this;
    }

    public VisDataRequest addGroupBy(Measure m)
    {
        if (null == groupBys)
            groupBys = new ArrayList<>();
        groupBys.add(m);
        return this;
    }

    public static class MeasureInfo
    {
        Measure measure;
        Measure dimension;
        DateOptions dateOptions;
        List<String> filterArray = new ArrayList<>();

        // study specific
        String time = "DATE"; // "DATE" or "VISIT"

        public MeasureInfo()
        {
        }

        public MeasureInfo(Measure m)
        {
            this.measure = m;
        }

        public Measure getMeasure()
        {
            return measure;
        }

        public MeasureInfo setMeasure(Measure measure)
        {
            this.measure = measure;
            return this;
        }

        public Measure getDimension()
        {
            return dimension;
        }

        public MeasureInfo setDimension(Measure dimension)
        {
            this.dimension = dimension;
            return this;
        }

        public DateOptions getDateOptions()
        {
            return dateOptions;
        }

        public MeasureInfo setDateOptions(DateOptions dateOptions)
        {
            this.dateOptions = dateOptions;
            return this;
        }

        public String getTime()
        {
            return time;
        }

        public MeasureInfo setTime(String time)
        {
            this.time = time;
            return this;
        }

        public List<String> getFilterArray()
        {
            return filterArray;
        }

        public MeasureInfo setFilterArray(List<String> filterArray)
        {
            this.filterArray = filterArray;
            return this;
        }
    }

    public static class Measure
    {
        Boolean allowNullResults;
        String aggregate;
        String alias;
        Boolean inNotNullSet;
        String name;
        String nsvalues;
        String queryName;
        String axisName;
        Boolean requireLeftJoin;
        String schemaName;
        List<Object> values = new ArrayList<>();

        public Measure()
        {}

        public Measure(String schema, String query, String name)
        {
            this.schemaName = schema;
            this.queryName = query;
            this.name = name;
            this.alias = (schema + "_" + query + "_" + name).toLowerCase();
        }


        // study specific properties
        boolean isDemographic=false;        // study schema specific property

        /* these are returned by getMeasure() but not needed by VisualizationSQLGenerator
        String defaultScale;
        String description;
        List<Group> groups = new ArrayList<>();
        boolean hidden=false;
        int id;
        boolean isDimension=false;
        boolean isMeasure=false;
        boolean isRecommendedVariable;
        boolean isUserDefined=false;
        String label;
        String longlabel;
        Lookup lookup=null;
        String queryLabel;
        String queryType;
        String recommendedVariableGrouper;
        int sortOrder;
        String type;
        List<String> uniqueKeys;
        String variableType;
        String yAxis;
        */

        // detect when we have an empty object e.g. dimension:{}
        public boolean isEmpty()
        {
            return null==name && null==schemaName && null == queryName && null == alias;
        }

        public String getAggregate()
        {
            return aggregate;
        }

        public Measure setAggregate(String aggregate)
        {
            this.aggregate = aggregate;
            return this;
        }

        public String getAlias()
        {
            return alias;
        }

        public Measure setAlias(String alias)
        {
            this.alias = alias;
            return this;
        }

        public Boolean getAllowNullResults()
        {
            return allowNullResults;
        }

        public Measure setAllowNullResults(Boolean allowNullResults)
        {
            this.allowNullResults = allowNullResults;
            return this;
        }

        public String getName()
        {
            return name;
        }

        public Boolean getInNotNullSet()
        {
            return inNotNullSet;
        }

        public Measure setInNotNullSet(Boolean inNotNullSet)
        {
            this.inNotNullSet = inNotNullSet;
            return this;
        }

        public Measure setName(String name)
        {
            this.name = name;
            return this;
        }

        public String getQueryName()
        {
            return queryName;
        }

        public Measure setQueryName(String queryName)
        {
            this.queryName = queryName;
            return this;
        }

        public String getAxisName()
        {
            return axisName;
        }

        public Measure setAxisName(String axisName)
        {
            this.axisName = axisName;
            return this;
        }

        public String getSchemaName()
        {
            return schemaName;
        }

        public Boolean getRequireLeftJoin()
        {
            return requireLeftJoin;
        }

        public Measure setRequireLeftJoin(Boolean requireLeftJoin)
        {
            this.requireLeftJoin = requireLeftJoin;
            return this;
        }

        public Measure setSchemaName(String schemaName)
        {
            this.schemaName = schemaName;
            return this;
        }

        public boolean isDemographic()
        {
            return isDemographic;
        }

        public Measure setIsDemographic(boolean isDemographic)
        {
            this.isDemographic = isDemographic;
            return this;
        }

        // see QueryService.get().getNamedSet();
        public String getNsvalues()
        {
            return nsvalues;
        }

        public Measure setNsvalues(String nsvalues)
        {
            this.nsvalues = nsvalues;
            return this;
        }

        public List<Object> getValues()
        {
            return values;
        }

        public Measure setValues(List<Object> values)
        {
            this.values = values;
            return this;
        }
    }

/*
    public static class ConstantMeasure extends Measure
    {
        Object constantValue = null;
        JdbcType constantType = null;

        public ConstantMeasure(String alias, Object value, JdbcType type)
        {
            this.alias = alias;
            this.constantValue = value;
            this.constantType = type;
        }

        public Object getConstantValue()
        {
            return constantValue;
        }

        public void setConstantValue(Object constantValue)
        {
            this.constantValue = constantValue;
        }

        public JdbcType getConstantType()
        {
            return constantType;
        }

        public void setConstantType(JdbcType constantType)
        {
            this.constantType = constantType;
        }

        public String getSQLLiteral(SqlDialect d)
        {
            if (null == constantValue)
            {
                if (null == constantType)
                    return "NULL";
                else
                    return "CAST(NULL AS " + d.sqlTypeNameFromJdbcType(constantType);
            }
            else
                throw new UnsupportedOperationException();
        }
    }
*/


    public static class DateOptions
    {
        String interval;
        Measure dateCol;
        Measure zeroDateCol;
        String zeroDayVisitTag;
        boolean isZeroDayVisitTagSet=false;  // for json compatibility
        boolean useProtocolDay=true;
        String altQueryName;

        public String getInterval()
        {
            return interval;
        }

        public DateOptions setInterval(String interval)
        {
            this.interval = interval;
            return this;
        }

        public Measure getDateCol()
        {
            return dateCol;
        }

        public DateOptions setDateCol(Measure dateCol)
        {
            this.dateCol = dateCol;
            return this;
        }

        public Measure getZeroDateCol()
        {
            return zeroDateCol;
        }

        public DateOptions setZeroDateCol(Measure zeroDateCol)
        {
            this.zeroDateCol = zeroDateCol;
            return this;
        }

        public String getZeroDayVisitTag()
        {
            return zeroDayVisitTag;
        }

        public DateOptions setZeroDayVisitTag(String zeroDayVisitTag)
        {
            this.isZeroDayVisitTagSet = true;
            this.zeroDayVisitTag = zeroDayVisitTag;
            return this;
        }

        public boolean isZeroDayVisitTagSet()
        {
            return isZeroDayVisitTagSet;
        }

        public boolean isUseProtocolDay()
        {
            return useProtocolDay;
        }

        public DateOptions setUseProtocolDay(boolean useProtocolDay)
        {
            this.useProtocolDay = useProtocolDay;
            return this;
        }

        public String getAltQueryName()
        {
            return altQueryName;
        }

        public DateOptions setAltQueryName(String altQueryName)
        {
            this.altQueryName = altQueryName;
            return this;
        }
    }
}
