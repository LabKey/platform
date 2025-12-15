/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.MultiValuedLookupColumn;
import org.labkey.api.data.MultiValuedRenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.exp.query.ExpDataClassDataTable;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
import static org.labkey.api.util.IntegerUtils.isIntegral;

/**
 * Base class for both types of objects that can be the input and output from an experiment run - material and data.
 * User: jeckels
 * Date: Jul 28, 2008
 */
public abstract class AbstractRunItemImpl<Type extends RunItem> extends ExpIdentifiableBaseImpl<Type> implements ExpRunItem
{
    private static final Logger LOG = LogManager.getLogger(AbstractRunItemImpl.class);

    private ExpProtocolApplicationImpl _sourceApp;
    private List<ExpProtocolApplicationImpl> _successorAppList;
    private List<Long> _successorRunIdList = null;

    // For serialization
    protected AbstractRunItemImpl() {}

    public AbstractRunItemImpl(Type object)
    {
        super(object);
    }

    @Nullable
    @Override
    public String getDescription()
    {
        return _object.getDescription();
    }

    public void setDescription(String description)
    {
        ensureUnlocked();
        _object.setDescription(description);
    }

    @Override
    public List<ExpProtocolApplicationImpl> getSuccessorApps()
    {
        if (null == _successorAppList)
            throw new IllegalStateException("successorAppList not populated");
        return _successorAppList;
    }

    @Override
    public List<ExpRun> getSuccessorRuns()
    {
        if (null == _successorRunIdList)
            throw new IllegalStateException("successorRunIdList not populated for '" + this.getName() + "'");
        List<ExpRun> result = new ArrayList<>();
        for (Long integer : _successorRunIdList)
        {
            result.add(ExperimentService.get().getExpRun(integer.intValue()));
        }
        return result;
    }

    public void addSuccessorRunId(long runId)
    {
        if (_successorRunIdList == null)
        {
            _successorRunIdList = new ArrayList<>();
        }
        _successorRunIdList.add(runId);
    }

    public void setSuccessorAppList(List<ExpProtocolApplicationImpl> successorAppList)
    {
        ensureUnlocked();
        _successorAppList = successorAppList;
    }

    public void markAsPopulated(ExpProtocolApplicationImpl sourceApp)
    {
        _sourceApp = sourceApp;
        if (_successorAppList == null)
        {
            _successorRunIdList = new ArrayList<>();
        }
        markSuccessorAppsAsPopulated();
    }

    public void markSuccessorAppsAsPopulated()
    {
        if (_successorAppList == null)
        {
            _successorAppList = new ArrayList<>();
        }
    }

    @Override
    public ExpProtocol getSourceProtocol()
    {
        ExpProtocolApplication protApp = getSourceApplication();
        if (protApp != null)
        {
            return protApp.getProtocol();
        }
        return null;
    }

    @Override
    @Nullable
    public ExpProtocolApplicationImpl getSourceApplication()
    {
        if (null != _sourceApp)
        {
            return _sourceApp;
        }
        if (_object.getSourceApplicationId() == null)
        {
            return null;
        }
        _sourceApp = ExperimentServiceImpl.get().getExpProtocolApplication(_object.getSourceApplicationId().intValue());
        return _sourceApp;
    }

    @Override
    public long getRowId()
    {
        return _object.getRowId();
    }

    protected void setRowId(long rowId)
    {
        _object.setRowId(rowId);
    }

    @Override
    public User getCreatedBy()
    {
        return _object.getCreatedBy() == null ? null : UserManager.getUser(_object.getCreatedBy().intValue());
    }

    @Override
    public Integer getCreatedById()
    {
        return _object.getCreatedBy();
    }

    @Override
    public User getModifiedBy()
    {
        return _object.getModifiedBy() == null ? null : UserManager.getUser(_object.getModifiedBy().intValue());
    }

    @Override
    public Integer getModifiedById()
    {
        return _object.getModifiedBy();
    }

    @Override
    public Date getModified()
    {
        return _object.getModified();
    }

    @Override
    @Nullable
    public ExpRunImpl getRun()
    {
        if (_object.getRunId() == null)
        {
            return null;
        }
        return ExperimentServiceImpl.get().getExpRun(_object.getRunId().intValue());
    }


    @Override
    public Long getRunId()
    {
        return _object.getRunId();
    }

    @Override
    public void setSourceApplication(ExpProtocolApplication app)
    {
        ensureUnlocked();
        if (app != null && app.getRowId() == 0)
        {
            throw new IllegalArgumentException();
        }
        _object.setSourceApplicationId(app == null ? null : app.getRowId());
        _object.setContainer(getContainer());
        _object.setRunId(app == null ? null : app.getRun().getRowId());
        _sourceApp = null;
        _successorAppList = null;
        _successorRunIdList = null;
    }

