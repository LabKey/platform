/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.issues.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.BoundTextBox;
import org.labkey.api.gwt.client.ui.DirtyCallback;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.LinkButton;
import org.labkey.api.gwt.client.ui.PropertiesEditor;
import org.labkey.api.gwt.client.ui.Saveable;
import org.labkey.api.gwt.client.ui.WebPartPanel;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 5/12/2016.
 */
public class IssueDesigner implements EntryPoint, Saveable<GWTDomain>
{
    private String _returnURL;
    private String _issueListUrl;
    private String _customizeEmailUrl;

    private List<Map<String, String>> _projects;
    private List<Map<String, String>> _folders;
    private GWTIssueDefinition _issueDef;
    private GWTDomain _domain;

    // UI bits
    private RootPanel _root;
    private FlexTable _buttons;
    private Label _loading;

    private String _typeURI;
    private GeneralProperties _generalPanel;
    private PropertiesEditor _propTable;
    private IssueDomain _domainPanel;
    private DirtySetter _dirtySetter = new DirtySetter();
    private boolean _dirty;
    private boolean _canEditDomain;

    private SubmitButton _saveButton;

    @Override
    public void onModuleLoad()
    {
        String defName = PropertyUtil.getServerProperty("defName");
        _issueListUrl = PropertyUtil.getServerProperty("issueListUrl");
        _customizeEmailUrl = PropertyUtil.getServerProperty("customizeEmailUrl");
        _canEditDomain = Boolean.parseBoolean(PropertyUtil.getServerProperty("canEditDomain"));

        _typeURI = PropertyUtil.getServerProperty("typeURI");

        _root =  RootPanel.get("org.labkey.issues.Designer-Root");
        _propTable = new PropertiesEditor.PD(_root, this, getService());
        _loading = new Label("");
        _root.add(_loading);

        asyncGetProjectGroups();
        asyncGetFolderMoveContainers();
        asyncGetIssueDefinition(defName);
        asyncGetDomainDescriptor(_typeURI, "Loading...");

        Window.addWindowClosingHandler(new Window.ClosingHandler()
        {
            public void onWindowClosing(Window.ClosingEvent event)
            {
                if (isDirty())
                    event.setMessage("Changes have not been saved and will be discarded.");
            }
        });
    }

    public void setProjects(List<Map<String, String>> projects)
    {
        _projects = projects;
        showUI();
    }

    public void setFolderMoveContainers(List<Map<String, String>> folders)
    {
        _folders = folders;
        showUI();
    }

    public void setIssueDef(GWTIssueDefinition issueDef)
    {
        _issueDef = issueDef;
        showUI();
    }

    public void setDomain(GWTDomain d)
    {
        _domain = d;

        _propTable.init(new GWTDomain(d));
        if (null == d.getFields() || d.getFields().size() == 0)
            _propTable.addField(new GWTPropertyDescriptor());

        showUI();
    }


    private void showUI()
    {
        if (_projects != null && _folders != null && _issueDef != null && _domain != null)
        {
            //_root.remove(_loading);
            _loading.setText("");

            if (_buttons == null)
            {
                _buttons = new FlexTable();
                _buttons.getElement().setClassName("gwt-ButtonBar");
                _saveButton = new SubmitButton();
                _buttons.setWidget(0, 0, new LinkButton("Customize Email Template", _customizeEmailUrl));
                _buttons.setWidget(0, 1, _saveButton);
                _buttons.setWidget(0, 2, new LinkButton("Cancel", _issueListUrl));

                _root.add(_buttons);
            }

            if (_generalPanel == null)
            {
                _generalPanel = new GeneralProperties(_folders);
                _root.add(new WebPartPanel("General", _generalPanel));
                _root.add(new HTML("<br/>"));
            }

            if (_domainPanel == null)
            {
                _domainPanel = new IssueDomain(_projects, _propTable);
                _root.add(new WebPartPanel("Configure Fields", _domainPanel));
            }
        }
    }

    @Override
    public String getCurrentURL()
    {
        return PropertyUtil.getCurrentURL();
    }

