package org.labkey.query.reports.getdata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: jeckels
 * Date: 5/15/13
 */
public class DataRequest
{
    private final ReportDataSource _source;
    private final List<ReportDataTransform> _transforms;
    private final ReportDataRenderer _renderer;

    public DataRequest(ReportDataSource source, List<ReportDataTransform> transforms, ReportDataRenderer renderer)
    {
        _source = source;
        _transforms = Collections.unmodifiableList(new ArrayList<>(transforms));
        _renderer = renderer;
    }

}
