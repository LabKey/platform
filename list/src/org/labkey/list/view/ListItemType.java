package org.labkey.list.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.ContainerManager;
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
    public @Nullable String getSelectSqlForIds()
    {
        StringBuilder sql = new StringBuilder();
        ListService svc = ListService.get();

        assert null != svc;

        ContainerManager.getAllChildren(ContainerManager.getRoot()).forEach(c -> {
            Map<String, ListDefinition> map = svc.getLists(c);
            map.forEach((k, v) -> {
                Domain domain = v.getDomain();
                if (null != domain && domain.getProperties().stream().anyMatch(p -> p.getPropertyType() == PropertyType.ATTACHMENT))
                {
                    sql.append("SELECT EntityId AS ID FROM list.").append(domain.getStorageTableName());
                    sql.append("\nUNION\n");
                }
            });
        });

        return sql.length() > 0 ? sql.delete(sql.length() - 7, sql.length()).toString() : null;
    }
}
