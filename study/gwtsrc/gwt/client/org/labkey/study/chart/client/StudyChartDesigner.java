/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

package gwt.client.org.labkey.study.chart.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import gwt.client.org.labkey.study.StudyApplication;
import org.labkey.api.gwt.client.model.GWTChart;
import org.labkey.api.gwt.client.ui.*;
import org.labkey.api.gwt.client.ui.reports.AbstractChartPanel;
import org.labkey.api.gwt.client.ui.reports.ChartAxisOptionsPanel;
import org.labkey.api.gwt.client.ui.reports.ChartMeasurementsPanel;
import org.labkey.api.gwt.client.ui.reports.ChartPreviewPanel;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;
import gwt.client.org.labkey.study.chart.client.model.GWTPair;

import java.util.*;

/**
 * User: Karl Lum
 * Date: Dec 7, 2007
 */
public class StudyChartDesigner extends AbstractChartPanel implements EntryPoint
{
    private RootPanel _root = null;
    private Label _loading = null;
    private String _redirectUrl;
    private String _datasetId;
    private String _participantId;
    private boolean _isParticipantChart;
    private String _subjectNounSingular;

    private StudyChartServiceAsync _service;
    private GWTChart _chart;

    private FlexTable _buttons;

    public void onModuleLoad()
    {
        _root = StudyApplication.getRootPanel();

        _redirectUrl = PropertyUtil.getRedirectURL();
        _datasetId = PropertyUtil.getServerProperty("datasetId");
        _participantId = PropertyUtil.getServerProperty("participantId");
        String participantChart = PropertyUtil.getServerProperty("isParticipantChart");
        if (participantChart != null)
            _isParticipantChart = Boolean.valueOf(participantChart).booleanValue();
        _subjectNounSingular = PropertyUtil.getServerProperty("subjectNounSingular");
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
        _service = (StudyChartServiceAsync)getService();

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
        if (!_isParticipantChart)
            panel.setWidget(row++, 0, createStudyOptions());
        panel.setWidget(row++, 0, new ChartPreviewPanel(_chart, _service).createWidget());

        return panel;
    }

    private Composite createStudyOptions()
    {
        // participant chart check
        FlexTable panel = new FlexTable();
        int row = 0;

        String subjectNounLower = Character.toLowerCase(_subjectNounSingular.charAt(0)) + _subjectNounSingular.substring(1);
        String subjectNounUpper = Character.toUpperCase(_subjectNounSingular.charAt(0)) + _subjectNounSingular.substring(1);
        BoundCheckBox participant = new BoundCheckBox(subjectNounUpper + " Chart", false, new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                if (((CheckBox)widget).isChecked())
                    _chart.setProperty("filterParam", "participantId");
                else
                    _chart.setProperty("filterParam", "");
            }
        });
        participant.setName("participantChart");
        HorizontalPanel hp = new HorizontalPanel();
        hp.add(participant);
        hp.add(new HelpPopup(subjectNounUpper + " Chart", subjectNounUpper + " chart views show measures for only one " + subjectNounLower +
                " at a time. " + subjectNounUpper + " chart views allow the user to step through charts for each " +
                subjectNounLower + " shown in any dataset grid."));
        panel.setWidget(row, 0, hp);

        return new WebPartPanel("Study Options", panel);
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

            if (_datasetId != null)
                chart.setProperty("datasetId", _datasetId);
            if (_participantId != null)
                chart.setProperty("participantId", _participantId);
        }
        return chart;
    }

    public ChartServiceAsync getService()
    {
        Map<String, String> params = new HashMap<String, String>();
        params.put("isParticipantChart", _isParticipantChart ? "true" : "false");

        StudyChartServiceAsync service = GWT.create(StudyChartService.class);
        ServiceUtil.configureEndpoint(service, "chartService", null, params);

        return service;
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
                SaveDialog dlg = new SaveDialog(_isParticipantChart);
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
        if (null == _redirectUrl || _redirectUrl.length() == 0)
            back();
        else
            navigate(_redirectUrl);
    }


    public static native void navigate(String url) /*-{
      $wnd.location.href = url;
    }-*/;

    public static native void back() /*-{
        $wnd.history.back();
    }-*/;

    private class SaveDialog extends DialogBox
    {
        List<GWTPair> _datasets;
        boolean _isParticipantChart;

        public SaveDialog(boolean isParticipantChart)
        {
            super(false, true);

            _isParticipantChart = isParticipantChart;
            if (!_isParticipantChart)
                asyncGetDatasets();
            else
                showUI();
                
        }

        private void asyncGetDatasets()
        {
            _service.getStudyDatasets(new ErrorDialogAsyncCallback<List<GWTPair>>()
            {
                public void onSuccess(List<GWTPair> result)
                {
                    _datasets = result;
                    showUI();
                }
            });
        }
        private void showUI()
        {
            FlexTable panel = new FlexTable();
            int row = 0;

            BoundTextBox name = new BoundTextBox("reportName", _chart.getReportName(), new WidgetUpdatable()
            {
                public void update(Widget widget)
                {
                    _chart.setReportName(((TextBox)widget).getText());
                }
            });
            name.setWidth("200px");
            panel.setWidget(row, 0, new HTML("Name"));
            panel.setWidget(row++, 1, name);

            if (!_isParticipantChart)
            {
                BoundListBox view = new BoundListBox(false, new WidgetUpdatable()
                {
                    public void update(Widget widget)
                    {
                        ListBox lb = (ListBox)widget;
                        String selected = null;
                        if (lb.getSelectedIndex() != -1)
                            selected = lb.getValue(lb.getSelectedIndex());
                        _chart.setProperty("showWithDataset", selected);
                    }
                });
                String selected = null;
                for (GWTPair pair : _datasets)
                {
                    view.addItem(pair.getKey(), pair.getValue());
                    if (pair.getValue().equals(_datasetId))
                        selected = pair.getKey();
                }
                view.setVisibleItemCount(1);
                if (selected != null)
                    view.setSelected(new String[]{selected});
                else
                    view.setItemSelected(0, true);
                if (view.getSelectedIndex() != -1)
                {
                    String showWithDataset = view.getValue(view.getSelectedIndex());
                    _chart.setProperty("showWithDataset", showWithDataset);
                }
                panel.setWidget(row, 0, new HTML("Add as Custom View for:"));
                panel.setWidget(row++, 1, view);

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
                    share.setName("shareReport");
                    panel.setWidget(row++, 1, share);
                }
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
                        getService().saveChart(_chart, new ErrorDialogAsyncCallback<String>()
                        {
                            public void onSuccess(String result)
                            {
                                _redirectUrl = result;

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
