package org.labkey.study.chart.client;

import com.google.gwt.user.client.rpc.AsyncCallback;
import org.labkey.api.gwt.client.model.GWTChart;
import org.labkey.api.gwt.client.ui.ChartServiceAsync;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Dec 7, 2007
 */
public interface StudyChartServiceAsync extends ChartServiceAsync
{
    void getStudyDatasets(AsyncCallback async);
    void saveCharts(GWTChart[] charts, Map properties, AsyncCallback async);
}
