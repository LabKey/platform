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
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ProtocolImplementation;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExpSampleSetImpl extends ExpIdentifiableEntityImpl<MaterialSource> implements ExpSampleSet
{
    private Domain _domain;
    private NameGenerator _nameGen;

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

    @Nullable
    public NameGenerator getNameGenerator()
    {
        if (_nameGen == null)
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
                TableInfo parentTable = QueryService.get().getUserSchema(User.getSearchUser(), getContainer(), SamplesSchema.SCHEMA_NAME).getTable(getName());
                _nameGen = new NameGenerator(s, parentTable, true);
            }
        }

        return _nameGen;
    }

    @Override
    public void createSampleNames(@NotNull List<Map<String, Object>> maps,
                                  @Nullable StringExpressionFactory.FieldKeyStringExpression expr,
                                  @Nullable Set<ExpData> parentDatas,
                                  @Nullable Set<ExpMaterial> parentSamples,
                                  boolean skipDuplicates,
                                  boolean addUniqueSuffixForDuplicates)
            throws ExperimentException
    {
        NameGenerator nameGen;
        if (expr != null)
        {
            TableInfo parentTable = QueryService.get().getUserSchema(User.getSearchUser(), getContainer(), SamplesSchema.SCHEMA_NAME).getTable(getName());
            nameGen = new NameGenerator(expr, parentTable);
        }
        else
        {
            nameGen = getNameGenerator();
            if (nameGen == null)
                throw new ExperimentException("Error creating name expression generator");
        }

        try
        {
            nameGen.generateNames(maps, parentDatas, parentSamples, skipDuplicates, addUniqueSuffixForDuplicates, true);
        }
        catch (NameGenerator.DuplicateNameException dup)
        {
            throw new ExperimentException("Duplicate name '" + dup.getName() + "' on row " + dup.getRowNumber(), dup);
        }
        catch (NameGenerator.NameGenerationException e)
        {
            // Failed to generate a name due to some part of the expression not in the row
            if (hasNameExpression())
                throw new ExperimentException("Failed to generate name for Sample on row " + e.getRowNumber(), e);
            else if (hasNameAsIdCol())
                throw new ExperimentException("Name is required for Sample on row " + e.getRowNumber(), e);
            else
                throw new ExperimentException("All id columns are required for Sample on row " + e.getRowNumber(), e);
        }
    }

    @Override
    public String createSampleName(@NotNull Map<String, Object> rowMap) throws ExperimentException
    {
        return createSampleName(rowMap, null, null);
    }

    @Override
    public String createSampleName(@NotNull Map<String, Object> rowMap,
                                   @Nullable Set<ExpData> parentDatas,
                                   @Nullable Set<ExpMaterial> parentSamples)
            throws ExperimentException
    {
        NameGenerator nameGen = getNameGenerator();
        if (nameGen == null)
            throw new ExperimentException("Error creating name expression generator");

        try
        {
            return nameGen.generateName(rowMap, parentDatas, parentSamples, true);
        }
        catch (NameGenerator.NameGenerationException e)
        {
            throw new ExperimentException("Failed to generate name for Sample", e);
        }
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
