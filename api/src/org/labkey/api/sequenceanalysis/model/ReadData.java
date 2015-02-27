package org.labkey.api.sequenceanalysis.model;

import java.io.Serializable;
import java.util.Date;

/**
 * Created by bimber on 2/17/2015.
 */
public interface ReadData extends Serializable
{
    public Integer getRowid();

    public Integer getReadset();

    public String getPlatformUnit();

    public String getCenterName();

    public Date getDate();

    public Integer getFileId1();

    public Integer getFileId2();

    public String getDescription();

    public String getContainer();

    public Date getCreated();

    public Integer getCreatedBy();

    public Date getModified();

    public Integer getModifiedBy();
}
