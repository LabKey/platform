package org.labkey.study.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.QueryService;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: davebradlee
 * Date: 11/16/12
 * Time: 11:21 AM
 * To change this template use File | Settings | File Templates.
 */
public class LocationSpecimenListTable extends SpecimenDetailTable
{
    public LocationSpecimenListTable(StudyQuerySchema schema)
    {
        super(schema);
        setName("LocationSpecimenList");

        List<ColumnInfo> defaultColumns = getColumns(
                "GlobalUniqueId, ParticipantId, Visit, Volume, VolumeUnits, " +
                        "DrawTimestamp, ProtocolNumber, PrimaryType, " +
                        "TotalCellCount, Clinic, FirstProcessedByInitials, " +
                        "Freezer, Fr_container, Fr_position, Fr_level1, Fr_level2");

        setDefaultVisibleColumns(QueryService.get().getDefaultVisibleColumns(defaultColumns));
    }
}
