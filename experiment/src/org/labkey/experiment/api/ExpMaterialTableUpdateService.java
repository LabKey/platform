/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.experiment.api;

import org.apache.commons.beanutils.converters.IntegerConverter;
import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.query.*;
import org.labkey.api.reader.MapTabLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.experiment.samples.UploadMaterialSetForm;
import org.labkey.experiment.samples.UploadSamplesHelper;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 */
class ExpMaterialTableUpdateService implements QueryUpdateService
{
    private ExpMaterialTableImpl _table;
    private ExpSampleSet _ss;

    public ExpMaterialTableUpdateService(ExpMaterialTableImpl table, ExpSampleSet ss)
    {
        if (ss == null)
            throw new IllegalArgumentException("Can't insert or update without a Sample Set.");

        _table = table;
        _ss = ss;
    }

    private String getMaterialLsid(Map<String, Object> row)
    {
        Object o = row.get(ExpMaterialTable.Column.LSID.name());
        if (o instanceof String)
            return (String)o;

        return null;
    }


    IntegerConverter _converter = new IntegerConverter();

    private Integer getMaterialRowId(Map<String, Object> row)
    {
        Object o = row.get(ExpMaterialTable.Column.RowId.name());
        if (o != null)
            return (Integer)(_converter.convert(Integer.class, o));

        return null;
    }

    private List<ExpMaterial> insertOrUpdate(User user, Container container, List<Map<String, Object>> rows)
            throws QueryUpdateServiceException, ValidationException
    {
        UploadMaterialSetForm form = new UploadMaterialSetForm();
        form.setContainer(container);
        form.setUser(user);
        form.setName(_ss.getName());
        form.setImportMoreSamples(true);
        form.setParentColumn(-1);
        form.setOverwriteChoice(UploadMaterialSetForm.OverwriteChoice.replace.name());
        form.setCreateNewSampleSet(false);

        try
        {
            form.setLoader(new MapTabLoader(rows));

            UploadSamplesHelper helper = new UploadSamplesHelper(form);
            Pair<MaterialSource, List<ExpMaterial>> pair = helper.uploadMaterials();
            return pair.second;
        }
        catch (IOException e)
        {
            throw new QueryUpdateServiceException(e);
        }
        catch (ExperimentException e)
        {
            throw new QueryUpdateServiceException(e);
        }
    }

    private Map<String, Object> getMaterialMap(Integer rowId, String lsid)
            throws QueryUpdateServiceException, SQLException
    {
        Filter filter;
        if (rowId != null)
            filter = new SimpleFilter(ExpMaterialTable.Column.RowId.name(), rowId);
        else if (lsid != null)
            filter = new SimpleFilter(ExpMaterialTable.Column.LSID.name(), lsid);
        else
            throw new QueryUpdateServiceException("Either RowId or LSID is required to get Sample Set Material.");


        return Table.selectObject(_table, Table.ALL_COLUMNS, filter, null, Map.class);
    }

    @Override
    public List<Map<String, Object>> getRows(User user, Container container, List<Map<String, Object>> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(keys.size());
        for (Map<String, Object> k : keys)
        {
            result.add(getMaterialMap(getMaterialRowId(k), getMaterialLsid(k)));
        }
        return result;
    }

    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows)
            throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        List<ExpMaterial> materials = insertOrUpdate(user, container, rows);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(materials.size());
        for (ExpMaterial material : materials)
        {
            result.add(getMaterialMap(material.getRowId(), material.getLSID()));
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        List<ExpMaterial> materials = insertOrUpdate(user, container, rows);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(materials.size());
        for (ExpMaterial material : materials)
        {
            result.add(getMaterialMap(material.getRowId(), material.getLSID()));
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        int[] ids = new int[keys.size()];
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(keys.size());
        for (Map<String, Object> k : keys)
        {
            Integer rowId = getMaterialRowId(k);
            Map<String, Object> map = getMaterialMap(rowId, getMaterialLsid(k));
            if (map == null)
                throw new QueryUpdateServiceException("No Sample Set Material found for rowId or LSID");

            if (rowId == null)
                rowId = getMaterialRowId(map);
            if (rowId == null)
                throw new QueryUpdateServiceException("RowID is required to delete a Sample Set Material");

            result.add(map);
        }

        ExperimentServiceImpl.get().deleteMaterialByRowIds(container, ids);
        return result;
    }

}
