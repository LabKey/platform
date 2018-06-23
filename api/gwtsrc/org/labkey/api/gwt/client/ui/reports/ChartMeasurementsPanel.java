/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Widget;
import org.labkey.api.gwt.client.model.GWTChart;
import org.labkey.api.gwt.client.model.GWTChartColumn;
import org.labkey.api.gwt.client.model.GWTChartRenderer;
import org.labkey.api.gwt.client.ui.ChartServiceAsync;
import org.labkey.api.gwt.client.ui.HelpPopup;
import org.labkey.api.gwt.client.ui.WebPartPanel;
import org.labkey.api.gwt.client.ui.WidgetUpdatable;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: Karl Lum
 * Date: Dec 6, 2007
 */
public class ChartMeasurementsPanel extends AbstractChartPanel
{
    // map of types to GWTChartRenderer
    private Map<String, GWTChartRenderer> _renderers;

    private Label _loading;
    private FlexTable _panel;
    private BoundListBox _columnsX;
    private BoundListBox _columnsY;

    public ChartMeasurementsPanel(GWTChart chart, ChartServiceAsync service)
    {
        super(chart, service);
        _loading = new Label("Loading...");
    }

    public Widget createWidget()
    {
        _panel = new FlexTable();
        _panel.setWidget(0, 0, _loading);

        WebPartPanel wpp = new WebPartPanel("Selected Measurements", _panel);
        wpp.setWidth("100%");

        asyncGetChartRenderers();

        return wpp;
    }

    private void showUI()
    {
        if (_renderers != null)
        {
            _panel.clear();

            int row = 0;
            _panel.setWidget(row, 0, new HTML("Chart Type"));
            _panel.setWidget(row, 1, new HTML("Horizontal Axis"));

            HorizontalPanel hp = new HorizontalPanel();
            hp.add(new Label("Vertical Axis"));
            hp.add(new HelpPopup("X Axis", "More than one measurement for the vertical axis may be selected."));
            _panel.setWidget(row++, 2, hp);

            Map<String, String> types = new HashMap<String, String>();
            for (GWTChartRenderer renderer : _renderers.values())
            {
                types.put(renderer.getName(), renderer.getType());
            }

            BoundListBox chartType = new BoundListBox(false, new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    ListBox lb = (ListBox)widget;
                    String selected = null;
                    if (lb.getSelectedIndex() != -1)
                        selected = lb.getValue(lb.getSelectedIndex());
                    getChart().setChartType(selected);
                    reset();
                }
            });
            chartType.setColumns(types);
            GWTChartRenderer renderer = _renderers.get(getChart().getChartType());
            if (renderer != null)
                chartType.setSelected(new String[]{renderer.getName()});
            _panel.setWidget(row, 0, chartType);

            _columnsX = new BoundListBox(false, new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    ListBox lb = (ListBox)widget;
                    String selected = null;
                    if (lb.getSelectedIndex() != -1)
                        selected = lb.getValue(lb.getSelectedIndex());
                    getChart().setColumnXName(selected);
                }
            });
            _columnsX.setName("columnsX");
            _panel.setWidget(row, 1, _columnsX);

            _columnsY = new BoundListBox(true, new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    ListBox lb = (ListBox)widget;
                    List<String> selected = new ArrayList<String>();

                    if (lb.getSelectedIndex() != -1)
                    {
                        for (int i=0; i < lb.getItemCount(); i++)
                        {
                            if (lb.isItemSelected(i))
                                selected.add(lb.getValue(i));
                        }
                    }
                    getChart().setColumnYName(selected.toArray(new String[selected.size()]));
                }
            });
            _columnsY.setName("columnsY");
            _panel.setWidget(row++, 2, _columnsY);
        }
    }

    private void reset()
    {
        GWTChartRenderer renderer = _renderers.get(getChart().getChartType());
        if (renderer != null)
        {
            setColumns(_columnsX, renderer.getColumnX());
            setColumns(_columnsY, renderer.getColumnY());
            _chart.setColumnXName(null);
            _chart.setColumnYName(new String[0]);
        }
    }

    private void setColumns(BoundListBox list, List<GWTChartColumn> columns)
    {
        list.clear();

        for (GWTChartColumn col : columns)
        {
            list.addItem(col.getCaption(), col.getAlias());
        }
    }

    private void asyncGetChartRenderers()
    {
        getService().getChartRenderers(getChart(), new ErrorDialogAsyncCallback<List<GWTChartRenderer>>()
        {
            public void handleFailure(String message, Throwable caught)
            {
                _loading.setText("ERROR: " + message);
            }

            public void onSuccess(List<GWTChartRenderer> renderers)
            {
                _renderers = new HashMap<String, GWTChartRenderer>();
                for (GWTChartRenderer renderer : renderers)
                {
                    _renderers.put(renderer.getType(), renderer);
                }
                showUI();
                reset();
            }
        });
    }
}
