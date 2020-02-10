package org.labkey.list.model;

import org.labkey.api.data.Entity;
import org.labkey.api.exp.api.DomainKindProperties;
import org.labkey.api.exp.list.ListDefinition;

import java.util.Date;

public class ListDomainKindProperties extends Entity implements DomainKindProperties
{
    protected int listId;
    protected String name;
    protected int domainId;
    protected String keyName;
    protected String keyType;

    protected String titleColumn;
    protected String description;
    protected Date lastIndexed;

    protected ListDefinition.DiscussionSetting discussionSetting = ListDefinition.DiscussionSetting.None;
    protected boolean allowDelete = true;
    protected boolean allowUpload = true;
    protected boolean allowExport = true;

    protected boolean entireListIndex = false;
    protected ListDefinition.IndexSetting entireListIndexSetting = ListDefinition.IndexSetting.MetaData;
    protected ListDefinition.TitleSetting entireListTitleSetting = ListDefinition.TitleSetting.Standard;
    protected String entireListTitleTemplate = null;
    protected ListDefinition.BodySetting entireListBodySetting = ListDefinition.BodySetting.TextOnly;
    protected String entireListBodyTemplate = null;

    protected boolean eachItemIndex = false;
    protected ListDefinition.TitleSetting eachItemTitleSetting = ListDefinition.TitleSetting.Standard;
    protected String eachItemTitleTemplate = null;
    protected ListDefinition.BodySetting eachItemBodySetting = ListDefinition.BodySetting.TextOnly;
    protected String eachItemBodyTemplate = null;

    protected boolean fileAttachmentIndex = false;

    public int getListId()
    {
        return listId;
    }

    public void setListId(int listId)
    {
        this.listId = listId;
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

    public Date getLastIndexed()
    {
        return lastIndexed;
    }

    public void setLastIndexed(Date lastIndexed)
    {
        this.lastIndexed = lastIndexed;
    }

    public ListDefinition.DiscussionSetting getDiscussionSetting()
    {
        return discussionSetting;
    }

    public void setDiscussionSetting(ListDefinition.DiscussionSetting discussionSetting)
    {
        this.discussionSetting = discussionSetting;
    }

    public boolean isAllowDelete()
    {
        return allowDelete;
    }

    public void setAllowDelete(boolean allowDelete)
    {
        this.allowDelete = allowDelete;
    }

    public boolean isAllowUpload()
    {
        return allowUpload;
    }

    public void setAllowUpload(boolean allowUpload)
    {
        this.allowUpload = allowUpload;
    }

    public boolean isAllowExport()
    {
        return allowExport;
    }

    public void setAllowExport(boolean allowExport)
    {
        this.allowExport = allowExport;
    }

    public boolean isEntireListIndex()
    {
        return entireListIndex;
    }

    public void setEntireListIndex(boolean entireListIndex)
    {
        this.entireListIndex = entireListIndex;
    }

    public ListDefinition.IndexSetting getEntireListIndexSetting()
    {
        return entireListIndexSetting;
    }

    public void setEntireListIndexSetting(ListDefinition.IndexSetting entireListIndexSetting)
    {
        this.entireListIndexSetting = entireListIndexSetting;
    }

    public ListDefinition.TitleSetting getEntireListTitleSetting()
    {
        return entireListTitleSetting;
    }

    public void setEntireListTitleSetting(ListDefinition.TitleSetting entireListTitleSetting)
    {
        this.entireListTitleSetting = entireListTitleSetting;
    }

    public String getEntireListTitleTemplate()
    {
        return entireListTitleTemplate;
    }

    public void setEntireListTitleTemplate(String entireListTitleTemplate)
    {
        this.entireListTitleTemplate = entireListTitleTemplate;
    }

    public ListDefinition.BodySetting getEntireListBodySetting()
    {
        return entireListBodySetting;
    }

    public void setEntireListBodySetting(ListDefinition.BodySetting entireListBodySetting)
    {
        this.entireListBodySetting = entireListBodySetting;
    }

    public String getEntireListBodyTemplate()
    {
        return entireListBodyTemplate;
    }

    public void setEntireListBodyTemplate(String entireListBodyTemplate)
    {
        this.entireListBodyTemplate = entireListBodyTemplate;
    }

    public boolean isEachItemIndex()
    {
        return eachItemIndex;
    }

    public void setEachItemIndex(boolean eachItemIndex)
    {
        this.eachItemIndex = eachItemIndex;
    }

    public ListDefinition.TitleSetting getEachItemTitleSetting()
    {
        return eachItemTitleSetting;
    }

    public void setEachItemTitleSetting(ListDefinition.TitleSetting eachItemTitleSetting)
    {
        this.eachItemTitleSetting = eachItemTitleSetting;
    }

    public String getEachItemTitleTemplate()
    {
        return eachItemTitleTemplate;
    }

    public void setEachItemTitleTemplate(String eachItemTitleTemplate)
    {
        this.eachItemTitleTemplate = eachItemTitleTemplate;
    }

    public ListDefinition.BodySetting getEachItemBodySetting()
    {
        return eachItemBodySetting;
    }

    public void setEachItemBodySetting(ListDefinition.BodySetting eachItemBodySetting)
    {
        this.eachItemBodySetting = eachItemBodySetting;
    }

    public String getEachItemBodyTemplate()
    {
        return eachItemBodyTemplate;
    }

    public void setEachItemBodyTemplate(String eachItemBodyTemplate)
    {
        this.eachItemBodyTemplate = eachItemBodyTemplate;
    }

    public boolean isFileAttachmentIndex()
    {
        return fileAttachmentIndex;
    }

    public void setFileAttachmentIndex(boolean fileAttachmentIndex)
    {
        this.fileAttachmentIndex = fileAttachmentIndex;
    }
}