    @Override
    public void setRun(ExpRun run)
    {
        ensureUnlocked();
        if (run != null && run.getRowId() == 0)
        {
            throw new IllegalArgumentException();
        }
        _object.setRunId(run == null ? null : run.getRowId());
    }

    @Override
    public void setCpasType(String type)
    {
        ensureUnlocked();
        _object.setCpasType(type);
    }

    @Override
    public Container getContainer()
    {
        return _object.getContainer();
    }

    @Override
    public void setContainer(Container container)
    {
        ensureUnlocked();
        _object.setContainer(container);
    }

    @Override
    public Date getCreated()
    {
        return _object.getCreated();
    }

    protected List<ExpProtocolApplicationImpl> getTargetApplications(SimpleFilter filter, TableInfo inputTable)
    {
        List<ExpProtocolApplicationImpl> ret = new ArrayList<>();
        for (Integer id : new TableSelector(inputTable, Collections.singleton("TargetApplicationId"), filter, null).getArrayList(Integer.class))
        {
            ret.add(ExperimentServiceImpl.get().getExpProtocolApplication(id.intValue()));
        }
        return ret;
    }

    protected List<ExpRunImpl> getTargetRuns(TableInfo inputTable, String rowIdColumnName)
    {
        SQLFragment sql = new SQLFragment("SELECT r.* FROM ");
        sql.append(ExperimentService.get().getTinfoExperimentRun(), "r");
        sql.append("\nWHERE r.RowId IN (SELECT pa.RunId \nFROM ");
        sql.append(ExperimentServiceImpl.get().getTinfoProtocolApplication(), "pa");
        sql.append("\nINNER JOIN ");
        sql.append(inputTable, "i");
        sql.append(" ON pa.RowId = i.TargetApplicationId AND i.");
        sql.append(rowIdColumnName);
        sql.append(" = ?)");
        sql.add(getRowId());
        return ExpRunImpl.fromRuns(new SqlSelector(ExperimentService.get().getSchema(), sql).getArrayList(ExperimentRun.class));
    }

    protected Map<String, ObjectProperty> getObjectProperties(TableInfo ti)
    {
        if (null == ti)
            return emptyMap();

        return OntologyManager.getExpSchema().getScope().executeWithRetryReadOnly(tx ->
        {
            var ret = new HashMap<String, ObjectProperty>();
            getObjectPropertiesSelector(ti).forEach(rs ->
            {
                for (ColumnInfo c : ti.getColumns())
                {
                    if (c.getPropertyURI() == null || Strings.CI.equals("lsid", c.getName()) || Strings.CI.equals("genId", c.getName()))
                        continue;
                    if (c.isMvIndicatorColumn())
                        continue;
                    Object value = c.getValue(rs);
                    String mvIndicator = null;
                    if (null != c.getMvColumnName())
                    {
                        ColumnInfo mv = ti.getColumn(c.getMvColumnName());
                        mvIndicator = null == mv ? null : (String) mv.getValue(rs);
                    }
                    if (null == value && null == mvIndicator)
                        continue;
                    if (null != mvIndicator)
                        value = null;

                    var propertyType = null == c.getPropertyType() ? PropertyType.getFromJdbcType(c.getJdbcType()) : c.getPropertyType();
                    var prop = new ObjectProperty(getLSID(), getContainer(), c.getPropertyURI(), value, propertyType, c.getName());
                    if (null != mvIndicator)
                        prop.setMvIndicator(mvIndicator);

                    ret.put(c.getPropertyURI(), prop);
                }
            });

            return ret;
        });
    }

    protected TableSelector getObjectPropertiesSelector(@NotNull TableInfo table)
    {
        return new TableSelector(table, new SimpleFilter(FieldKey.fromParts("lsid"), getLSID()), null);
    }

