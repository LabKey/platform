/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.api.laboratory.assay;

import com.google.gwt.codegen.server.StringGenerator;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 12/9/12
 * Time: 8:44 PM
 */
abstract public class PivotingImportMethod extends DefaultAssayImportMethod
{
    protected AssayImportMethod _importMethod;
    protected String _pivotField;
    protected String _valueField;
    protected TableInfo _sourceTable = null;
    protected String _sourceColumn = null;

    public PivotingImportMethod(AssayImportMethod method, String pivotField, String valueField, TableInfo sourceTable, String sourceColumn)
    {
        super(method.getProviderName());
        _importMethod = method;
        _pivotField = pivotField;
        _valueField = valueField;
        _sourceTable = sourceTable;
        _sourceColumn = sourceColumn;
    }

    @Override
    public String getName()
    {
        return "pivotedBy" + _pivotField;
    }

    @Override
    public String getLabel()
    {
        return "Pivoted By " + _pivotField;
    }

    protected DomainProperty getValueColumn(ExpProtocol protocol)
    {
        Domain results = getAssayProvider().getResultsDomain(protocol);
        for (DomainProperty dp : results.getProperties())
        {
            if (dp.getName().equalsIgnoreCase(_valueField))
                return dp;
        }
        throw new IllegalArgumentException("Column not present in protocol " + protocol.getName() + ": " + _valueField);
    }

    protected DomainProperty getPivotColumn(ExpProtocol protocol)
    {
        Domain results = getAssayProvider().getResultsDomain(protocol);
        for (DomainProperty dp : results.getProperties())
        {
            if (dp.getName().equalsIgnoreCase(_pivotField))
                return dp;
        }
        throw new IllegalArgumentException("Pivot column not present in protocol " + protocol.getName() + ": " + _pivotField);
    }

    @Override
    public List<String> getImportColumns(ViewContext ctx, ExpProtocol protocol)
    {
        List<String> ret = new ArrayList<>();

        List<String> columns = super.getImportColumns(ctx, protocol);
        DomainProperty pivotCol = getPivotColumn(protocol);
        DomainProperty valueCol = getValueColumn(protocol);

        for (String col : columns)
        {
            if (!col.equalsIgnoreCase(pivotCol.getName())
                && !col.equalsIgnoreCase(pivotCol.getLabel())
                && !col.equals(valueCol.getName())
                && !col.equals(valueCol.getLabel())
            ){
                ret.add(col);
            }
        }

        ret.addAll(getAllowableValues());
        return ret;
    }

    public List<String> getAllowableValues()
    {
        if (_sourceTable != null)
        {
            TableSelector ts = new TableSelector(_sourceTable, Collections.singleton(_sourceColumn), null, new Sort(_sourceColumn));
            String[] values = ts.getArray(String.class);
            return Arrays.asList(values);
        }
        return Collections.emptyList();
    }

    public AssayParser getFileParser(Container c, User u, int assayId)
    {
        return new PivotingAssayParser(this, c, u, assayId);
    }

    @Override
    public String getTemplateInstructions()
    {
        return "This is a variation on the default excel template.  It allows multiple results to be imported per row.  Note: the headers on the column are very sensitive, so it is recommended that you use the template that can be downloaded below.";
    }
}
