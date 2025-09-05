package org.labkey.experiment;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class PropertyQueryChangeListener implements QueryChangeListener
{
    @Override
    public void queryCreated(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
    }

    private void updateLookupQuery(String newValue, SchemaKey schema, String oldQuery, Container container)
    {
        String fieldName = "lookupquery";
        TableInfo pdTable = OntologyManager.getTinfoPropertyDescriptor();
        SQLFragment updateSql = new SQLFragment("UPDATE ").append(pdTable)
                .append(" SET ")
                .append(fieldName)
                .append(" = ? WHERE lookupschema = ? AND lookupquery = ? AND ")
                .append("(lookupcontainer = ? OR (lookupcontainer IS NULL AND container = ?))")
                .add(newValue)
                .add(schema.toString())
                .add(oldQuery)
                .add(container)
                .add(container);

        new SqlExecutor(pdTable.getSchema()).execute(updateSql);

    }

    private void updateLookupSchema(String newValue, String oldSchema, Container container)
    {
        String fieldName = "lookupschema";
        TableInfo pdTable = OntologyManager.getTinfoPropertyDescriptor();
        SQLFragment updateSql = new SQLFragment("UPDATE ").append(pdTable)
                .append(" SET ")
                .append(fieldName)
                .append(" = ? WHERE lookupschema = ? AND ")
                .append("(lookupcontainer = ? OR (lookupcontainer IS NULL AND container = ?))")
                .add(newValue)
                .add(oldSchema)
                .add(container)
                .add(container);

        new SqlExecutor(pdTable.getSchema()).execute(updateSql);

    }

    @Override
    public void queryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull QueryProperty property, @NotNull Collection<QueryPropertyChange<?>> changes)
    {
        if (!property.equals(QueryProperty.SchemaName) && !property.equals(QueryProperty.Name)) // Issue 53846
            return;

        // is there any other schema change other than assay renaming?
        boolean isSchemaChange = schema.toString().toLowerCase().startsWith("assay.general.");

        Map<String, String> queryNameChangeMap = new CaseInsensitiveHashMap<>();
        for (QueryPropertyChange<?> qpc : changes)
        {
            String oldVal = qpc.getOldValue() != null ? qpc.getOldValue().toString() : null;
            String newVal = qpc.getNewValue() != null ? qpc.getNewValue().toString() : null;
            if (oldVal != null && !oldVal.equals(newVal))
                queryNameChangeMap.put(oldVal, newVal);
        }

        for (String oldValue : queryNameChangeMap.keySet())
        {
            String newValue = queryNameChangeMap.get(oldValue);
            if (isSchemaChange)
                updateLookupSchema(newValue, oldValue, container);
            else
                updateLookupQuery(newValue, schema, oldValue, container);
        }
    }

    @Override
    public void queryDeleted(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {

    }

    @Override
    public Collection<String> queryDependents(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
        return List.of();
    }
}
