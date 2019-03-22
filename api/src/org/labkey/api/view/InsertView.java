/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.view;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableViewForm;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple single row insert view for a {@link DataRegion}, implemented as a basic HTML form.
 */
public class InsertView extends DataView
{
    private Map<String, Object> _initialValues = null;
    private String _focusId;

    public InsertView(DataRegion dataRegion, BindException errors)
    {
        super(dataRegion, errors);
    }

    public InsertView(DataRegion dataRegion, RenderContext context)
    {
        super(dataRegion, context);    
    }

    public InsertView(DataRegion dataRegion, TableViewForm form, BindException errors)
    {
        super(dataRegion, form, errors);
    }

    public InsertView(TableViewForm form, BindException errors)
    {
        super(form, errors);
    }


    public InsertView(List<ColumnInfo> cols, BindException errors)
    {
        super(new DataRegion(), errors);
        getDataRegion().setColumns(cols);
    }

    protected boolean isColumnIncluded(ColumnInfo col)
    {
        return col.isShownInInsertView();
    }

    public void setInitialValues(Map<String, Object> initialValues)
    {
        _initialValues = new HashMap<>(initialValues);
    }

    public Map<String, Object> getInitialValues()
    {
        return _initialValues;
    }

    public void setInitialValue(String inputName, Object value)
    {
        if (_initialValues == null)
            _initialValues = new HashMap<>();
        _initialValues.put(inputName, value);
    }

    protected void _renderDataRegion(RenderContext ctx, Writer out) throws IOException
    {
        TableInfo tableInfo = getTable();

        TableViewForm form = ctx.getForm();
        if (form == null)
        {
            form = new TableViewForm(tableInfo);
        }

        if (null == _initialValues)
        {
            Map<String, Object> initialValues = new HashMap<>();

            Domain domain = tableInfo.getDomain();

            if (null != domain)
            {
                Map<DomainProperty, Object> domainDefaults = DefaultValueService.get().getDefaultValues(ctx.getContainer(), domain);
                ColumnInfo column;
                for (Map.Entry<DomainProperty, Object> entry : domainDefaults.entrySet())
                {
                    column = tableInfo.getColumn(FieldKey.fromParts(entry.getKey().getName()));
                    if (null != column)
                        initialValues.put(column.getName(), entry.getValue());
                }
            }
            else
            {
                for (ColumnInfo col : tableInfo.getColumns())
                {
                    Object defaultValue = col.getDefaultValue();
                    if (defaultValue != null)
                        initialValues.put(col.getName(), defaultValue);
                }
            }
            if (!initialValues.isEmpty())
                _initialValues = initialValues;
        }

        if (null != _initialValues)
            form.setTypedValues(_initialValues, false);

        ctx.setForm(form);
        ctx.put("setFocusId", _focusId);

        ctx.setMode(DataRegion.MODE_INSERT);
        getDataRegion().render(ctx, out);
    }
    
    public void setFocusId(String focusId)
    {
        _focusId = focusId;
    }

    public String getFocusId()
    {
        return _focusId;
    }
}
