/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.study.query;

import org.apache.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.study.StudyService;
import org.labkey.study.SampleManager;
import org.labkey.study.model.PrimaryType;
import org.labkey.study.model.SiteImpl;
import org.labkey.study.model.SpecimenTypeSummary;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Mar 14, 2012
 */
public abstract class BaseSpecimenPivotTable extends FilteredTable
{
    protected static final String AGGREGATE_DELIM = "::";
    protected static final String TYPE_DELIM = "-";

    protected static String getNormalName(String name)
    {
        return name.toLowerCase();
    }

    protected class LegalCaseInsensitiveMap
    {
        // Map of names to normalized and legalized names
        // If normalized name is duplicated, it is added to the Duplicated set
        private HashMap<String, String> _nameToNormalName = new HashMap<String, String>();
        private HashSet<String> _normalNameDuplicated = new HashSet<String>();

        public void putName(String key)
        {
            if (key != null && !_nameToNormalName.containsKey(key))
            {
                String legalName = ColumnInfo.legalNameFromName(key);
                String normalName = BaseSpecimenPivotTable.getNormalName(legalName);
                if (_nameToNormalName.containsValue(normalName))
                {
                    _normalNameDuplicated.add(normalName);
                }
                _nameToNormalName.put(key, normalName);

                if (!_typeNameIdMapWrapper.containsKey(key))
                {
                    int id = _typeNameIdMapWrapper.size() + 1;
                    _typeNameIdMapWrapper.put(key, String.valueOf(id));
                }
            }
        }

        public String getNormalName(String key)
        {
            return _nameToNormalName.get(key);
        }

        public boolean isDuplicated(String key)
        {
            return _normalNameDuplicated.contains(getNormalName(key));
        }

    }

    protected class NameLabelPair
    {
        public String _name;
        public String _label;

        public NameLabelPair(String name, String label)
        {
            _name = name;
            _label = label;
        }
    }

    // For assigning unique ids to type names, so if there are legal/lowercase name conflicts we can create
    // a unique id to ensure uniqueness in the queries we create
    private static final String CATEGORY_NAME = "TypeNameToUniqueIdPropertyMap";

    private class PropertyMapWrapper
    {
        private PropertyManager.PropertyMap _typeNameIdMapWritable = null;
        private Map<String, String> _typeNameIdMap = null;
        private Container _container = null;

        public PropertyMapWrapper(Container container)
        {
            _container = container;
            _typeNameIdMap = PropertyManager.getProperties(container, CATEGORY_NAME);
        }
        public boolean containsKey(String key)
        {
            if (null != _typeNameIdMapWritable)
                return _typeNameIdMapWritable.containsKey(key);
            return _typeNameIdMap.containsKey(key);
        }
        public String get(String key)
        {
            if (null != _typeNameIdMapWritable)
                return _typeNameIdMapWritable.get(key);
             return _typeNameIdMap.get(key);
        }
        public void put(String key, String value)
        {
            if (null == _typeNameIdMapWritable)
                _typeNameIdMapWritable = PropertyManager.getWritableProperties(_container, CATEGORY_NAME, true);
            _typeNameIdMapWritable.put(key, value);

        }
        public void save()
        {
            if (null != _typeNameIdMapWritable)
                PropertyManager.saveProperties(_typeNameIdMapWritable);
        }
        public int size()
        {
            if (null != _typeNameIdMapWritable)
                return _typeNameIdMapWritable.size();
            return _typeNameIdMap.size();
        }
    }

    private PropertyMapWrapper _typeNameIdMapWrapper = null;

    public BaseSpecimenPivotTable(final TableInfo tinfo, final StudyQuerySchema schema)
    {
        super(tinfo, schema.getContainer());

        Logger.getInstance(BaseSpecimenPivotTable.class).debug("creating specimen pivot\n" +
                "SCHEMA=" + schema.getName() + " " + schema.getClass().getSimpleName()+"@"+System.identityHashCode(schema) + "\n" +
                "TABLE=" + tinfo.getName() + " " + this.getClass().getSimpleName() + "@" + System.identityHashCode(this),
                new Throwable("stack trace")
        );

        addWrapColumn(_rootTable.getColumn(StudyService.get().getSubjectColumnName(getContainer())));
        addWrapColumn(_rootTable.getColumn(StudyService.get().getSubjectVisitColumnName(getContainer())));
        addWrapColumn(_rootTable.getColumn("Visit"));

        _typeNameIdMapWrapper = new PropertyMapWrapper(getContainer());
    }

    protected void saveTypeNameIdMap()
    {
        _typeNameIdMapWrapper.save();
    }

