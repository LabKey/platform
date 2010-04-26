package org.labkey.experiment.api;

import org.apache.commons.beanutils.converters.IntegerConverter;
import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.query.*;
import org.labkey.api.reader.MapTabLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.experiment.samples.UploadMaterialSetForm;
import org.labkey.experiment.samples.UploadSamplesHelper;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 */
class ExpMaterialTableUpdateService implements QueryUpdateService
{
    private ExpMaterialTableImpl _table;

    public ExpMaterialTableUpdateService(ExpMaterialTableImpl table)
    {
        _table = table;
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

    private String getSampleSetLsid(Map<String, Object> row)
    {
        Object o = row.get(ExpMaterialTable.Column.SampleSet.name());
        if (o instanceof String)
            return (String)o;

        o = row.get("CpasType");
        if (o instanceof String)
            return (String)o;

        return null;
    }

    private List<ExpMaterial> insertOrUpdate(User user, Container container, String sampleSetLsid, Map<String, Object> row) throws QueryUpdateServiceException, ValidationException
    {
        if (sampleSetLsid == null)
            throw new QueryUpdateServiceException("Can't insert or update without a Sample Set LSID.");

        MaterialSource source = ExperimentServiceImpl.get().getMaterialSource(sampleSetLsid);
        if (source == null)
            throw new QueryUpdateServiceException("Can't find Sample Set for lsid '" + sampleSetLsid + "'");

        UploadMaterialSetForm form = new UploadMaterialSetForm();
        form.setContainer(container);
        form.setUser(user);
        form.setName(source.getName());
        form.setImportMoreSamples(true);
        form.setParentColumn(-1);
        form.setOverwriteChoice(UploadMaterialSetForm.OverwriteChoice.replace.name());
        form.setCreateMissingProperties(false);
        form.setCreateNewSampleSet(false);

        try
        {
            form.setLoader(new MapTabLoader(Collections.<Map<String, Object>>singletonList(row)));

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

    private Map<String, Object> getMaterialMap(Integer rowId, String lsid) throws QueryUpdateServiceException, SQLException
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

    public Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        return getMaterialMap(getMaterialRowId(keys), getMaterialLsid(keys));
    }

    public Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        List<ExpMaterial> materials = insertOrUpdate(user, container, getSampleSetLsid(row), row);
        if (materials.size() > 0)
            return getMaterialMap(materials.get(0).getRowId(), materials.get(0).getLSID());

        return null;
    }

    public Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldKeys) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        List<ExpMaterial> materials = insertOrUpdate(user, container, getSampleSetLsid(oldKeys), row);
        if (materials.size() > 0)
            return getMaterialMap(materials.get(0).getRowId(), materials.get(0).getLSID());

        return null;
    }

    public Map<String, Object> deleteRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        Integer rowId = getMaterialRowId(keys);

        Map<String, Object> map = getMaterialMap(rowId, getMaterialLsid(keys));
        if (map == null)
            throw new QueryUpdateServiceException("No Sample Set Material found for rowId or LSID");

        if (rowId == null)
            rowId = getMaterialRowId(map);
        if (rowId == null)
            throw new QueryUpdateServiceException("RowID is required to delete a Sample Set Material");

        ExperimentServiceImpl.get().deleteMaterialByRowIds(container, rowId.intValue());
        return map;
    }
}
