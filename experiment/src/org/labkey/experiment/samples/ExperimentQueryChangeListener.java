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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.labkey.api.exp.api.ExperimentJSONConverter.DATA_INPUTS;
import static org.labkey.api.exp.api.ExperimentJSONConverter.MATERIAL_INPUTS;

public class ExperimentQueryChangeListener implements QueryChangeListener
{
    public static final String MATERIAL_INPUTS_ALIAS_PREFIX = MATERIAL_INPUTS + "/";
    public static final String DATA_INPUTS_ALIAS_PREFIX = DATA_INPUTS + "/";

    @Override
    public void queryCreated(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {

    }

    private List<ExpSampleTypeImpl> getRenamedSampleTypes(Container container, String searchStr)
    {
        SimpleFilter sampleTypeFilter = SimpleFilter.createContainerFilter(container, "Container");
        sampleTypeFilter.addCondition(FieldKey.fromParts("materialparentimportaliasmap"), searchStr, CompareType.CONTAINS);
        TableInfo sampleTypeTable = ExperimentServiceImpl.get().getTinfoSampleType();

        return new TableSelector(sampleTypeTable, sampleTypeFilter, null).stream(MaterialSource.class)
                .map(ExpSampleTypeImpl::new)
                .toList();
    }

    private List<ExpDataClassImpl> getRenamedDataClasses(Container container, String searchStr)
    {
        SimpleFilter dataClassFilter = SimpleFilter.createContainerFilter(container, "Container");
        dataClassFilter.addCondition(FieldKey.fromParts("dataparentimportaliasmap"), searchStr, CompareType.CONTAINS);
        TableInfo dataClassTable = ExperimentServiceImpl.get().getTinfoDataClass();
        return new TableSelector(dataClassTable, dataClassFilter, null).stream(DataClass.class)
                .map(ExpDataClassImpl::new)
                .toList();
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
            String oldVal = (String)qpc.getOldValue();
            String newVal = (String)qpc.getNewValue();
            if (oldVal != null && !oldVal.equals(newVal))
                queryNameChangeMap.put((String)qpc.getOldValue(), (String)qpc.getNewValue());
        }

        String prefix = isSamples ? MATERIAL_INPUTS_ALIAS_PREFIX : DATA_INPUTS_ALIAS_PREFIX;

        for (String oldQueryName : queryNameChangeMap.keySet())
        {
            String newQueryName = queryNameChangeMap.get(oldQueryName);

            String searchStr = "\"" + prefix + oldQueryName + "\"";
            String replaceStr = "\"" + prefix + newQueryName + "\"";

            for (ExpSampleTypeImpl sampleType : getRenamedSampleTypes(container, searchStr))
            {
                String updatedAlias = sampleType.getImportAliasJson().replaceAll("(?i)" + searchStr, replaceStr);
                sampleType.setImportAliasMapJson(updatedAlias);
                sampleType.save(sampleType.getModifiedBy());
            }

            for (ExpDataClassImpl dataClass : getRenamedDataClasses(container, searchStr))
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

        String prefix = isSamples ? MATERIAL_INPUTS_ALIAS_PREFIX : DATA_INPUTS_ALIAS_PREFIX;

        for (String removed : queries)
        {
            String inputType = prefix + removed;
            String searchStr = "\"" + inputType + "\"";

            for (ExpSampleTypeImpl sampleType : getRenamedSampleTypes(container, searchStr))
            {
                try
                {
                    Map<String, Map<String, Object>> originalMap = sampleType.getImportAliasMap();
                    Map<String, Map<String, Object>> updatedMap = new HashMap<>();
                    for (String alias : originalMap.keySet())
                    {
                        String dataType = (String) originalMap.get(alias).get("inputType");
                        if (!dataType.equals(inputType))
                            updatedMap.put(alias, originalMap.get(alias));
                    }
                    sampleType.setImportAliasMap(updatedMap);
                    sampleType.save(sampleType.getModifiedBy());
                }
                catch (IOException e)
                {
                    LogManager.getLogger(ExperimentQueryChangeListener.class).error("An error occurred updating sample type alias map: ", e);
                }
            }

            for (ExpDataClassImpl dataClass : getRenamedDataClasses(container, searchStr))
            {
                try
                {
                    Map<String, Map<String, Object>> originalMap = dataClass.getImportAliasMap();
                    Map<String, Map<String, Object>> updatedMap = new HashMap<>();
                    for (String alias : originalMap.keySet())
                    {
                        String dataType = (String) originalMap.get(alias).get("inputType");
                        if (!dataType.equals(inputType))
                            updatedMap.put(alias, originalMap.get(alias));
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
        return Collections.emptyList();
    }
}
