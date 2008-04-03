package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.rpc.AsyncCallback;
import org.labkey.api.gwt.client.model.GWTChart;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Dec 3, 2007
 */
public interface ChartServiceAsync 
{
    void getChart(int id, AsyncCallback async);

    void saveChart(GWTChart chart, AsyncCallback async);

    void getDisplayURL(GWTChart chart, AsyncCallback async);
    
    void getChartRenderers(GWTChart chart, AsyncCallback async);
}
