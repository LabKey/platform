/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
import org.labkey.api.exp.PropertyDescriptor;

import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: brittp
* Date: Oct 25, 2007
* Time: 10:06:18 AM
*/
public class OORAwarePropertyForeignKey extends PropertyForeignKey
{
    private TableInfo _baseTable;
    private OORMetadata _metadata;

    private static class OORColumnGroup
    {
        private String _baseName;
        private PropertyDescriptor _displayPd;
        private PropertyDescriptor _indicatorPd;
        private PropertyDescriptor _numberPd;
        private PropertyDescriptor _inRangePd;

        public OORColumnGroup(String baseName, PropertyDescriptor displayPd, PropertyDescriptor indicatorPd,
                              PropertyDescriptor numberPd, PropertyDescriptor inRangePd)
        {
            _baseName = baseName;
            _displayPd = displayPd;
            _indicatorPd = indicatorPd;
            _numberPd = numberPd;
            _inRangePd = inRangePd;
        }

        public PropertyDescriptor getDisplayPd()
        {
            return _displayPd;
        }

        public PropertyDescriptor getIndicatorPd()
        {
            return _indicatorPd;
        }

        public PropertyDescriptor getNumberPd()
        {
            return _numberPd;
        }

        public PropertyDescriptor getInRangePd()
        {
            return _inRangePd;
        }

        public String getBaseName()
        {
            return _baseName;
        }
    }

    private static class OORMetadata
    {
        private List<OORColumnGroup> _oorGroups = new ArrayList<OORColumnGroup>();
        private List<PropertyDescriptor> _additionalDisplayPds = new ArrayList<PropertyDescriptor>();

        public PropertyDescriptor[] getDisplayProperties()
        {
            List<PropertyDescriptor> pds = new ArrayList<PropertyDescriptor>();
            pds.addAll(_additionalDisplayPds);
            for (OORColumnGroup group : _oorGroups)
            {
                pds.add(group.getDisplayPd());
                pds.add(group.getIndicatorPd());
                pds.add(group.getInRangePd());
                pds.add(group.getNumberPd());
            }
            return pds.toArray(new PropertyDescriptor[pds.size()]);
        }

        public PropertyDescriptor[] getDefaultHiddenProperties()
        {
            List<PropertyDescriptor> pds = new ArrayList<PropertyDescriptor>();
            for (OORColumnGroup group : _oorGroups)
            {
                pds.add(group.getIndicatorPd());
                pds.add(group.getInRangePd());
                pds.add(group.getNumberPd());
            }
            return pds.toArray(new PropertyDescriptor[pds.size()]);
        }

        public void add(String baseName, PropertyDescriptor displayPd, PropertyDescriptor indicatorPd,
                              PropertyDescriptor numberPd, PropertyDescriptor inRangePd)
        {
            _oorGroups.add(new OORColumnGroup(baseName, displayPd, indicatorPd, numberPd, inRangePd));
        }

        public void add(PropertyDescriptor nonOORPd)
        {
            _additionalDisplayPds.add(nonOORPd);
        }

        public OORColumnGroup getGroupByDisplayColumn(PropertyDescriptor pd)
        {
            for (OORColumnGroup group : _oorGroups)
            {
                if (pd.getName().equals(group.getDisplayPd().getName()))
                    return group;
            }
            return null;
        }

        public OORColumnGroup getGroupByInRangeColumn(PropertyDescriptor pd)
        {
            for (OORColumnGroup group : _oorGroups)
            {
                if (pd.getName().equals(group.getInRangePd().getName()))
                    return group;
            }
            return null;
        }
    }

    public PropertyDescriptor[] getDefaultHiddenProperties()
    {
        return _metadata.getDefaultHiddenProperties();
    }

    protected ColumnInfo getIndicatorColumn(String baseName, ColumnInfo colInfo)
    {
        return getNamedColumn(baseName, colInfo, OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX);
    }

    protected ColumnInfo getNumberColumn(String baseName, ColumnInfo colInfo)
    {
        return getNamedColumn(baseName, colInfo, OORDisplayColumnFactory.NUMBER_COLUMN_SUFFIX);
    }

    protected ColumnInfo getNamedColumn(String baseName, ColumnInfo colInfo, String suffix)
    {
        FieldKey thisFieldKey = FieldKey.fromString(colInfo.getName());
        List<FieldKey> keys = new ArrayList<FieldKey>();

        FieldKey oorIndicatorKey = new FieldKey(thisFieldKey.getParent(), baseName + suffix);
        keys.add(oorIndicatorKey);
        Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(colInfo.getParentTable(), keys);
        return cols.get(oorIndicatorKey);
    }