    public void saveIssueDefinition()
    {
        save(new SaveListener<GWTDomain>()
        {
            public void saveSuccessful(GWTDomain issue, String designerUrl)
            {
                getService().getDomainDescriptor(_typeURI, new ErrorDialogAsyncCallback<GWTDomain>()
                {
                    public void handleFailure(String message, Throwable caught)
                    {
                        _loading.setText("ERROR: " + message);
                    }

                    public void onSuccess(GWTDomain domain)
                    {
                        setDomain(domain);
                        // just return back to the issues list
                        navigate(_issueListUrl);
                    }
                });
            }
        });
    }

    @Override
    public void save()
    {
        save(null);
    }

    @Override
    public void save(final SaveListener<GWTDomain> listener)
    {
        _saveButton.setEnabled(false);

        List<String> errors = new ArrayList<String>();

        _generalPanel.validate(errors);
        _domainPanel.validate(errors);

        if (!errors.isEmpty())
        {
            StringBuilder sb = new StringBuilder();
            for (String error : errors)
                sb.append(error).append("\n");
            Window.alert(sb.toString());
            _saveButton.setEnabled(true);
            return;
        }

        AsyncCallback<List<String>> callback = new ErrorDialogAsyncCallback<List<String>>() {
            public void handleFailure(String message, Throwable caught)
            {
                _saveButton.setEnabled(true);
            }

            public void onSuccess(List<String> errors)
            {
                _saveButton.setEnabled(true);
                if (null == errors || errors.isEmpty())
                {
                    setDirty(false);
                    if (listener != null)
                    {
                        listener.saveSuccessful(_domain, PropertyUtil.getCurrentURL());
                    }
                }
                else
                {
                    StringBuilder sb = new StringBuilder();
                    for (String error : errors)
                        sb.append(error).append("\n");
                    Window.alert(sb.toString());
                }
            }
        };

        // clear out any fake propertids
        GWTDomain<GWTPropertyDescriptor> updates = _propTable.getUpdates();
        for (GWTPropertyDescriptor pd : updates.getFields())
            if (pd.getPropertyId() < 0)
                pd.setPropertyId(0);

        if (_canEditDomain)
            getService().updateIssueDefinition(_issueDef, _domain, _propTable.getUpdates(), callback);
        else
            getService().updateIssueDefinition(_issueDef, _domain, null, callback);
    }

    @Override
    public void cancel()
    {
        back();
    }

    @Override
    public void finish()
    {
        saveIssueDefinition();
    }

    private void setDirty(boolean dirty)
    {
        _dirty = dirty;
    }

    @Override
    public boolean isDirty()
    {
        return _dirty || _propTable.isDirty();
    }

    class SubmitButton extends ImageButton
    {
        SubmitButton()
        {
            super("Save");
        }

        public void onClick(Widget sender)
        {
            saveIssueDefinition();
        }
    }

    public static native void navigate(String url) /*-{
        $wnd.location.href = url;
    }-*/;

    public static native void back() /*-{
        $wnd.history.back();
    }-*/;

    /*
     * SERVER CALLBACKS
     */

    private IssueServiceAsync _service = null;

    private IssueServiceAsync getService()
    {
        if (_service == null)
        {
            _service = GWT.create(IssueService.class);
            ServiceUtil.configureEndpoint(_service, "issueService");
        }
        return _service;
    }

    void asyncGetProjectGroups()
    {
        getService().getProjectGroups(new ErrorDialogAsyncCallback<List<Map<String, String>>>()
        {
            public void handleFailure(String message, Throwable caught)
            {
                _loading.setText("ERROR: " + message);
            }

            public void onSuccess(List<Map<String, String>> projects)
            {
                setProjects(projects);
            }
        });
    }

    void asyncGetFolderMoveContainers()
    {
        getService().getFolderMoveContainers(new ErrorDialogAsyncCallback<List<Map<String, String>>>()
        {
            public void handleFailure(String message, Throwable caught)
            {
                _loading.setText("ERROR: " + message);
            }

            public void onSuccess(List<Map<String, String>> folders)
            {
                setFolderMoveContainers(folders);
            }
        });
    }

