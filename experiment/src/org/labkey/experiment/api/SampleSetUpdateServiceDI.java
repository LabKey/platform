/*
 * Copyright (c) 2019 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.converters.IntegerConverter;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.Pump;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.experiment.ExpDataIterators;
import org.labkey.experiment.SampleSetAuditProvider;
import org.labkey.experiment.samples.UploadSamplesHelper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 *
 * This replaces the old row at a time UploadSamplesHelper.uploadMaterials() implementations.
 *
 * originally copied from ExpDataClassDataTableImpl.DataClassDataUpdateService,
 *
 * TODO find remaining shared code and refactor
 *
 */
public class SampleSetUpdateServiceDI extends DefaultQueryUpdateService
{
    public static final Logger LOG = Logger.getLogger(SampleSetUpdateServiceDI.class);

    public enum Options {
        SkipDerivation
    }


    final ExpSampleSetImpl _sampleset;
    final UserSchema _schema;
    final TableInfo _samplesTable;
    // super.getRootTable() is UserSchema table
    // getDbTable() is exp.materials
    // getSamplesTable() is the materialized table with row properties

    public SampleSetUpdateServiceDI(ExpMaterialTableImpl table, ExpSampleSetImpl sampleset)
    {
        super(table, table.getRealTable());
        this._sampleset = sampleset;
        this._schema = table.getUserSchema();
        this._samplesTable = sampleset.getTinfo();
    }

    UserSchema getSchema()
    {
        return _schema;
    }

    Container getContainer()
    {
        return _schema.getContainer();
    }

    User getUser()
    {
        return _schema.getUser();
    }


    @Override
    protected DataIteratorBuilder preTriggerDataIterator(DataIteratorBuilder in, DataIteratorContext context)
    {
//        boolean addUniqueSuffix = false;
//        boolean skipDerivation = false;
        Map<Enum,Object> configParameters = context.getConfigParameters();
        if (configParameters != null)
        {
//            if (configParameters.containsKey(SampleSetUpdateServiceDI.Options.AddUniqueSuffixForDuplicateNames))
//                addUniqueSuffix = true;
//            if (configParameters.containsKey(SampleSetUpdateServiceDI.Options.SkipDerivation))
//                skipDerivation = true;
        }

        // MOVE PrepareDataIteratorBuilder into this file
        return new UploadSamplesHelper.PrepareDataIteratorBuilder(_sampleset, getQueryTable(), in);
    }

    @Override
    public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
    {
        int ret = _importRowsUsingDIB(user, container, rows, null, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext);
        if (ret > 0 && !errors.hasErrors())
        {
            onSamplesChanged();
            audit("insert");
        }
        return ret;
    }

    @Override
    public int loadRows(User user, Container container, DataIteratorBuilder rows, DataIteratorContext context, @Nullable Map<String, Object> extraScriptContext) throws SQLException
    {
        int ret = super.loadRows(user, container, rows, context, extraScriptContext);
        if (ret > 0 && !context.getErrors().hasErrors())
        {
            onSamplesChanged();
            audit(context.getInsertOption().mergeRows ? "merge" : "insert");
        }
        return ret;
    }

    @Override
    public int mergeRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
    {
        int ret = _importRowsUsingDIB(user, container, rows, null, getDataIteratorContext(errors, InsertOption.MERGE, configParameters), extraScriptContext);
        if (ret > 0 && !errors.hasErrors())
        {
            onSamplesChanged();
            audit("merge");
        }
        return ret;
    }

    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
    {
        // insertRows with lineage is pretty good at deadlocking against it self, so use retry loop

        DbScope scope = getSchema().getDbSchema().getScope();
        List<Map<String, Object>> results = scope.executeWithRetry(transaction ->
                super._insertRowsUsingDIB(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext));

        if (results != null && results.size() > 0 && !errors.hasErrors())
        {
            onSamplesChanged();
            audit("insert");
        }
        return results;
    }

    @Override
    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        var ret = super.updateRows(user, container, rows, oldKeys, configParameters, extraScriptContext);

        /* setup mini dataiterator pipeline to process lineage */
        DataIterator di = _toDataIterator("updateRows.lineage", ret);
        ExpDataIterators.DerivationDataIteratorBuilder ddib = new ExpDataIterators.DerivationDataIteratorBuilder(DataIteratorBuilder.wrap(di), container, user, true);
        DataIteratorContext context = new DataIteratorContext();
        context.setInsertOption(InsertOption.MERGE);
        DataIterator derive = ddib.getDataIterator(context);
        new Pump(derive, context).run();
        if (context.getErrors().hasErrors())
            throw context.getErrors();

        if (ret.size() > 0)
        {
            onSamplesChanged();
            audit("update");
        }