    protected void processIndexValues(
            Map<String, Object> props,
            @NotNull ExpRunItemTableImpl<?> table,
            CaseInsensitiveHashSet skipColumns,
            Set<String> identifiersHi,
            Set<String> identifiersMed,
            Set<String> identifiersLo,
            Set<String> keywordsHi,
            Set<String> keywordsMed,
            Set<String> keywordsLo,
            JSONObject jsonData
    )
    {
        skipColumns.add("genId");
        skipColumns.add("rowId");
        // collect the set of columns to index
        Set<ColumnInfo> columns = table.getExtendedColumns(true).values().stream().filter(col -> {
            // skip the base-columns - they will be added to the index separately and/or we don't want to index them (Issue 52467)
            if (skipColumns.contains(col.getName()))
                return false;

            // skip non-text and non-int columns or columns that aren't lookups
            if (!(col.getJdbcType().isText() || col.getJdbcType().isInteger() || col.getFk() != null))
                return false;

            // Issue 52467: Skip indexing both the raw columns like LSID and the wrapped versions of those columns that are lookups to other data
            if ("lsidtype".equalsIgnoreCase(col.getSqlTypeName()) || "entityid".equalsIgnoreCase(col.getSqlTypeName()))
                return false;

            // Issue 51862: Skip indexing calculated columns
            if (col.isValueExpressionColumn())
                return false;

            return true;
        }).collect(Collectors.toCollection(LinkedHashSet::new));

        if (columns.isEmpty())
            return;

        TableSelector ts = new TableSelector(table, columns, new SimpleFilter(ExpDataClassDataTable.Column.RowId.fieldKey(), getRowId()), null);
        ts.setForDisplay(true);
        try (Results r = ts.getResults())
        {
            Map<FieldKey, ColumnInfo> fields = r.getFieldMap();
            if (r.next())
            {
                Map<FieldKey, Object> map = r.getFieldKeyRowMap();
                for (Map.Entry<FieldKey, ColumnInfo> entry : fields.entrySet())
                {
                    FieldKey fieldKey = entry.getKey();
                    ColumnInfo col = entry.getValue();
                    if (!col.getJdbcType().isText() && !col.getJdbcType().isInteger())
                        continue;

                    if (col.getName().equalsIgnoreCase("lsid") || col.getSqlTypeName().equalsIgnoreCase("lsidtype") || col.getSqlTypeName().equalsIgnoreCase("entityid"))
                        continue;

                    Object o = map.get(fieldKey);
                    String s;
                    // Issue 52961: DataClass: Integer fields are not index for data class
                    if (o instanceof String)
                        s = (String)o;
                    else if (isIntegral(o))
                        s = String.valueOf(o);
                    else
                        continue;

                    List<String> values;

                    if (col instanceof MultiValuedLookupColumn)
                        values = Arrays.asList(s.split(MultiValuedRenderContext.VALUE_DELIMITER_REGEX));
                    else
                        values = Arrays.asList(s);

                    SearchService.PROPERTY searchProperty = table.getSearchIndexColumn(fieldKey);
                    if (searchProperty != null)
                    {
                        // Fow now only add indexed field values to search jsonData
                        if (!values.isEmpty())
                        {
                            if (values.size() == 1)
                                jsonData.put(fieldKey.toString(), values.get(0));
                            else
                                jsonData.put(fieldKey.toString(), values);
                        }

                        switch (searchProperty)
                        {
                            case identifiersHi -> {
                                identifiersHi.addAll(values);
                                continue;
                            }
                            case identifiersMed -> {
                                identifiersMed.addAll(values);
                                continue;
                            }
                            case identifiersLo -> {
                                identifiersLo.addAll(values);
                                continue;
                            }
                            case keywordsHi -> {
                                keywordsHi.addAll(values);
                                continue;
                            }
                            case keywordsMed -> {
                                keywordsMed.addAll(values);
                                continue;
                            }
                            case keywordsLo -> {
                                keywordsLo.addAll(values);
                                continue;
                            }
                            default -> LOG.debug("Unable to index column " + fieldKey.toString() + " with property: " + searchProperty.name() + ". Not yet supported.");
                        }
                    }

                    if ("textarea".equalsIgnoreCase(col.getInputType()))
                    {
                        // treat multi-line text values as keywords, otherwise treat as an identifier
                        keywordsLo.addAll(values);
                    }
                    else
                    {
                        identifiersMed.addAll(values);
                    }
                }
            }
        }
        catch (SQLException e)
        {
            // ignore
        }

        // === Not stemmed

        props.put(SearchService.PROPERTY.identifiersHi.toString(), StringUtils.join(identifiersHi, " "));
        props.put(SearchService.PROPERTY.identifiersMed.toString(), StringUtils.join(identifiersMed, " "));
        props.put(SearchService.PROPERTY.identifiersLo.toString(), StringUtils.join(identifiersLo, " "));


        // === Stemmed

        props.put(SearchService.PROPERTY.keywordsHi.toString(), StringUtils.join(keywordsHi, " "));
        props.put(SearchService.PROPERTY.keywordsMed.toString(), StringUtils.join(keywordsMed, " "));
        props.put(SearchService.PROPERTY.keywordsLo.toString(), StringUtils.join(keywordsLo, " "));

    }
}
