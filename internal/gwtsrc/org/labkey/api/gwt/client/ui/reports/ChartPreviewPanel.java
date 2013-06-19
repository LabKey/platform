/*
 * Copyright (c) 2007-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.gwt.client.ui.reports;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import org.labkey.api.gwt.client.model.GWTChart;
import org.labkey.api.gwt.client.ui.ChartServiceAsync;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.WebPartPanel;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Karl Lum
 * Date: Dec 6, 2007
 */
public class ChartPreviewPanel extends AbstractChartPanel
{
    private VerticalPanel _panel;
    private Image _image;

    public ChartPreviewPanel(GWTChart chart, ChartServiceAsync service)
    {
        super(chart, service);
    }

    protected boolean validate()
    {
        List<String> errors = new ArrayList<String>();

        getChart().validate(errors);

        if (!errors.isEmpty())
        {
            String s = "";
            for (String error : errors)
                s += error + "\n";
            Window.alert(s);
            return false;
        }
        return true;
    }

    public Widget createWidget()
    {
        _panel = new VerticalPanel();
        _panel.setStyleName("chart-preview");

        ImageButton plotButton = new ImageButton("Refresh Chart");
        plotButton.addClickHandler(new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                if (validate())
                {
                    if (_image != null)
                    {
                        _panel.remove(_image);
                        _image = null;
                    }
                    asyncPlotChart();
                }
            }
        });
        _panel.add(plotButton);
        WebPartPanel wpp = new WebPartPanel("Chart Preview", _panel);
        wpp.setWidth("100%");

        return wpp;
    }

    private void asyncPlotChart()
    {
        getService().getDisplayURL(getChart(), new ErrorDialogAsyncCallback<String>()
        {
            public void onSuccess(String result)
            {
                _image = new Image(result);
                _panel.add(_image);
            }
        });
    }
}
