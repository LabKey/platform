package org.labkey.api.products;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class MenuSection
{
    protected ViewContext _context;
    protected String _label;
    protected String _iconClass;
    protected int _totalCount;
    protected Integer _itemLimit;

    public MenuSection(@NotNull ViewContext context, @NotNull String label, @Nullable String iconClass, @Nullable Integer itemLimit)
    {
        _context = context;
        _label = label;
        _iconClass = iconClass;
        _itemLimit = itemLimit;
    }


    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public String getIconClass()
    {
        return _iconClass;
    }

    public void setIconClass(String iconClass)
    {
        _iconClass = iconClass;
    }


    public void setTotalCount(int totalCount)
    {
        _totalCount = totalCount;
    }

    public int getTotalCount()
    {
        return _totalCount;
    }

    @JsonIgnore
    public ViewContext getContext()
    {
        return _context;
    }

    public void setContext(ViewContext context)
    {
        _context = context;
    }

    @JsonIgnore
    public Container getContainer()
    {
        return _context.getContainer();
    }

    @JsonIgnore
    public User getUser()
    {
        return _context.getUser();
    }

    public Integer getItemLimit()
    {
        return _itemLimit;
    }

    public void setItemLimit(Integer itemLimit)
    {
        _itemLimit = itemLimit;
    }

    // Might be handy if we implement linking deep into LabKey server from application, but also for URLResolver
    // There won't be one of these for each category, though, so default is null.
    public @Nullable ActionURL getUrl()
    {
        return null;
    }

    @JsonIgnore
    protected abstract @NotNull List<MenuItem> getAllItems();

    public List<MenuItem> getItems()
    {
        List<MenuItem> items = getAllItems();
        items.sort(Comparator.comparing(MenuItem::getOrderNum).thenComparing(MenuItem::getLabel, String.CASE_INSENSITIVE_ORDER));
        if (_itemLimit != null && _itemLimit < items.size())
            return items.subList(0, _itemLimit);
        else
            return items;
    }

    protected TableInfo getTableInfo(UserSchema schema, String queryName)
    {
        QuerySettings settings = schema.getSettings(getContext(), QueryView.DATAREGIONNAME_DEFAULT, queryName);
        QueryDefinition def = settings.getQueryDef(schema);
        return def.getTable(new ArrayList<>(), true);
    }

    @NotNull
    protected String getLabel(UserSchema schema, String queryName)
    {
        TableInfo tableInfo = getTableInfo(schema, queryName);
        String label = tableInfo.getTitle();
        if (label == null)
            label = tableInfo.getName();
        String[] parts = StringUtils.splitByCharacterTypeCamelCase(label);

        StringBuilder labelBuilder = new StringBuilder();
        String space = "";
        for (String part : parts)
        {
            if (!part.isBlank())
                labelBuilder.append(space).append(part);
            space = " ";
        }
        return labelBuilder.toString();
    }
}
