package org.labkey.api.products;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class MenuSection
{
    protected ViewContext _context;
    protected String _label;
    protected Integer _itemLimit;
    protected String _key;
    private List<MenuItem> _allItems;

    public MenuSection(@NotNull ViewContext context, @NotNull String label, @NotNull String key, @Nullable Integer itemLimit)
    {
        _context = context;
        _label = label;
        _key = key;
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

    public String getKey()
    {
        return _key;
    }

    public void setKey(String key)
    {
        _key = key;
    }

    public int getTotalCount()
    {
        return ensureAllItems().size();
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

    private @NotNull List<MenuItem> ensureAllItems()
    {
        if (_allItems == null)
            _allItems = getAllItems();
        return _allItems;
    }

    public List<MenuItem> getItems()
    {
        ensureAllItems().sort(Comparator.comparing(MenuItem::getOrderNum).thenComparing(MenuItem::getLabel, String.CASE_INSENSITIVE_ORDER));
        if (_itemLimit != null && _itemLimit < _allItems.size())
            return _allItems.subList(0, _itemLimit);
        else
            return _allItems;
    }

    protected TableInfo getTableInfo(UserSchema schema, String queryName)
    {
        QuerySettings settings = schema.getSettings(getContext(), QueryView.DATAREGIONNAME_DEFAULT, queryName);
        QueryDefinition def = settings.getQueryDef(schema);
        return def.getTable(new ArrayList<>(), true);
    }

    protected String getLabel(UserSchema schema, String queryName, boolean splitCamelCase)
    {
        TableInfo tableInfo = getTableInfo(schema, queryName);
        String label = tableInfo.getTitle();
        if (label == null)
            label = tableInfo.getName();
        return splitCamelCase ? StringUtilsLabKey.splitCamelCase(label) : label;
    }
}
