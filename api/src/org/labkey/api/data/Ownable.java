package org.labkey.api.data;

/**
 * User: jeckels
 * Date: Dec 20, 2005
 */
public interface Ownable
{
    public int getModifiedBy();
    public void setModifiedBy(int modifiedBy);
    public int getCreatedBy();
    public void setCreatedBy(int createdBy);

    public String getContainerId();
}