    void asyncGetIssueDefinition(final String defName)
    {
        getService().getIssueDefinition(defName, new ErrorDialogAsyncCallback<GWTIssueDefinition>()
        {
            public void handleFailure(String message, Throwable caught)
            {
                _loading.setText("ERROR: " + message);
            }

            public void onSuccess(GWTIssueDefinition issueDefinition)
            {
                setIssueDef(issueDefinition);
            }
        });
    }

    void asyncGetDomainDescriptor(final String domainURI, String message)
    {
        _loading.setText(message);

        getService().getDomainDescriptor(domainURI, new ErrorDialogAsyncCallback<GWTDomain>()
        {
            public void handleFailure(String message, Throwable caught)
            {
                _loading.setText("ERROR: " + message);
            }

            public void onSuccess(GWTDomain domain)
            {
                setDomain(domain);
            }
        });
    }

    private class DirtySetter implements DirtyCallback
    {
        @Override
        public void setDirty(boolean dirty)
        {
            IssueDesigner.this.setDirty(true);
        }
    }

    private class GeneralProperties extends FlexTable
    {
        private List<Map<String, String>> _folders;

        public GeneralProperties(List<Map<String, String>> folders)
        {
            super();
            _folders = folders;

            addStyleName("labkey-pad-cells");
            setCellPadding(2);
            setCellSpacing(0);
            setWidth("100%");

            createPanel();
        }

        private void createPanel()
        {
            String labelStyleName="labkey-form-label";
            FlexCellFormatter cellFormatter = getFlexCellFormatter();

            FlexTable table = new FlexTable();
            table.addStyleName("labkey-pad-cells");
            table.setCellPadding(2);
            table.setCellSpacing(0);

            createItemNameConfiguration(table);
            createCommentSortDirectionConfiguration(table);
            setWidget(0, 0, table);
            //setWidget(0, 2, createFolderMoveConfiguration());
        }

        public void validate(List<String> errors)
        {
        }

        /**
         * Create the widget to handle the item name configuration
         */
        private void createItemNameConfiguration(FlexTable table)
        {
            table.setWidget(0, 0, new Label("Singular item name"));
            table.setWidget(0, 1, new BoundTextBox("", "entrySingularName", _issueDef._singularItemName, _dirtySetter));

            table.setWidget(1, 0, new Label("Plural items name"));
            table.setWidget(1, 1, new BoundTextBox("", "entryPluralName", _issueDef._pluralItemName, _dirtySetter));
        }

        /**
         * Create the widget to handle the comment sort direction configuration
         */
        private void createCommentSortDirectionConfiguration(FlexTable table)
        {
            FlexCellFormatter cellFormatter = getFlexCellFormatter();
            table.setWidget(0, 2, new Label("Comment sort direction"));

            final ListBox sortDirection = new ListBox(false);
            sortDirection.setName("sortDirection");
            sortDirection.addItem("Oldest first", "ASC");
            sortDirection.addItem("Newest first", "DESC");
            sortDirection.setSelectedIndex("DESC".equals(_issueDef.getCommentSortDirection()) ? 1 : 0);
            sortDirection.addChangeHandler(new ChangeHandler(){
                public void onChange(ChangeEvent event)
                {
                    String value = sortDirection.getValue(sortDirection.getSelectedIndex());
                    _issueDef.setCommentSortDirection(value);
                    setDirty(true);
                }
            });
            table.setWidget(0, 3, sortDirection);
        }

