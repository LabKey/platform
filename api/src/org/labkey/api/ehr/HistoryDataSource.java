package org.labkey.api.ehr;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 2/17/13
 * Time: 4:50 PM
 *
 *
 * This class is responsible for selecting rows from a target table,
 * and converting them into the display HTML
 */
public interface HistoryDataSource
{
    public String getName();

    public boolean isAvailable(Container c, User u);

    public List<HistoryRow> getRows(Container c, User u, String subjectId, Date minDate, Date maxDate);
}
