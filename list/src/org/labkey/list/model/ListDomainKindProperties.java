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

    protected boolean allowDelete = true;
    protected boolean allowUpload = true;
    protected boolean allowExport = true;

    protected int discussionSetting = ListDefinition.DiscussionSetting.None.getValue();

    //Index Entire List as a Single Document
    protected String entireListTitleTemplate = String.valueOf(ListDefinition.TitleSetting.Standard.getValue());
    protected String entireListIndexSetting = String.valueOf(ListDefinition.IndexSetting.MetaData.getValue());
    protected String entireListBodySetting = String.valueOf(ListDefinition.BodySetting.TextOnly.getValue());

    //Index Each Item as a Separate Document
    protected String eachItemTitleTemplate = String.valueOf(ListDefinition.TitleSetting.Standard.getValue());
    protected String eachItemBodySetting = String.valueOf(ListDefinition.BodySetting.TextOnly.getValue());

    protected boolean entireListIndex = false;
    protected String entireListBodyTemplate = null;

    protected boolean eachItemIndex = false;
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

    public int getDiscussionSetting()
    {
        return discussionSetting;
    }

    public void setDiscussionSetting(int discussionSetting)
    {
        this.discussionSetting = discussionSetting;
    }

    public String getEntireListTitleTemplate()
    {
        return entireListTitleTemplate;
    }

    public void setEntireListTitleTemplate(String entireListTitleTemplate)
    {
        this.entireListTitleTemplate = entireListTitleTemplate;
    }

    public String getEntireListIndexSetting()
    {
        return entireListIndexSetting;
    }

    public void setEntireListIndexSetting(String entireListIndexSetting)
    {
        this.entireListIndexSetting = entireListIndexSetting;
    }

    public String getEntireListBodySetting()
    {
        return entireListBodySetting;
    }

    public void setEntireListBodySetting(String entireListBodySetting)
    {
        this.entireListBodySetting = entireListBodySetting;
    }

    public String getEachItemTitleTemplate()
    {
        return eachItemTitleTemplate;
    }

    public void setEachItemTitleTemplate(String eachItemTitleTemplate)
    {
        this.eachItemTitleTemplate = eachItemTitleTemplate;
    }

    public String getEachItemBodySetting()
    {
        return eachItemBodySetting;
    }

    public void setEachItemBodySetting(String eachItemBodySetting)
    {
        this.eachItemBodySetting = eachItemBodySetting;
    }

    public boolean isEntireListIndex()
    {
        return entireListIndex;
    }

    public void setEntireListIndex(boolean entireListIndex)
    {
        this.entireListIndex = entireListIndex;
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
