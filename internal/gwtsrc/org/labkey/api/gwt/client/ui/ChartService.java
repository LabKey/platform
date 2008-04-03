package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.rpc.RemoteService;
import org.labkey.api.gwt.client.model.GWTChart;
import org.labkey.api.gwt.client.model.GWTChartRenderer;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Dec 3, 2007
 */
public interface ChartService extends RemoteService
{
    public GWTChart getChart(int id) throws Exception;

    /**
     * @return a redirect url if the save was successful
     */
    public String saveChart(GWTChart chart) throws Exception;

    public String getDisplayURL(GWTChart chart) throws Exception;
    
    public GWTChartRenderer[] getChartRenderers(GWTChart chart) throws Exception;
}
