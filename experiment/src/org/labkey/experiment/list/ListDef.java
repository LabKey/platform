package org.labkey.experiment.list;

import org.labkey.api.data.CacheKey;
import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.util.UnexpectedException;

public class ListDef extends Entity implements Cloneable
{
    public enum Column
    {
        rowId,
        name,
        domainId,
    }

    static public class Key extends CacheKey<ListDef, Column>
    {
        public Key(Container container)
        {
            super(ListManager.get().getTinfoList(), ListDef.class, container);
        }
    }

    private int rowId;
    private String name;
    private int domainId;
    private String keyName;
    private String keyType;
    private String titleColumn;
    private String description;
    private ListDefinition.DiscussionSetting discussionSetting = ListDefinition.DiscussionSetting.None;
    private boolean allowDelete = true;
    private boolean allowUpload = true;
    private boolean allowExport = true;

    public int getRowId()
    {
        return rowId;
    }

    public void setRowId(int rowId)
    {
        this.rowId = rowId;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public int getDomainId()
    {
        return domainId;
    }

    public void setDomainId(int domainId)
    {
        this.domainId = domainId;
    }

    public String getKeyName()
    {
        return keyName;
    }

    public void setKeyName(String keyName)
    {
        this.keyName = keyName;
    }

    public String getKeyType()
    {
        return keyType;
    }

    public void setKeyType(String keyType)
    {
        this.keyType = keyType;
    }

    public String getTitleColumn()
    {
        return titleColumn;
    }

    public void setTitleColumn(String titleColumn)
    {
        this.titleColumn = titleColumn;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public int getDiscussionSetting()
    {
        return discussionSetting.getValue();
    }

    public void setDiscussionSetting(int value)
    {
        discussionSetting = ListDefinition.DiscussionSetting.getForValue(value);
    }

    public ListDefinition.DiscussionSetting getDiscussionSettingEnum()
    {
        return discussionSetting;
    }

    public void setDiscussionSettingEnum(ListDefinition.DiscussionSetting discussionSetting)
    {
        this.discussionSetting = discussionSetting;
    }

    public boolean getAllowDelete()
    {
        return allowDelete;
    }

    public void setAllowDelete(boolean allowDelete)
    {
        this.allowDelete = allowDelete;
    }

    public boolean getAllowUpload()
    {
        return allowUpload;
    }

    public void setAllowUpload(boolean allowUpload)
    {
        this.allowUpload = allowUpload;
    }

    public boolean getAllowExport()
    {
        return allowExport;
    }

    public void setAllowExport(boolean allowExport)
    {
        this.allowExport = allowExport;
    }

    protected ListDef clone()
    {
        try
        {
            return (ListDef) super.clone();
        }
        catch (CloneNotSupportedException cnse)
        {
            throw UnexpectedException.wrap(cnse);
        }
    }
}
