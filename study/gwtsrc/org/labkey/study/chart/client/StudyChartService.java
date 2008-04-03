package org.labkey.study.chart.client;

import org.labkey.api.gwt.client.model.GWTChart;
import org.labkey.api.gwt.client.ui.ChartService;

import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Dec 7, 2007
 */
public interface StudyChartService extends ChartService
{
    /**
     * Map of dataset names to ids
     *
     * @gwt.typeArgs <org.labkey.study.chart.client.model.GWTPair>
     */
    public List getStudyDatasets() throws Exception;
    
    /**
     * @return a list of errors
     *
     * @gwt.typeArgs <java.lang.String>
     */
    public List saveCharts(GWTChart[] chart, Map properties) throws Exception;
}