    private class LateBoundOORDisplayColumnFactory implements DisplayColumnFactory
    {
        private String _baseName;

        public LateBoundOORDisplayColumnFactory(String baseName)
        {
            _baseName = baseName;
        }

        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            ColumnInfo oorIndicator = getIndicatorColumn(_baseName, colInfo);
            return new OutOfRangeDisplayColumn(colInfo, oorIndicator);
        }
    }

    private class InRangeExprColumn extends ExprColumn
    {
        private String _baseName;

        public InRangeExprColumn(TableInfo parent, String columnName, String baseName)
        {
            super(parent, columnName, null, Types.INTEGER);
            _baseName = baseName;
        }

        public SQLFragment getValueSql(String tableAlias)
        {
            ColumnInfo indicatorCol = getIndicatorColumn(_baseName, this);
            ColumnInfo numberCol = getNumberColumn(_baseName, this);

            SQLFragment inRangeSQL = new SQLFragment("CASE WHEN (");
            inRangeSQL.append(indicatorCol.getValueSql(tableAlias));
            inRangeSQL.append(") IS NULL THEN (");
            inRangeSQL.append(numberCol.getValueSql(tableAlias));
            inRangeSQL.append(") ELSE NULL END");
            return inRangeSQL;
        }

        public void declareJoins(Map<String, SQLFragment> map)
        {
            ColumnInfo indicatorCol = getIndicatorColumn(_baseName, this);
            indicatorCol.declareJoins(map);
            ColumnInfo numberCol = getNumberColumn(_baseName, this);
            numberCol.declareJoins(map);
        }
    }

    public OORAwarePropertyForeignKey(PropertyDescriptor[] pds, TableInfo baseTable, QuerySchema schema)
    {
        super(getDisplayPds(pds).getDisplayProperties(), schema);
        _baseTable = baseTable;
        // TODO: it's a terrible hack that we have to call 'getDisplayPds' twice.
        // It's harmless, but it would be nice if we could refactor the code to avoid this.
        _metadata = getDisplayPds(pds);
    }

    protected ColumnInfo constructColumnInfo(ColumnInfo parent, String name, PropertyDescriptor pd)
    {
        OORColumnGroup group = _metadata.getGroupByDisplayColumn(pd);
        if (group != null)
        {
            ColumnInfo col = super.constructColumnInfo(parent, name, pd);
            col.setDisplayColumnFactory(new LateBoundOORDisplayColumnFactory(group.getBaseName()));
            return col;
        }

        group = _metadata.getGroupByInRangeColumn(pd);
        if (group != null)
        {
            ColumnInfo inRangeColumn = new InRangeExprColumn(_baseTable, name, group.getBaseName());
            inRangeColumn.setCaption(group.getInRangePd().getLabel());
            inRangeColumn.setFormatString(group.getNumberPd().getFormat());
            return inRangeColumn;
        }
        return super.constructColumnInfo(parent, name, pd);
    }


    public static OORMetadata getDisplayPds(PropertyDescriptor[] pds)
    {
        final Map<String, PropertyDescriptor> nameToPropertyMap = new HashMap<String, PropertyDescriptor>();
        for (PropertyDescriptor pd : pds)
            nameToPropertyMap.put(pd.getName(), pd);
        OORMetadata metadata = new OORMetadata();

        for (PropertyDescriptor displayPd : pds)
        {
            PropertyDescriptor oorIndicatorPd = nameToPropertyMap.get(displayPd.getName() + OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX);
            if (oorIndicatorPd != null)
            {
                PropertyDescriptor numberPd = displayPd.clone();
                numberPd.setName(displayPd.getName() + OORDisplayColumnFactory.NUMBER_COLUMN_SUFFIX);
                numberPd.setLabel(displayPd.getName() + " Number");

                PropertyDescriptor inRangePd = displayPd.clone();
                inRangePd.setName(displayPd.getName() + OORDisplayColumnFactory.IN_RANGE_COLUMN_SUFFIX);
                inRangePd.setLabel(displayPd.getName() + " In Range");
                metadata.add(displayPd.getName(), displayPd, oorIndicatorPd, numberPd, inRangePd);
            }
            else if (!(displayPd.getName().toLowerCase().endsWith(OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX.toLowerCase()) &&
                    nameToPropertyMap.containsKey(displayPd.getName().substring(0, displayPd.getName().length() - OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX.length()))))
            {
                metadata.add(displayPd);
            }
        }
        return metadata;
    }
}
