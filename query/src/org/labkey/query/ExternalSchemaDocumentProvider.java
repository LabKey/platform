package org.labkey.query;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.SimpleDocumentResource;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Apr 29, 2010
 * Time: 9:25:48 AM
 */
public class ExternalSchemaDocumentProvider implements SearchService.DocumentProvider
{
    public static final SearchService.SearchCategory externalTableCategory = new SearchService.SearchCategory("externalTable", "External Table");

    public void enumerateDocuments(SearchService.IndexTask t, final @NotNull Container c, Date since)
    {
        final SearchService.IndexTask task = null==t ? ServiceRegistry.get(SearchService.class).defaultTask() : t;

        Runnable r = new Runnable()
        {
            public void run()
            {
                User user = User.getSearchUser();
                DefaultSchema defaultSchema = DefaultSchema.get(user, c);
                Map<String, UserSchema> externalSchemas = QueryService.get().getExternalSchemas(defaultSchema);

                for (UserSchema schema : externalSchemas.values())
                {
                    String schemaName = schema.getName();
                    Set<String> tableNames = schema.getTableNames();

                    for (String tableName : tableNames)
                    {
                        TableInfo table = schema.getTable(tableName);

                        assert tableName.equals(table.getName());

                        StringBuilder body = new StringBuilder();
                        Map<String, Object> props = new HashMap<String,Object>();

                        props.put(SearchService.PROPERTY.categories.toString(), externalTableCategory.toString());
                        props.put(SearchService.PROPERTY.displayTitle.toString(), "Table " + schemaName + "." + tableName);
                        props.put(SearchService.PROPERTY.searchTitle.toString(), schemaName + " " + tableName);

                        if (!StringUtils.isEmpty(table.getDescription()))
                            body.append(table.getDescription()).append("\n");

                        String sep = "";

                        for (ColumnInfo column : table.getColumns())
                        {
                            String n = StringUtils.trimToEmpty(column.getName());
                            String l = StringUtils.trimToEmpty(column.getLabel());
                            if (n.equals(l))
                                l = "";
                            String d = StringUtils.trimToEmpty(column.getDescription());
                            if (n.equals(d) || l.equals(d))
                                d = "";
                            String colProps = StringUtilsLabKey.joinNonBlank(" ", n, l, d);
                            body.append(sep).append(colProps);
                            sep = ",\n";
                        }

                        ActionURL url = QueryService.get().urlFor(user, c, QueryAction.executeQuery, schemaName, tableName);
                        String documentId = "externalTable:" + c.getId() + ":" + schemaName + "." + tableName;
                        SimpleDocumentResource r = new SimpleDocumentResource(
                                new Path(documentId),
                                documentId,
                                c.getId(),
                                "text/plain",
                                body.toString().getBytes(),
                                url,
                                props);
                        task.addResource(r, SearchService.PRIORITY.item);
                    }
                }
            }
        };

        task.addRunnable(r, SearchService.PRIORITY.group);
    }


    public void indexDeleted() throws SQLException
    {
    }
}
