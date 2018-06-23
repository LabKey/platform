/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTChart;
import org.labkey.api.gwt.client.ui.ChartServiceAsync;
import org.labkey.api.gwt.client.ui.HelpPopup;
import org.labkey.api.gwt.client.ui.WebPartPanel;
import org.labkey.api.gwt.client.ui.WidgetUpdatable;

/**
 * User: Karl Lum
 * Date: Dec 6, 2007
 */
public class ChartAxisOptionsPanel extends AbstractChartPanel
{
    private BoundRadioButton _singlePlot;
    private BoundRadioButton _multiPlot;

    private BoundCheckBox _multipleYAxis;
    private HelpPopup _multipleYAxisHP;
    private BoundRadioButton _orientationVert;
    private BoundRadioButton _orientationHoriz;

    
    public ChartAxisOptionsPanel(GWTChart chart, ChartServiceAsync service)
    {
        super(chart, service);
    }

    public Widget createWidget()
    {
        int row = 0;
        FlexTable panel = new FlexTable();

        BoundTextBox height = new BoundTextBox("height", Integer.toString(getChart().getHeight()), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                String text = ((TextBox)widget).getText();
                getChart().setHeight(Integer.parseInt(text));
            }
        });
        panel.setWidget(row, 0, new HTML("Height (px)"));
        panel.setWidget(row, 1, height);

        BoundCheckBox logX = new BoundCheckBox("Logarithmic X Scale", getChart().isLogX(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                getChart().setLogX(((CheckBox)widget).isChecked());
            }
        });
        panel.setWidget(row, 2, logX);

        BoundCheckBox showLines = new BoundCheckBox("Show Lines between Points", getChart().isShowLines(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                getChart().setShowLines(((CheckBox)widget).isChecked());
            }
        });
        panel.setWidget(row++, 3, showLines);


        BoundTextBox width = new BoundTextBox("width", Integer.toString(getChart().getWidth()), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                String text = ((TextBox)widget).getText();
                getChart().setWidth(Integer.parseInt(text));
            }
        });
        panel.setWidget(row, 0, new HTML("Width (px)"));
        panel.setWidget(row, 1, width);

        BoundCheckBox logY = new BoundCheckBox("Logarithmic Y Scale", getChart().isLogY(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                getChart().setLogY(((CheckBox)widget).isChecked());
            }
        });
        panel.setWidget(row++, 2, logY);

        row++;
        _singlePlot = new BoundRadioButton("combinedChart", "Single plot option", !getChart().isShowMultipleCharts(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                getChart().setShowMultipleCharts(!((RadioButton)widget).isChecked());
                reset();
            }
        });
        HorizontalPanel hp = new HorizontalPanel();
        hp.add(_singlePlot);
        hp.add(new HelpPopup("Single Plot", "All vertical axis measurements will be plotted with the horizontal measurement on the same chart"));

        panel.getFlexCellFormatter().setColSpan(row, 0, 2);
        panel.setWidget(row++, 0, hp);

        _multipleYAxis = new BoundCheckBox("Multiple Y axis", getChart().isShowMultipleCharts(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                getChart().setShowMultipleYAxis(((CheckBox)widget).isChecked());
            }
        });
        hp = new HorizontalPanel();
        hp.add(_multipleYAxis);
        _multipleYAxisHP = new HelpPopup("Multiple Y Axis", "A separate Y axis will be drawn for each vertical axis measurement");
        hp.add(_multipleYAxisHP);
        panel.setWidget(row++, 1, hp);


        _multiPlot = new BoundRadioButton("combinedChart", "Multiple plot option", getChart().isShowMultipleCharts(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                getChart().setShowMultipleCharts(((RadioButton)widget).isChecked());
                reset();
            }
        });
        hp = new HorizontalPanel();
        hp.add(_multiPlot);
        hp.add(new HelpPopup("Multiple Plots", "Each vertical axis measurement will be plotted with the horizontal measurement on a separate chart"));

        panel.getFlexCellFormatter().setColSpan(row, 0, 2);
        panel.setWidget(row++, 0, hp);

        _orientationVert = new BoundRadioButton("orientation", "Vertical Orientation", getChart().isVerticalOrientation(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                getChart().setVerticalOrientation(((RadioButton)widget).isChecked());
            }
        });
        HorizontalPanel vert = new HorizontalPanel();
        vert.add(_orientationVert);

        panel.setWidget(row++, 1, vert);

        _orientationHoriz = new BoundRadioButton("orientation", "Horizontal Orientation", !getChart().isVerticalOrientation(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                getChart().setVerticalOrientation(!((RadioButton)widget).isChecked());
            }
        });
        HorizontalPanel horiz = new HorizontalPanel();
        horiz.add(_orientationHoriz);
        panel.setWidget(row++, 1, horiz);

        WebPartPanel wpp = new WebPartPanel("Axis Options", panel);
        wpp.setWidth("100%");

        reset();
        return wpp;
    }

    private void reset()
    {
        boolean showSingle = _singlePlot.isChecked();

        _multipleYAxis.setVisible(showSingle);
        _multipleYAxisHP.setVisible(showSingle);

        _orientationHoriz.setVisible(!showSingle);
        _orientationVert.setVisible(!showSingle);
    }
}