    protected ColumnInfo wrapPivotColumn(ColumnInfo col, String descriptionFormat, NameLabelPair ...parts)
    {
        // The parts._name should already be "Normal Legal Name" parts
        StringBuilder name = new StringBuilder();
        StringBuilder label = new StringBuilder();
        String delim = "";
        String labelDelim = "";
        String[] labelsForDescription = new String[parts.length];
        int i = 0;
        for (NameLabelPair part : parts)
        {
            if (part != null && part._name != null)
            {
                name.append(delim).append(part._name);
                label.append(labelDelim).append(part._label);
                labelsForDescription[i] = part._label;

                delim = "_";
                labelDelim = ":";
            }
            i += 1;
        }
        ColumnInfo colInfo = new AliasedColumn(this, name.toString(), col);       // make lower case
        colInfo.setLabel(label.toString());
        if (descriptionFormat != null)
            colInfo.setDescription(String.format(descriptionFormat, labelsForDescription));

        return addColumn(colInfo);
    }
    
    /**
     * Returns a map of primary type id's to labels
     */
    protected Map<Integer, NameLabelPair> getPrimaryTypeMap(Container container)
    {
        Map<Integer, NameLabelPair> typeMap = new HashMap<Integer, NameLabelPair>();
        LegalCaseInsensitiveMap legalMap = new LegalCaseInsensitiveMap();
        SpecimenTypeSummary summary = SampleManager.getInstance().getSpecimenTypeSummary(getContainer());
        List<? extends SpecimenTypeSummary.TypeCount> primaryTypes = summary.getPrimaryTypes();

        for (SpecimenTypeSummary.TypeCount type : primaryTypes)
        {
            if (type.getId() != null)
            {
                legalMap.putName(type.getLabel());
            }
        }

        for (SpecimenTypeSummary.TypeCount type : primaryTypes)
        {
            if (type.getId() != null)
                typeMap.put(type.getId(), new NameLabelPair(
                        getLabel(type.getLabel(), type.getId(), legalMap), type.getLabel()));
        }
        return typeMap;
    }

    /**
     * Returns a map of all primary types
     */
    protected Map<Integer, NameLabelPair> getAllPrimaryTypesMap(Container container) throws SQLException
    {
        Map<Integer, NameLabelPair> typeMap = new HashMap<Integer, NameLabelPair>();
        LegalCaseInsensitiveMap legalMap = new LegalCaseInsensitiveMap();
        PrimaryType[] primaryTypes = SampleManager.getInstance().getPrimaryTypes(container);

        for (PrimaryType type : primaryTypes)
        {
            legalMap.putName(type.getPrimaryType());
        }

        for (PrimaryType type : primaryTypes)
        {
            typeMap.put((int)type.getRowId(), new NameLabelPair(
                    getLabel(type.getPrimaryType(), (int)type.getRowId(), legalMap), type.getPrimaryType()));
        }
        return typeMap;
    }

    /**
     * Returns a map of derivative type id's to labels
     */
    protected Map<Integer, NameLabelPair> getDerivativeTypeMap(Container container)
    {
        Map<Integer, NameLabelPair> typeMap = new HashMap<Integer, NameLabelPair>();
        LegalCaseInsensitiveMap legalMap = new LegalCaseInsensitiveMap();
        SpecimenTypeSummary summary = SampleManager.getInstance().getSpecimenTypeSummary(getContainer());
        List<? extends SpecimenTypeSummary.TypeCount> types = summary.getDerivatives();

        for (SpecimenTypeSummary.TypeCount type : types)
        {
            if (type.getId() != null && type.getLabel() != null)
                legalMap.putName(type.getLabel());
        }

        for (SpecimenTypeSummary.TypeCount type : types)
        {
            if (type.getId() != null  && type.getLabel() != null)
                typeMap.put(type.getId(), new NameLabelPair(
                        getLabel(type.getLabel(), type.getId(), legalMap), type.getLabel()));
        }
        return typeMap;
    }

    /**
     * Returns a map of site id's to labels
     */
    protected Map<Integer, NameLabelPair> getSiteMap(Container container)
    {
        Map<Integer, NameLabelPair> siteMap = new HashMap<Integer, NameLabelPair>();
        LegalCaseInsensitiveMap legalMap = new LegalCaseInsensitiveMap();
        SiteImpl[] sites = SampleManager.getInstance().getSites(container);

        for (SiteImpl site : sites)
        {
            legalMap.putName(site.getLabel());
        }

        for (SiteImpl site : sites)
        {
            siteMap.put(site.getRowId(), new NameLabelPair(
                    getLabel(site.getLabel(), site.getRowId(), legalMap), site.getLabel()));
        }
        return siteMap;
    }

    /**
     * use the row id to uniquify the column name, else just return the name
     */
    private String getLabel(String label, int id, LegalCaseInsensitiveMap legalMap)
    {
        if (label != null && legalMap.isDuplicated(label))
        {
            String idString = _typeNameIdMapWrapper.get(label);
            int mappedId = (idString != null) ? Integer.valueOf(idString) : id;
            return String.format("%s(%s)", legalMap.getNormalName(label), mappedId);
        }
        else
            return label;
    }
}