        return ret;
    }

    @Override
    protected Map<String, Object> _select(Container container, Object[] keys) throws ConversionException
    {
        TableInfo d = getDbTable();
        TableInfo t = _sampleset.getTinfo();

        SQLFragment sql = new SQLFragment()
                .append("SELECT t.*, d.RowId, d.Name, d.Container, d.Description, d.CreatedBy, d.Created, d.ModifiedBy, d.Modified")
                .append(" FROM ").append(d, "d")
                .append(" LEFT OUTER JOIN ").append(t, "t")
                .append(" ON d.lsid = t.lsid")
                .append(" WHERE d.Container=?").add(container.getEntityId())
                .append(" AND d.rowid=?").add(keys[0]);

        return new SqlSelector(getDbTable().getSchema(), sql).getMap();
    }

    @Override
    protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws SQLException, ValidationException
    {
        // LSID was stripped by super.updateRows() and is needed to insert into the dataclass provisioned table
        String lsid = (String) oldRow.get("lsid");
        if (lsid == null)
            throw new ValidationException("lsid required to update row");

        // Replace attachment columns with filename and keep AttachmentFiles
        Map<String, Object> rowCopy = new CaseInsensitiveHashMap<>();

        rowCopy.putAll(row);
        Map<String, Object> ret = new CaseInsensitiveHashMap<>(super._update(user, c, rowCopy, oldRow, keys));

        // update provisioned table -- note that LSID isn't the PK so we need to use the filter to update the correct row instead
        keys = new Object[]{lsid};
        TableInfo t = _sampleset.getTinfo();
        // Sampleset Uses FILE_LINK not FILE_ATTACHMENT, use convertTypes() to handle posted files
        convertTypes(c, rowCopy, t, "sampleset");
        if (t.getColumnNameSet().stream().anyMatch(rowCopy::containsKey))
        {
            ret.putAll(Table.update(user, t, rowCopy, t.getColumn("lsid"), keys, null, Level.DEBUG));
        }

        // update comment
        ExpMaterialImpl sample = null;
        if (row.containsKey("flag") || row.containsKey("comment"))
        {
            Object o = row.containsKey("flag") ? row.get("flag") : row.get("comment");
            String flag = Objects.toString(o, null);

            sample = ExperimentServiceImpl.get().getExpMaterial(lsid);
            sample.setComment(user, flag);
        }

        // update aliases
        if (row.containsKey("Alias"))
            AliasInsertHelper.handleInsertUpdate(getContainer(), user, lsid, ExperimentService.get().getTinfoMaterialAliasMap(), row.get("Alias"));

        // search index
        SearchService ss = SearchService.get();
        if (ss != null)
        {
            if (sample == null)
                sample = ExperimentServiceImpl.get().getExpMaterial(lsid);
            sample.index(null);
        }

        ret.put("lsid", lsid);
        return ret;
    }

    @Override
    protected int truncateRows(User user, Container container)
    {
        int ret = SampleSetServiceImpl.get().truncateSampleSet(_sampleset, user, container);
        if (ret > 0)
        {
            // NOTE: Not necessary to call onSamplesChanged -- already called by truncateSampleSet
            audit("delete");
        }
        return ret;
    }

    protected Domain getDomain()
    {
        return _sampleset.getDomain();
    }

    @Override
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext)
            throws QueryUpdateServiceException
    {
        List<Integer> ids = new LinkedList<>();
        List<Map<String, Object>> result = new ArrayList<>(keys.size());

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

            ids.add(rowId);
            result.add(map);
        }

        // TODO check if this handle attachments???
        ExperimentServiceImpl.get().deleteMaterialByRowIds(user, container, ids);

        if (result.size() > 0)
        {
            // NOTE: Not necessary to call onSamplesChanged -- already called by deleteMaterialByRowIds
            audit("delete");
        }
        return result;
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
        if (row != null)
        {
            Object o = row.get(ExpMaterialTable.Column.RowId.name());
            if (o != null)
                return (Integer) (_converter.convert(Integer.class, o));
        }

        return null;
    }

    private Map<String, Object> getMaterialMap(Integer rowId, String lsid)
            throws QueryUpdateServiceException
    {
        Filter filter;
        if (rowId != null)
            filter = new SimpleFilter(FieldKey.fromParts(ExpMaterialTable.Column.RowId), rowId);
        else if (lsid != null)
            filter = new SimpleFilter(FieldKey.fromParts(ExpMaterialTable.Column.LSID), lsid);
        else
            throw new QueryUpdateServiceException("Either RowId or LSID is required to get Sample Set Material.");

        return new TableSelector(getQueryTable(), filter, null).getMap();
    }

    @Override
    public List<Map<String, Object>> getRows(User user, Container container, List<Map<String, Object>> keys)
            throws QueryUpdateServiceException
    {
        List<Map<String, Object>> result = new ArrayList<>(keys.size());
        for (Map<String, Object> k : keys)
        {
            result.add(getMaterialMap(getMaterialRowId(k), getMaterialLsid(k)));
        }
        return result;
    }

    @Override
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        return getMaterialMap(getMaterialRowId(keys), getMaterialLsid(keys));
    }

    /* don't need to implement these since we override insertRows() etc. */

//    @Override
//    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys)
//    {
//        throw new IllegalStateException();
//    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row)
    {
        throw new IllegalStateException();
    }

//    @Override
//    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow)
//    {
//        throw new IllegalStateException();
//    }

//    @Override
//    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
//    {
//        throw new IllegalStateException();
//    }

    private void onSamplesChanged()
    {
        var tx = getSchema().getDbSchema().getScope().getCurrentTransaction();
        if (tx != null)
        {
             if (!tx.isAborted())
                 tx.addCommitTask(this::fireSamplesChanged, DbScope.CommitTaskOption.POSTCOMMIT);
             else
                 LOG.info("Skipping onSamplesChanged callback; transaction aborted");
        }
        else
        {
            this.fireSamplesChanged();
        }
    }

    private void fireSamplesChanged()
    {
        _sampleset.onSamplesChanged(getUser(), null);
    }

    void audit(String insertUpdateChoice)
    {
        String verb;
        if (insertUpdateChoice.equals("merge"))
            verb = "inserted or updated";
        else
            verb = insertUpdateChoice + (insertUpdateChoice.endsWith("e") ? "d" : "ed");

        SampleSetAuditProvider.SampleSetAuditEvent event = new SampleSetAuditProvider.SampleSetAuditEvent(
                getContainer().getId(), "Samples " + verb + " in: " + _sampleset.getName());
        event.setSourceLsid(_sampleset.getLSID());
        event.setSampleSetName(_sampleset.getName());
        event.setInsertUpdateChoice(insertUpdateChoice);
        AuditLogService.get().addEvent(getUser(), event);
    }
}
