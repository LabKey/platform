package org.labkey.list.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;

import java.util.Map;

public class ListItemType implements AttachmentType
{
    private static final ListItemType INSTANCE = new ListItemType();

    public static ListItemType get()
    {
        return INSTANCE;
    }

    private ListItemType()
    {
    }

    @Override
    public @NotNull String getUniqueName()
    {
        return getClass().getName();
    }

    @Override
    public void addWhereSql(SQLFragment sql, String parentColumn, String documentNameColumn)
    {
        StringBuilder unionSql = new StringBuilder();
        ListService svc = ListService.get();

        assert null != svc;

        ContainerManager.getAllChildren(ContainerManager.getRoot()).forEach(c -> {
            Map<String, ListDefinition> map = svc.getLists(c);
            map.forEach((k, v) -> {
                Domain domain = v.getDomain();
                if (null != domain && domain.getProperties().stream().anyMatch(p -> p.getPropertyType() == PropertyType.ATTACHMENT))
                {
                    unionSql.append("\n    SELECT EntityId AS ID FROM list.").append(domain.getStorageTableName());
                    unionSql.append("\n    UNION");
                }
            });
        });

        if (unionSql.length() > 0)
        {
            sql.append(parentColumn).append(" IN (");
            sql.append(unionSql.delete(unionSql.length() - 9, unionSql.length()));
            sql.append(")");
        }
        else
        {
            sql.append("1 = 0");   // No lists with attachment columns
        }
    }
}