        /**
         * Create the widget to handle the folder move configuration
         */
        private Widget createFolderMoveConfiguration()
        {
            HorizontalPanel nonePanel = new HorizontalPanel();
            nonePanel.setSpacing(2);
            RadioButton none = new RadioButton("moveToContainer", "None");
            none.addValueChangeHandler(new ValueChangeHandler<Boolean>()
            {
                @Override
                public void onValueChange(ValueChangeEvent<Boolean> event)
                {
                    event.getValue();
                }
            });
            nonePanel.add(none);

            HorizontalPanel folderPanel = new HorizontalPanel();
            folderPanel.setSpacing(2);
            RadioButton specificFolder = new RadioButton("moveToContainer", "Specific Folder");
            specificFolder.addValueChangeHandler(new ValueChangeHandler<Boolean>()
            {
                @Override
                public void onValueChange(ValueChangeEvent<Boolean> event)
                {
                    event.getValue();
                }
            });
            folderPanel.add(specificFolder);

            // drop down for specific folder
            final ListBox folderList = new ListBox(false);
            Integer idx = 0;
            int selectedIndex = 0;

            for (Map<String, String> folder : _folders)
            {
                folderList.addItem(folder.get("name"), folder.get("value"));
                if (idx.equals(_issueDef.getMoveToContainerSelect()))
                    selectedIndex = idx;
                idx++;
            }
            folderList.setSelectedIndex(selectedIndex);
            folderList.addChangeHandler(new ChangeHandler(){
                public void onChange(ChangeEvent event)
                {
                    String value = folderList.getValue(folderList.getSelectedIndex());
                    setDirty(true);
                }
            });
            folderPanel.add(folderList);

            Panel panel = new VerticalPanel();
            panel.add(new Label("Set move to folder:"));
            panel.add(nonePanel);
            panel.add(folderPanel);

            return panel;
        }
    }

    private class IssueDomain extends FlexTable
    {
        private List<Map<String, String>> _projects;
        private PropertiesEditor _propEdit;
        private ListBox _userList;
        private ListBox _groupList;

        public IssueDomain(List<Map<String, String>> projects, PropertiesEditor propEdit)
        {
            super();
            _projects = projects;
            _propEdit = propEdit;

            addStyleName("labkey-pad-cells"); // padding:5
            setCellPadding(2);   // doesn't work inside extContainer!
            setCellSpacing(0);   // causes spaces in highlight background if != 0

            createPanel();
        }

        private void createPanel()
        {
            FlexCellFormatter cellFormatter = getFlexCellFormatter();

            setWidget(0, 0, createAssignedToConfiguration());
            setWidget(0, 1, createDefaultAssignedToConfiguration());

            // domain editor
            if (_canEditDomain)
            {
                Widget propTable = _propEdit.getWidget();
                setWidget(1, 0, new WebPartPanel("", propTable));
                cellFormatter.setColSpan(1, 0, 2);
            }
        }

        public void validate(List<String> errors)
        {
            errors.addAll(_propEdit.validate());
        }

        /**
         * Create the widget to handle the assigned to configuration
         */
        private Widget createAssignedToConfiguration()
        {
            HorizontalPanel allPanel = new HorizontalPanel();
            allPanel.setSpacing(2);
            RadioButton allUsers = new RadioButton("assignedToMethod", "All Project Users");
            allUsers.setStylePrimaryName("assigned-to-group-project");
            allUsers.setValue((_issueDef.getAssignedToGroup() == null));
            allUsers.addValueChangeHandler(new ValueChangeHandler<Boolean>()
            {
                @Override
                public void onValueChange(ValueChangeEvent<Boolean> event)
                {
                    if (event.getValue())
                        _issueDef.setAssignedToGroup(null);
                    _groupList.setEnabled(!event.getValue());
                    setDirty(true);
                }
            });
            allPanel.add(allUsers);

            HorizontalPanel groupPanel = new HorizontalPanel();
            groupPanel.setSpacing(2);
            groupPanel.setVerticalAlignment(HasVerticalAlignment.ALIGN_MIDDLE);
            RadioButton specificGroup = new RadioButton("assignedToMethod", "Specific Group");
            specificGroup.setStylePrimaryName("assigned-to-group-specific");
            specificGroup.setValue((_issueDef.getAssignedToGroup() != null));
            specificGroup.addValueChangeHandler(new ValueChangeHandler<Boolean>()
            {
                @Override
                public void onValueChange(ValueChangeEvent<Boolean> event)
                {
                    String value = _groupList.getValue(_groupList.getSelectedIndex());
                    _issueDef.setAssignedToGroup(Integer.parseInt(value));
                    _groupList.setEnabled(event.getValue());
                    setDirty(true);
                }
            });
            groupPanel.add(specificGroup);

            // drop down for specific group
            _groupList = new ListBox(false);
            _groupList.setStylePrimaryName("assigned-to-group");
            Integer idx = 0;
            int selectedIndex = 0;

            for (Map<String, String> group : _projects)
            {
                _groupList.addItem(group.get("name"), group.get("value"));
                if (_issueDef.getAssignedToGroup() != null)
                {
                    if (Integer.parseInt(group.get("value")) == _issueDef.getAssignedToGroup())
                        selectedIndex = idx;
                }
                idx++;
            }
            _groupList.setSelectedIndex(selectedIndex);
            _groupList.addChangeHandler(new ChangeHandler(){
                public void onChange(ChangeEvent event)
                {
                    String value = _groupList.getValue(_groupList.getSelectedIndex());
                    _issueDef.setAssignedToGroup(Integer.parseInt(value));
                    populateUserList(Integer.parseInt(value));
                    setDirty(true);
                }
            });
            groupPanel.add(_groupList);

            Panel panel = new VerticalPanel();
            panel.add(new Label("Populate the assigned to list from:"));
            panel.add(allPanel);
            panel.add(groupPanel);

            return panel;
        }

