package org.labkey.api.ehr;

import org.json.JSONObject;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 3/3/13
 * Time: 9:00 PM
 */
public interface HistoryRow
{
    public JSONObject toJSON();

    public void setShowTime(Boolean showTime);

    public String getSortDateString();
}
