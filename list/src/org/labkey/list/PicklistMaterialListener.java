package org.labkey.list;

import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExperimentListener;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.security.User;
import org.labkey.list.model.ListDef;
import org.labkey.list.model.ListManager;
import org.labkey.list.model.ListQuerySchema;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class PicklistMaterialListener implements ExperimentListener
{
    @Override
    public void beforeMaterialDelete(List<? extends ExpMaterial> materials, Container container, User user)
    {
        Collection<ListDef> picklists = ListManager.get().getPicklists(container);
        List<Integer> materialIds = materials.stream().map(ExpMaterial::getRowId).collect(Collectors.toList());
        picklists.forEach(picklist -> {
            ListQuerySchema listQuerySchema = new ListQuerySchema(user, container);
            TableInfo table = listQuerySchema.getTable(picklist.getName());

            if (table != null)
            {
                SimpleFilter filter = new SimpleFilter();
                filter.addInClause(FieldKey.fromParts("SampleID"), materialIds);

                Table.delete(((FilteredTable) table).getRealTable(), filter);
            }
        });
    }

}
