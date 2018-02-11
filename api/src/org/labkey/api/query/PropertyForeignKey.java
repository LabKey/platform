/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.User;
import org.labkey.api.util.StringExpression;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Used to create a table from a set of {@link org.labkey.api.exp.PropertyDescriptor} objects, which are exposed
 * as a lookup from the source column (which typically holds an ObjectId or ObjectURI value).
 */
public class PropertyForeignKey extends AbstractForeignKey implements PropertyColumnDecorator
{
    private static final Logger LOG = Logger.getLogger(PropertyForeignKey.class);

    Map<String, PropertyDescriptor> _pdMap;
    protected QuerySchema _schema;
    protected boolean _parentIsObjectId = false;

    private List<PropertyColumnDecorator> _decorators = new ArrayList<>();

    public PropertyForeignKey(Map<String, PropertyDescriptor> pds, QuerySchema schema)
    {
        _pdMap = pds;
        _schema = schema;
    }


    /**
     * Creates a virtual table with columns for each of the property descriptors.
     */
    public PropertyForeignKey(Iterable<PropertyDescriptor> pds, QuerySchema schema)
    {
        _pdMap = new TreeMap<>();
        for (PropertyDescriptor pd : pds)
        {
            _pdMap.put(pd.getName(), pd);
        }
        _schema = schema;
    }


    public PropertyForeignKey(Domain domain, QuerySchema schema)
    {
        this(listProperties(domain), schema);
    }


    
    private static List<PropertyDescriptor> listProperties(Domain domain)
    {
        List<PropertyDescriptor> result = new ArrayList<>();
        for (DomainProperty prop : domain.getProperties())
        {
            result.add(prop.getPropertyDescriptor());
        }
        return result;
    }


    public void setParentIsObjectId(boolean id)
    {
        _parentIsObjectId = id;
    }



    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        // make sure this FK is attached to an lsid not an objectid
        String parentName = null, valueSql = null;
        assert null != (parentName = parent.getFieldKey().getName().toLowerCase());
        assert null != (valueSql = parent.getValueSql("X").getSQL().toLowerCase());
        assert _parentIsObjectId || parentName.contains("uri") || parentName.contains("lsid") || valueSql.contains("lsid") || valueSql.contains("uri");
        assert _parentIsObjectId || parent.getSqlTypeInt() == Types.VARCHAR || parent.getSqlTypeInt() == -9; // NVARCHAR
        assert _parentIsObjectId || parent.getJdbcType() == JdbcType.VARCHAR;

        if (displayField == null)
            return null;
        PropertyDescriptor pd = new CaseInsensitiveHashMap<>(_pdMap).get(displayField);
        if (pd == null)
        {
            pd = resolvePropertyDescriptor(displayField);
        }
        if (pd == null)
            return null;

        return constructColumnInfo(parent, decideColumnName(parent, displayField, pd), pd);
    }


    protected FieldKey decideColumnName(ColumnInfo parent, String displayField, PropertyDescriptor pd)
    {
        return new FieldKey(parent.getFieldKey(), "$P" + pd.getPropertyId());
    }


    static public SQLFragment getMvIndicatorSQL()
    {
        return new SQLFragment("exp.ObjectProperty.MvIndicator");
    }


    protected ColumnInfo constructColumnInfo(ColumnInfo parent, FieldKey name, PropertyDescriptor pd)
    {
        ColumnInfo ret;
        if (parent == null)
        {
            // this happens from getLookupTableInfo()
            ret = new ColumnInfo(pd.getName());
            initColumn(_schema.getUser(), ret, pd);
        }
        else
        {
            ret = new PropertyColumn(pd, parent, _schema.getContainer(), _schema.getUser(), false);
            ((PropertyColumn)ret).setParentIsObjectId(_parentIsObjectId);
            ret.setFieldKey(name);
        }
        decorateColumn(ret, pd);
        return ret;
    }


    public void decorateColumn(ColumnInfo columnInfo, PropertyDescriptor pd)
    {
        for (PropertyColumnDecorator decorator : _decorators)
        {
            decorator.decorateColumn(columnInfo, pd);
        }
    }

    
    public TableInfo getLookupTableInfo()
    {
        VirtualTable ret = new VirtualTable(ExperimentService.get().getSchema(), null);
        for (Map.Entry<String, PropertyDescriptor> entry : _pdMap.entrySet())
        {
            ColumnInfo column = constructColumnInfo(null, new FieldKey(null,entry.getKey()), entry.getValue());
            if (column != null)
            {
                column.setParentTable(ret);
                if (ret.getColumn(column.getName()) == null)
                {
                    ret.addColumn(column);
                }
                else
                {
                    LOG.warn("Duplicate property name found with " + column.getName() + ", PropertyURI: " + entry.getValue().getPropertyURI());
                }
            }
        }
        return ret;
    }


    public void addDecorator(PropertyColumnDecorator decorator)
    {
        _decorators.add(decorator);
    }


    public StringExpression getURL(ColumnInfo parent)
    {
        return null;
    }


    /**
     * Override this method to allow properties which might not have been
     * known in advance, and in {@link #_pdMap}.
     */
    protected PropertyDescriptor resolvePropertyDescriptor(String name)
    {
        for (PropertyDescriptor propertyDescriptor : _pdMap.values())
        {
            if (propertyDescriptor.getName().replace(" ", "").equalsIgnoreCase(name.replace(" ", "")))
            {
                return propertyDescriptor;
            }
        }
        return null;
    }


    private void initColumn(User user, ColumnInfo column, PropertyDescriptor pd)
    {
        if (pd.getLabel() != null)
            column.setLabel(pd.getLabel());
        else
            column.setLabel(ColumnInfo.labelFromName(pd.getName()));
        if (pd.getFormat() != null)
            column.setFormat(pd.getFormat());
        column.setNullable(!pd.isRequired());
        column.setHidden(pd.isHidden());
        column.setURL(pd.getURL());
        column.setImportAliasesSet(pd.getImportAliasSet());
        column.setSqlTypeName(CoreSchema.getInstance().getSqlDialect().sqlTypeNameFromSqlType(pd.getPropertyType().getSqlType()));
        column.setDescription(pd.getDescription());
        column.setFk(new PdLookupForeignKey(user, pd, _schema.getContainer()));
    }
}
