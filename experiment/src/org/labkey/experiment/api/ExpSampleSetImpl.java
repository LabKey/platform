/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ProtocolImplementation;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.labkey.experiment.samples.UploadSamplesHelper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class ExpSampleSetImpl extends ExpIdentifiableEntityImpl<MaterialSource> implements ExpSampleSet
{
    private Domain _domain;
    private StringExpression _parsedNameExpression;

    public ExpSampleSetImpl(MaterialSource ms)
    {
        super(ms);
    }

    @Override
    public URLHelper detailsURL()
    {
        ActionURL ret = new ActionURL(ExperimentController.ShowMaterialSourceAction.class, getContainer());
        ret.addParameter("rowId", Integer.toString(getRowId()));
        return ret;
    }

    @Override
    public Container getContainer()
    {
        return _object.getContainer();
    }

    @Override
    public int getRowId()
    {
        return _object.getRowId();
    }

    @Override
    public String getMaterialLSIDPrefix()
    {
        return _object.getMaterialLSIDPrefix();
    }

    @Override
    public String getDescription()
    {
        return _object.getDescription();
    }

    @Override
    public boolean canImportMoreSamples()
    {
        return hasNameAsIdCol() || getIdCol1() != null;
    }

    private DomainProperty getDomainProperty(String uri)
    {
        if (uri == null)
        {
            return null;
        }

        return getType().getPropertyByURI(uri);
    }

    @Override
    @NotNull
    public List<DomainProperty> getIdCols()
    {
        List<DomainProperty> result = new ArrayList<>();
        if (hasNameAsIdCol())
            return result;

        DomainProperty idCol1 = getIdCol1();
        if (idCol1 != null)
        {
            result.add(idCol1);
        }
        DomainProperty idCol2 = getIdCol2();
        if (idCol2 != null)
        {
            result.add(idCol2);
        }
        DomainProperty idCol3 = getIdCol3();
        if (idCol3 != null)
        {
            result.add(idCol3);
        }
        return result;
    }

    @Override
    public boolean hasIdColumns()
    {
        return _object.getIdCol1() != null;
    }

    @Override
    public boolean hasNameAsIdCol()
    {
        return ExpMaterialTable.Column.Name.name().equals(_object.getIdCol1());
    }

    // NOTE: intentionally not public in ExpSampleSet interface
    public void setParentCol(@Nullable String parentColumnPropertyURI)
    {
        _object.setParentCol(getPropertyOrThrow(parentColumnPropertyURI).getPropertyURI());
    }

    // NOTE: intentionally not public in ExpSampleSet interface
    public void setIdCols(@NotNull List<String> propertyURIs)
    {
        if (_object.getNameExpression() != null)
            throw new IllegalArgumentException("Can't set both a name expression and idCols");

        if (propertyURIs.size() > 0)
        {
            _object.setIdCol1(getPropertyOrThrow(propertyURIs.get(0)).getPropertyURI());
            if (propertyURIs.size() > 1)
            {
                _object.setIdCol2(getPropertyOrThrow(propertyURIs.get(1)).getPropertyURI());
                if (propertyURIs.size() > 2)
                {
                    _object.setIdCol3(getPropertyOrThrow(propertyURIs.get(2)).getPropertyURI());
                    if (propertyURIs.size() > 3)
                    {
                        throw new IllegalArgumentException("Only three ID columns are supported, but " + propertyURIs.size() + " were requested: " + propertyURIs);
                    }
                }
            }
        }
    }

    @NotNull
    private DomainProperty getPropertyOrThrow(String propertyURI)
    {
        DomainProperty dp = getDomainProperty(propertyURI);
        if (dp == null)
            throw new IllegalArgumentException("Failed to find property '" + propertyURI + "'");

        return dp;
    }

    @Override
    @Nullable
    public DomainProperty getIdCol1()
    {
        if (hasNameAsIdCol())
            return null;

        DomainProperty result = getDomainProperty(_object.getIdCol1());
        if (result == null)
        {
            List<? extends DomainProperty> props = getType().getProperties();
            if (!props.isEmpty())
            {
                result = props.get(0);
            }
        }
        return result;
    }

    @Override
    public DomainProperty getIdCol2()
    {
        return getDomainProperty(_object.getIdCol2());
    }

    @Override
    public DomainProperty getIdCol3()
    {
        return getDomainProperty(_object.getIdCol3());
    }

    @Override
    public DomainProperty getParentCol()
    {
        return getDomainProperty(_object.getParentCol());
    }

    // NOTE: intentionally not public in ExpSampleSet interface
    public void setNameExpression(String expression)
    {
        if (hasIdColumns() && !hasNameAsIdCol())
            throw new IllegalArgumentException("Can't set both a name expression and idCols");

        _object.setNameExpression(expression);
    }

    @Override
    public String getNameExpression()
    {
        return _object.getNameExpression();
    }

    @Override
    public boolean hasNameExpression()
    {
        return _object.getNameExpression() != null;
    }

    @Override
    @Nullable
    public StringExpression getParsedNameExpression()
    {
        if (_parsedNameExpression == null)
        {
            String s = null;
            if (_object.getNameExpression() != null)
            {
                s = _object.getNameExpression();
            }
            else if (hasNameAsIdCol())
            {
                s = "${name}";
            }
            else if (hasIdColumns())
            {
                List<DomainProperty> idCols = getIdCols();
                StringBuilder expr = new StringBuilder();
                String sep = "";
                for (DomainProperty dp : idCols)
                {
                    expr.append(sep).append("${").append(dp.getName()).append("}");
                    sep = "-";
                }
                s = expr.toString();
            }
            else
            {
                // CONSIDER: Create a default expression as a fallback? ${RowId}
            }

            if (s != null)
            {
                // NOTE: Side-effects are allowed so the sample counters can be incremented when evaluating the expression
                _parsedNameExpression = StringExpressionFactory.create(s, false, StringExpressionFactory.AbstractStringExpression.NullValueBehavior.ReplaceNullWithBlank, true);
            }
        }

        return _parsedNameExpression;
    }

    /**
     * Create the name expression context shared for the entire batch of samples.
     */
    private Map<String, Object> createBatchExpressionContext()
    {
        Map<String, Object> map = new CaseInsensitiveHashMap<>();

        map.put("BatchRandomId", String.valueOf(new Random().nextInt()).substring(1,5));
        map.put("Now", new Date());

        return map;
    }

    @Override
    public void createSampleNames(@NotNull List<Map<String, Object>> maps) throws ExperimentException
    {
        createSampleNames(maps, null, null, null, false, false);
    }

    @Override
    public void createSampleNames(@NotNull List<Map<String, Object>> maps,
                                  @Nullable StringExpression expr,
                                  @Nullable Set<ExpData> parentDatas,
                                  @Nullable Set<ExpMaterial> parentSamples,
                                  boolean skipDuplicates,
                                  boolean addUniqueSuffixForDuplicates)
            throws ExperimentException
    {
        if (expr == null)
            expr = getParsedNameExpression();

        Map<String, Object> batchExpressionContext = createBatchExpressionContext();

        Map<String, Integer> newNames = new CaseInsensitiveHashMap<>();
        Map<String, Map<String, Object>> firstRowForName = new CaseInsensitiveHashMap<>();
        int i = 0;
        ListIterator<Map<String, Object>> li = maps.listIterator();
        while (li.hasNext())
        {
            i++;
            Map<String, Object> map = li.next();
            String name;
            try
            {
                name = createSampleName(map, batchExpressionContext, expr, parentDatas, parentSamples);
            }
            catch (IllegalArgumentException e)
            {
                // Failed to generate a name due to some part of the expression not in the row
                if (hasNameExpression())
                    throw new ExperimentException("Failed to generate name for Sample on row " + i, e);
                else if (hasNameAsIdCol())
                    throw new ExperimentException("Name is required for Sample on row " + i, e);
                else
                    throw new ExperimentException("All id columns are required for Sample on row " + i, e);
            }

            if (newNames.containsKey(name))
            {
                if (addUniqueSuffixForDuplicates)
                {
                    // Update the first occurrence of the name to include the unique suffix
                    Map<String, Object> first = firstRowForName.get(name);
                    if (first != null)
                    {
                        first.put("Name", name + ".1");
                        firstRowForName.remove(name);
                    }

                    // Add a unique suffix to the end of the name.
                    int count = newNames.get(name) + 1;
                    newNames.put(name, count);
                    name += "." + count;
                }
                else if (skipDuplicates)
                {
                    // Issue 23384: SampleSet: import should ignore duplicate rows when ignore duplicates is selected
                    li.remove();
                    continue;
                }
                else
                    throw new ExperimentException("Duplicate material '" + name + "' on row " + i);
            }
            else
            {
                newNames.put(name, 1);

                // If we generating unique names, remember the first time we see each name
                if (addUniqueSuffixForDuplicates)
                    firstRowForName.put(name, map);
            }

            map.put("Name", name);
        }
    }

    @Override
    public String createSampleName(@NotNull Map<String, Object> rowMap)
    {
        return createSampleName(rowMap, null, null, null, null);
    }

    @Override
    public String createSampleName(@NotNull Map<String, Object> rowMap,
                                   @Nullable Map<String, Object> batchContext,
                                   @Nullable StringExpression expr,
                                   @Nullable Set<ExpData> parentDatas,
                                   @Nullable Set<ExpMaterial> parentSamples)
    {
        // If a name is already provided, just use it as is
        if (rowMap.get("name") != null)
            return String.valueOf(rowMap.get("name"));

        if (expr == null)
            expr = getParsedNameExpression();
        if (expr == null)
            return null;

        if (batchContext == null)
            batchContext = createBatchExpressionContext();

        // Add extra context variables
        Map<String, Object> ctx = additionalContext(rowMap, batchContext, parentDatas, parentSamples);

        String name = expr.eval(ctx);
        if (name == null || name.length() == 0)
            throw new IllegalArgumentException("Can't create new sample name in sample set '" + getName() + "' using the name expression: " + expr.getSource());

        return name;
    }

    private Map<String, Object> additionalContext(@NotNull Map<String, Object> rowMap, @NotNull Map<String, Object> batchContext, Set<ExpData> parentDatas, Set<ExpMaterial> parentSamples)
    {
        StringExpression expr = getParsedNameExpression();
        assert expr != null;
        String exprSource = expr.getSource().toLowerCase();

        Map<String, Object> ctx = new CaseInsensitiveHashMap<>();
        ctx.putAll(batchContext);
        ctx.put("RandomId", String.valueOf(new Random().nextInt()).substring(1,5));
        ctx.putAll(rowMap);

        // UploadSamplesHelper uses propertyURIs in the rowMap -- add short column names to the map
        Domain d = getType();
        if (d != null)
        {
            for (DomainProperty dp : d.getProperties())
            {
                PropertyDescriptor pd = dp.getPropertyDescriptor();
                if (rowMap.containsKey(pd.getPropertyURI()))
                    ctx.put(pd.getName(), rowMap.get(pd.getPropertyURI()));
            }
        }

        // Inspect the expression looking for any sample counter formats bound to a column, e.g. ${column:dailySampleCount}
        // If sample counters bound to a column are found, the sample counters will be incremented for that date when the expression is evaluated.
        // Otherwise, update the sample counters for today's date immediately even if the expression doesn't contain a counter replacement token.
        // TODO: Expose the set of expression variables and substitution formats instead of relying on the source
        if (!(exprSource.contains(":dailysamplecount}") || exprSource.contains(":weeklysamplecount}") || exprSource.contains(":monthlysamplecount}") || exprSource.contains(":yearlysamplecount}")))
        {
            Date now = (Date)batchContext.get("now");
            Map<String, Integer> counts = ExperimentServiceImpl.get().incrementSampleCounts(now);
            ctx.putAll(counts);
        }

        // If needed, add the parent names to the replacement map
        // TODO: Expose the set of expression variables instead of relying on the source
        if (exprSource.contains("${inputs") || exprSource.contains("${datainputs") || exprSource.contains("${materialinputs"))
        {
            Set<String> allInputs = new LinkedHashSet<>();
            Set<String> dataInputs = new LinkedHashSet<>();
            Set<String> materialInputs = new LinkedHashSet<>();

            if (parentDatas != null)
            {
                parentDatas.stream().map(ExpObject::getName).forEachOrdered(parentName -> {
                    allInputs.add(parentName);
                    dataInputs.add(parentName);
                });
            }

            if (parentSamples != null)
            {
                parentSamples.stream().map(ExpObject::getName).forEachOrdered(parentName -> {
                    allInputs.add(parentName);
                    materialInputs.add(parentName);
                });
            }

            for (String colName : rowMap.keySet())
            {
                Object value = rowMap.get(colName);
                if (value == null)
                    continue;

                if (colName.startsWith(UploadSamplesHelper.DATA_INPUT_PARENT))
                {
                    parentNames(value, colName).forEach(parentName -> {
                        allInputs.add(parentName);
                        dataInputs.add(parentName);
                    });
                }
                else if (colName.startsWith(UploadSamplesHelper.MATERIAL_INPUT_PARENT))
                {
                    parentNames(value, colName).forEach(parentName -> {
                        allInputs.add(parentName);
                        materialInputs.add(parentName);
                    });
                }
            }

            ctx.put("Inputs", allInputs);
            ctx.put("DataInputs", dataInputs);
            ctx.put("MaterialInputs", materialInputs);
        }

        return ctx;
    }

    private Collection<String> parentNames(Object value, String parentColName)
    {
        return UploadSamplesHelper.parentNames(value, parentColName).collect(Collectors.toList());
    }


    @Override
    public void setDescription(String s)
    {
        ensureUnlocked();
        _object.setDescription(s);
    }

    @Override
    public void setMaterialLSIDPrefix(String s)
    {
        ensureUnlocked();
        _object.setMaterialLSIDPrefix(s);
    }

    @Override
    public List<ExpMaterialImpl> getSamples()
    {
        return getSamples(getContainer());
    }

    @Override
    public List<ExpMaterialImpl> getSamples(Container c)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("CpasType"), getLSID());
        Sort sort = new Sort("Name");
        return ExpMaterialImpl.fromMaterials(new TableSelector(ExperimentServiceImpl.get().getTinfoMaterial(), filter, sort).getArrayList(Material.class));
    }

    @Override
    @Deprecated
    public ExpMaterialImpl getSample(String name)
    {
        return getSample(getContainer(), name);
    }

    @Override
    public ExpMaterialImpl getSample(Container c, String name)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("CpasType"), getLSID());
        filter.addCondition(FieldKey.fromParts("Name"), name);

        Material material = new TableSelector(ExperimentServiceImpl.get().getTinfoMaterial(), filter, null).getObject(Material.class);
        if (material == null)
            return null;
        return new ExpMaterialImpl(material);
    }

    @Override
    @NotNull
    public Domain getType()
    {
        if (_domain == null)
        {
            _domain = PropertyService.get().getDomain(getContainer(), getLSID());
            if (_domain == null)
            {
                _domain = PropertyService.get().createDomain(getContainer(), getLSID(), getName());
                try
                {
                    _domain.save(null);
                }
                catch (ChangePropertyDescriptorException e)
                {
                    throw new UnexpectedException(e);
                }
            }
        }
        return _domain;
    }

    public ExpProtocol[] getProtocols(User user)
    {
        TableInfo tinfoProtocol = ExperimentServiceImpl.get().getTinfoProtocol();
        ColumnInfo colLSID = tinfoProtocol.getColumn("LSID");
        ColumnInfo colSampleLSID = new PropertyColumn(ExperimentProperty.SampleSetLSID.getPropertyDescriptor(), colLSID, getContainer(), user, false);
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(colSampleLSID, getLSID());
        List<ColumnInfo> selectColumns = new ArrayList<>();
        selectColumns.addAll(tinfoProtocol.getColumns());
        selectColumns.add(colSampleLSID);
        Protocol[] protocols = new TableSelector(tinfoProtocol, selectColumns, filter, null).getArray(Protocol.class);
        ExpProtocol[] ret = new ExpProtocol[protocols.length];
        for (int i = 0; i < protocols.length; i ++)
        {
            ret[i] = new ExpProtocolImpl(protocols[i]);
        }
        return ret;
    }

    public void onSamplesChanged(User user, List<Material> materials) throws SQLException
    {
        ExpProtocol[] protocols = getProtocols(user);
        if (protocols.length == 0)
            return;
        List<ExpMaterialImpl> expMaterials = null;

        if (materials != null)
        {
            expMaterials = new ArrayList<>(materials.size());
            for (int i = 0; i < expMaterials.size(); i ++)
            {
                expMaterials.add(new ExpMaterialImpl(materials.get(i)));
            }
        }
        for (ExpProtocol protocol : protocols)
        {
            ProtocolImplementation impl = protocol.getImplementation();
            if (impl == null)
                continue;
            impl.onSamplesChanged(user, protocol, expMaterials);
        }
    }


    @Override
    public void setContainer(Container container)
    {
        ensureUnlocked();
        _object.setContainer(container);
    }

    @Override
    public void save(User user)
    {
        boolean isNew = _object.getRowId() == 0;
        save(user, ExperimentServiceImpl.get().getTinfoMaterialSource());
        if (isNew)
        {
            Domain domain = PropertyService.get().getDomain(getContainer(), getLSID());
            if (domain == null)
            {
                domain = PropertyService.get().createDomain(getContainer(), getLSID(), getName());
                try
                {
                    domain.save(user);
                }
                catch (ChangePropertyDescriptorException e)
                {
                    throw new UnexpectedException(e);
                }
            }

            ExpSampleSet activeSampleSet = ExperimentServiceImpl.get().lookupActiveSampleSet(getContainer());
            if (activeSampleSet == null)
            {
                ExperimentServiceImpl.get().setActiveSampleSet(getContainer(), this);
            }
        }

        // NOTE cacheMaterialSource() of course calls transactioncache.put(), which does not alter the shared cache! (BUG?)
        // Just call uncache(), and let normal cache loading do its thing
        ExperimentServiceImpl.get().uncacheMaterialSource(_object);

        ExperimentServiceImpl.get().indexSampleSet(this);
    }

    @Override
    public void delete(User user)
    {
        try
        {
            ExperimentServiceImpl.get().deleteSampleSet(getRowId(), getContainer(), user);
        }
        catch (ExperimentException e)
        {
            throw new RuntimeValidationException(e);
        }
    }

    @Override
    public Lsid.LsidBuilder generateSampleLSID()
    {
        return UploadSamplesHelper.generateSampleLSID(this.getDataObject());
    }

    @Override
    public String toString()
    {
        return "SampleSet " + getName() + " in " + getContainer().getPath();
    }
}
