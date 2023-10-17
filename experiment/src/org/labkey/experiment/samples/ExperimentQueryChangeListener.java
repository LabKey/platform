package org.labkey.experiment.samples;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.experiment.api.DataClass;
import org.labkey.experiment.api.ExpDataClassImpl;
import org.labkey.experiment.api.ExpSampleTypeImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.MaterialSource;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExperimentQueryChangeListener implements QueryChangeListener
{
    @Override
    public void queryCreated(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {

    }

    @Override
    public void queryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull QueryProperty property, @NotNull Collection<QueryPropertyChange> changes)
    {
        boolean isSamples = schema.toString().equalsIgnoreCase("samples");
        boolean isData = schema.toString().equalsIgnoreCase("exp.data");
        if (!isSamples && !isData)
            return;

        if (!property.equals(QueryProperty.Name))
            return;

        Map<String, String> queryNameChangeMap = new CaseInsensitiveHashMap<>();
        for (QueryPropertyChange qpc : changes)
        {
            queryNameChangeMap.put((String)qpc.getOldValue(), (String)qpc.getNewValue());
        }

        String prefix = isSamples ? "materialInputs/" : "dataInputs/";

        for (String oldQueryName : queryNameChangeMap.keySet())
        {
            String newQueryName = queryNameChangeMap.get(oldQueryName);
            if (oldQueryName.equals(newQueryName))
                continue;

            String searchStr = "\"" + prefix + oldQueryName + "\"";
            String replaceStr = "\"" + prefix + newQueryName + "\"";

            SimpleFilter sampleTypeFilter = SimpleFilter.createContainerFilter(container, "Container");
            sampleTypeFilter.addCondition(FieldKey.fromParts("materialparentimportaliasmap"), searchStr, CompareType.CONTAINS);
            TableInfo sampleTypeTable = ExperimentServiceImpl.get().getTinfoSampleType();
            List<ExpSampleTypeImpl> sampleTypes = new TableSelector(sampleTypeTable, sampleTypeFilter, null).stream(MaterialSource.class)
                    .map(ExpSampleTypeImpl::new)
                    .toList();
            for (ExpSampleTypeImpl sampleType : sampleTypes)
            {
                String updatedAlias = sampleType.getImportAliasJson().replaceAll("(?i)" + searchStr, replaceStr);
                sampleType.setImportAliasMapJson(updatedAlias);
                sampleType.save(sampleType.getModifiedBy());
            }

            SimpleFilter dataClassFilter = SimpleFilter.createContainerFilter(container, "Container");
            dataClassFilter.addCondition(FieldKey.fromParts("dataparentimportaliasmap"), searchStr, CompareType.CONTAINS);
            TableInfo dataClassTable = ExperimentServiceImpl.get().getTinfoDataClass();
            List<ExpDataClassImpl> dataClasses = new TableSelector(dataClassTable, dataClassFilter, null).stream(DataClass.class)
                    .map(ExpDataClassImpl::new)
                    .toList();
            for (ExpDataClassImpl dataClass : dataClasses)
            {
                String updatedAlias = dataClass.getImportAliasJson().replaceAll("(?i)" + searchStr, replaceStr);
                dataClass.setImportAliasMapJson(updatedAlias);
                dataClass.save(dataClass.getModifiedBy());
            }
        }

    }

    @Override
    public void queryDeleted(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
        boolean isSamples = schema.toString().equalsIgnoreCase("samples");
        boolean isData = schema.toString().equalsIgnoreCase("exp.data");
        if (!isSamples && !isData)
            return;

        String prefix = isSamples ? "materialInputs/" : "dataInputs/";

        for (String removed : queries)
        {
            String searchStr = "\"" + prefix + removed + "\"";

            SimpleFilter sampleTypeFilter = SimpleFilter.createContainerFilter(container, "Container");
            sampleTypeFilter.addCondition(FieldKey.fromParts("materialparentimportaliasmap"), searchStr, CompareType.CONTAINS);
            TableInfo sampleTypeTable = ExperimentServiceImpl.get().getTinfoSampleType();
            List<ExpSampleTypeImpl> sampleTypes = new TableSelector(sampleTypeTable, sampleTypeFilter, null).stream(MaterialSource.class)
                    .map(ExpSampleTypeImpl::new)
                    .toList();
            for (ExpSampleTypeImpl sampleType : sampleTypes)
            {
                try
                {
                    Map<String, String> originalMap = sampleType.getImportAliasMap();
                    Map<String, String> updatedMap = new HashMap<>();
                    for (String alias : originalMap.keySet())
                    {
                        String dataType = originalMap.get(alias);
                        if (!dataType.equals(searchStr))
                            updatedMap.put(alias, dataType);
                    }
                    sampleType.setImportAliasMap(updatedMap);
                    sampleType.save(sampleType.getModifiedBy());
                }
                catch (IOException e)
                {
                    LogManager.getLogger(ExperimentQueryChangeListener.class).error("An error occurred updating sample type alias map: ", e);
                }
            }

            SimpleFilter dataClassFilter = SimpleFilter.createContainerFilter(container, "Container");
            dataClassFilter.addCondition(FieldKey.fromParts("dataparentimportaliasmap"), searchStr, CompareType.CONTAINS);
            TableInfo dataClassTable = ExperimentServiceImpl.get().getTinfoDataClass();
            List<ExpDataClassImpl> dataClasses = new TableSelector(dataClassTable, dataClassFilter, null).stream(DataClass.class)
                    .map(ExpDataClassImpl::new)
                    .toList();
            for (ExpDataClassImpl dataClass : dataClasses)
            {
                try
                {
                    Map<String, String> originalMap = dataClass.getImportAliasMap();
                    Map<String, String> updatedMap = new HashMap<>();
                    for (String alias : originalMap.keySet())
                    {
                        String dataType = originalMap.get(alias);
                        if (!dataType.equals(searchStr))
                            updatedMap.put(alias, dataType);
                    }
                    dataClass.setImportAliasMap(updatedMap);
                    dataClass.save(dataClass.getModifiedBy());
                }
                catch (IOException e)
                {
                    LogManager.getLogger(ExperimentQueryChangeListener.class).error("An error occurred updating dataclass alias map: ", e);
                }
            }
        }
    }

    @Override
    public Collection<String> queryDependents(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
        return null;
    }
}