        /**
         * Create the widget to handle the default assigned to configuration
         */
        private Widget createDefaultAssignedToConfiguration()
        {
            HorizontalPanel defaultPanel = new HorizontalPanel();
            defaultPanel.setSpacing(2);
            RadioButton noDefault = new RadioButton("assignedToUser", "No default");
            noDefault.setStylePrimaryName("assigned-to-empty");
            noDefault.setValue((_issueDef.getAssignedToUser() == null));
            noDefault.addValueChangeHandler(new ValueChangeHandler<Boolean>()
            {
                @Override
                public void onValueChange(ValueChangeEvent<Boolean> event)
                {
                    if (event.getValue())
                        _issueDef.setAssignedToUser(null);
                    _userList.setEnabled(!event.getValue());
                    setDirty(true);
                }
            });
            defaultPanel.add(noDefault);

            HorizontalPanel specificPanel = new HorizontalPanel();
            specificPanel.setSpacing(2);
            RadioButton specificUser = new RadioButton("assignedToUser", "Specific User");
            specificUser.setStylePrimaryName("assigned-to-specific-user");
            specificUser.setValue((_issueDef.getAssignedToUser() != null));
            specificUser.addValueChangeHandler(new ValueChangeHandler<Boolean>()
            {
                @Override
                public void onValueChange(ValueChangeEvent<Boolean> event)
                {
                    String value = _userList.getValue(_userList.getSelectedIndex());
                    _issueDef.setAssignedToUser(Integer.parseInt(value));
                    _userList.setEnabled(event.getValue());
                    setDirty(true);
                }
            });
            specificPanel.add(specificUser);

            // drop down for specific user
            _userList = new ListBox(false);
            _userList.setWidth("250px");
            _userList.setStylePrimaryName("assigned-to-user");
            populateUserList(_issueDef.getAssignedToGroup());
            _userList.addChangeHandler(new ChangeHandler(){
                public void onChange(ChangeEvent event)
                {
                    String value = _userList.getValue(_userList.getSelectedIndex());
                    _issueDef.setAssignedToUser(Integer.parseInt(value));
                    setDirty(true);
                }
            });
            specificPanel.add(_userList);

            Panel panel = new VerticalPanel();
            panel.add(new Label("Set default assigned to user:"));
            panel.add(defaultPanel);
            panel.add(specificPanel);

            return panel;
        }

        private void populateUserList(Integer groupId)
        {
            getService().getUsersForGroup(groupId, new ErrorDialogAsyncCallback<List<Map<String, String>>>()
            {
                @Override
                public void onSuccess(List<Map<String, String>> users)
                {
                    _userList.clear();

                    Integer idx = 0;
                    int selectedIndex = 0;

                    for (Map<String, String> user : users)
                    {
                        _userList.addItem(user.get("name"), user.get("value"));
                        if (_issueDef.getAssignedToUser() != null)
                        {
                            if (Integer.parseInt(user.get("value")) == _issueDef.getAssignedToUser())
                                selectedIndex = idx;
                        }
                        idx++;
                    }
                    _userList.setSelectedIndex(selectedIndex);
                }
            });
        }
    }
}
