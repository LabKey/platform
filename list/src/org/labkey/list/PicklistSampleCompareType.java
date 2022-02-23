package org.labkey.list;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;

import java.util.Arrays;
import java.util.Collections;

public class PicklistSampleCompareType extends CompareType
{
    public PicklistSampleCompareType()
    {
        super("Samples for picklist", "picklistsamples", "PICKLIST_SAMPLES", true, " matches picklist ", null);
    }

    @Override
    public SimpleFilter.FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
    {
        final String listName = value.toString();
        final User user = (User) QueryService.get().getEnvironment(QueryService.Environment.USER);
        final Container container = (Container) QueryService.get().getEnvironment(QueryService.Environment.CONTAINER);
        if (user == null || container == null)
            return new SimpleFilter.FalseClause();

        ListDefinition listDef = ListService.get().getList(container, listName);
        if (listDef == null || !listDef.isPicklist())
            return new SimpleFilter.FalseClause();

        ContainerFilter cf = ListService.get().getPicklistContainerFilter(container, user, listDef);
        TableInfo listTable = listDef.getTable(user, container, cf);
        Integer[] sampleIds = new TableSelector(listTable, Collections.singleton("SampleID")).getArray(Integer.class);
        return new SimpleFilter.InClause(fieldKey, Arrays.asList(sampleIds));
    }
}
