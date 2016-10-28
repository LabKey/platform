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

package org.labkey.reports.designer.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTChart;
import org.labkey.api.gwt.client.ui.ChartService;
import org.labkey.api.gwt.client.ui.ChartServiceAsync;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.WidgetUpdatable;
import org.labkey.api.gwt.client.ui.reports.AbstractChartPanel;
import org.labkey.api.gwt.client.ui.reports.ChartAxisOptionsPanel;
import org.labkey.api.gwt.client.ui.reports.ChartMeasurementsPanel;
import org.labkey.api.gwt.client.ui.reports.ChartPreviewPanel;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Karl Lum
 * Date: Nov 30, 2007
 */
public class ChartDesigner extends AbstractChartPanel implements EntryPoint
{
    private RootPanel _root = null;
    private Label _loading = null;
    private String _returnURL;


    private ChartServiceAsync _service;
    private GWTChart _chart;

    private FlexTable _buttons;

    public void onModuleLoad()
    {
        _root = RootPanel.get("org.labkey.reports.designer.ChartDesigner-Root");

        _returnURL = PropertyUtil.getReturnURL();
        String isAdmin = PropertyUtil.getServerProperty("isAdmin");
        _isAdmin = isAdmin != null ? Boolean.valueOf(isAdmin).booleanValue() : false;
        String isGuest = PropertyUtil.getServerProperty("isGuest");
        _isGuest = isGuest != null ? Boolean.valueOf(isGuest).booleanValue() : false;

        _buttons = new FlexTable();
        _buttons.setWidget(0, 0, new CancelButton());
        if (!_isGuest)
            _buttons.setWidget(0, 1, new SubmitButton());

        _loading = new Label("Loading...");
        _root.add(_loading);

        _chart = getChart();
        _service = getService();

        showUI();
    }

    private void showUI()
    {
        if (_chart != null)
        {
            _root.remove(_loading);
            _root.add(_buttons);
            _root.add(createWidget());
        }
    }

    public Widget createWidget()
    {
        FlexTable panel = new FlexTable();
        int row = 0;

        panel.setWidget(row++, 0, new ChartMeasurementsPanel(_chart, _service).createWidget());
        panel.setWidget(row++, 0, new ChartAxisOptionsPanel(_chart, _service).createWidget());
        panel.setWidget(row++, 0, new ChartPreviewPanel(_chart, _service).createWidget());

        return panel;
    }

    public GWTChart getChart()
    {
        GWTChart chart = null;
        String reportId = PropertyUtil.getServerProperty("reportId");
        if (reportId == null)
        {
            chart = new GWTChart();

            chart.setReportType(PropertyUtil.getServerProperty("reportType"));
            chart.setQueryName(PropertyUtil.getServerProperty("queryName"));
            chart.setSchemaName(PropertyUtil.getServerProperty("schemaName"));
            chart.setViewName(PropertyUtil.getServerProperty("viewName"));
            chart.setChartType(PropertyUtil.getServerProperty("chartType"));
            chart.setHeight(Integer.parseInt(PropertyUtil.getServerProperty("height")));
            chart.setWidth(Integer.parseInt(PropertyUtil.getServerProperty("width")));
        }
        return chart;
    }

    protected boolean validate()
    {
        List<String> errors = new ArrayList<String>();

        _chart.validate(errors);

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

    public ChartServiceAsync getService()
    {
        ChartServiceAsync service = GWT.create(ChartService.class);
        ServiceUtil.configureEndpoint(service, "chartService");

        return service;
    }

    class SubmitButton extends ImageButton
    {
        SubmitButton()
        {
            super("Save");
        }

        public void onClick(Widget sender)
        {
            if (validate())
            {
                SaveDialog dlg = new SaveDialog();
                dlg.setPopupPosition(sender.getAbsoluteLeft() + 25, sender.getAbsoluteTop() + 25);
                dlg.show();
            }
        }
    }

    class CancelButton extends ImageButton
    {
        CancelButton()
        {
            super("Cancel");
        }

        public void onClick(Widget sender)
        {
            cancelForm();
        }
    }

    private void cancelForm()
    {
        if (null == _returnURL || _returnURL.length() == 0)
            back();
        else
            navigate(_returnURL);
    }


    public static native void navigate(String url) /*-{
      $wnd.location.href = url;
    }-*/;
    
    public static native void back() /*-{
        $wnd.history.back();
    }-*/;

    private class SaveDialog extends DialogBox
    {
        public SaveDialog()
        {
            super(false, true);
            createPanel();
        }

        private void createPanel()
        {
            FlexTable panel = new FlexTable();
            int row = 0;

            BoundTextBox name = new BoundTextBox("name", _chart.getReportName(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _chart.setReportName(((TextBox)widget).getText());
                }
            });
            panel.setWidget(row, 0, new HTML("Name"));
            panel.setWidget(row++, 1, name);

            BoundTextArea description = new BoundTextArea("description", "", new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _chart.setReportDescription(((TextArea)widget).getText());
                }
            });
            description.setCharacterWidth(60);
            description.setHeight("40px");

            panel.setWidget(row, 0, new HTML("Description"));
            panel.setWidget(row++, 1, description);

            if (_isAdmin)
            {
                BoundCheckBox share = new BoundCheckBox("Make this view available to all users.", false, new WidgetUpdatable()
                {
                    public void update(Widget widget)
                    {
                        _chart.setShared(((CheckBox)widget).isChecked());
                    }
                });
                panel.setWidget(row++, 1, share);
            }

            ImageButton save = new ImageButton("OK");
            save.addClickHandler(new ClickHandler()
            {
                public void onClick(ClickEvent e)
                {
                    if (_chart.getReportName() == null || _chart.getReportName().length() == 0)
                    {
                        Window.alert("Chart name cannot be blank");
                    }
                    else
                    {
                        getService().saveChart(_chart, new AsyncCallback<String>()
                        {
                            public void onFailure(Throwable caught)
                            {
                                Window.alert(caught.getMessage());
                            }

                            public void onSuccess(String result)
                            {
                                    _returnURL = result;

                                cancelForm();
                            }
                        });
                    }
                }
            });

            ImageButton cancel = new ImageButton("Cancel");
            cancel.addClickHandler(new ClickHandler()
            {
                public void onClick(ClickEvent e)
                {
                    SaveDialog.this.hide();
                }
            });

            HorizontalPanel hp = new HorizontalPanel();
            hp.add(save);
            hp.add(new HTML("&nbsp;"));
            hp.add(cancel);
            panel.setWidget(row++, 1, hp);

            setText("Save Chart View");
            setWidget(panel);
        }
    }
}
