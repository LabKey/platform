package org.labkey.api.ehr;

import org.json.JSONObject;
import org.labkey.api.data.Container;

/**
 * User: bimber
 * Date: 10/29/13
 * Time: 2:48 PM
 */
public interface EHRQCState
{
    public int getRowId();

    public String getLabel();

    public Container getContainer();

    public String getDescription();

    public Boolean isPublicData();

    public Boolean isDraftData();

    public Boolean isDeleted();

    public Boolean isRequest();

    public Boolean isAllowFutureDates();

    public JSONObject toJson();

}
