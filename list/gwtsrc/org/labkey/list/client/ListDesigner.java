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

package org.labkey.list.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLTable;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.BoundCheckBox;
import org.labkey.api.gwt.client.ui.BoundListBox;
import org.labkey.api.gwt.client.ui.BoundRadioButton;
import org.labkey.api.gwt.client.ui.BoundTextAreaBox;
import org.labkey.api.gwt.client.ui.BoundTextBox;
import org.labkey.api.gwt.client.ui.DirtyCallback;
import org.labkey.api.gwt.client.ui.HelpPopup;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.LookupServiceAsync;
import org.labkey.api.gwt.client.ui.PropertiesEditor;
import org.labkey.api.gwt.client.ui.Saveable;
import org.labkey.api.gwt.client.ui.Tooltip;
import org.labkey.api.gwt.client.ui.WebPartPanel;
import org.labkey.api.gwt.client.ui.WindowUtil;
import org.labkey.api.gwt.client.ui.domain.DomainImporter;
import org.labkey.api.gwt.client.ui.domain.DomainImporterService;
import org.labkey.api.gwt.client.ui.domain.DomainImporterServiceAsync;
import org.labkey.api.gwt.client.util.BooleanProperty;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;
import org.labkey.api.gwt.client.util.IntegerProperty;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;
import org.labkey.api.gwt.client.util.StringProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: matthewb
 * Date: Apr 24, 2007
 * Time: 9:24:04 AM
 */
public class ListDesigner implements EntryPoint, Saveable<GWTList>
{
    private String _returnURL;
    private String _cancelURL;
    private boolean _hasDesignListPermission = false;
    private boolean _hasInsertPermission = false;
    private boolean _hasDeleteListPermission = false;

    private boolean _readonly = true;
    private int _listId = 0;
    private HashSet<String> _listNames = new HashSet<String>();   // Really want CaseInsenstiveHashSet, but not accessible here
    private GWTList _list;
    private GWTDomain _domain;

    // internal form state 
    private BooleanProperty _importFromFile = new BooleanProperty(false);
    private int _titlePropertyId = 0;
    private ListBox _titleListBox = null;

    // UI bits
    private RootPanel _root = null;
    private FlexTable _buttons = null;
    private Label _loading = null;
    private PropertiesEditor _propTable = null;

    private ListPropertiesPanel _propertiesPanel;
    private ListSchema _schemaPanel;
    private boolean _dirty;

    private SubmitButton _saveButton;

    private class DomainListSaveable implements Saveable<GWTDomain>
    {
        private Saveable<GWTList> _listSaveable;

        public DomainListSaveable(Saveable<GWTList> listSaveable)
        {
            _listSaveable = listSaveable;
        }

        @Override
        public String getCurrentURL()
        {
            return getEditorURL();
        }

        public void save()
        {
            _listSaveable.save();
        }

        public void save(final SaveListener<GWTDomain> gwtDomainSaveListener)
        {
            // okay, this gets a bit mind bending.  When asked to save, the GWTDomain saveable delegates
            // to the GWTList Saveable.  When the list reply comes back, the GWTList Saveable must
            // make an async call to get the GWT Domain so we can forward the successful save message back
            // to the original GWT Domain saveable.
            _listSaveable.save(new SaveListener<GWTList>()
            {
                public void saveSuccessful(GWTList listResult, String designerUrl)
                {
                    asyncGetDefinition(gwtDomainSaveListener);
                }
            });
        }

        public void cancel()
        {
            _listSaveable.cancel();
        }

        public void finish()
        {
            _listSaveable.finish();
        }

        public boolean isDirty()
        {
            return _listSaveable.isDirty();
        }
    }



    String loadingStyle = null;
    
    public String getLoadingStyle()
    {
        if (null == loadingStyle)
        {
            loadingStyle = PropertyUtil.getServerProperty("loadingStyle");
            if (null == loadingStyle || "".equals(loadingStyle))
                loadingStyle = "loading-indicator";
        }
        return loadingStyle;
    }
    
    // move to shared base class
    public void clearLoading(RootPanel panel)
    {
        panel.clear();
        Element e = panel.getElement();
        e.removeClassName(getLoadingStyle());
    }


    public void loading(RootPanel panel, String message)
    {
        panel.clear();
        panel.getElement().addClassName(getLoadingStyle());
        _loading = new Label(null==message?"Loading...":message);
        panel.add(_loading);
    }


    public void onModuleLoad()
    {
        _listId = Integer.parseInt(PropertyUtil.getServerProperty("listId"));
        _returnURL = PropertyUtil.getReturnURL();
        _cancelURL = PropertyUtil.getCancelURL();
        _hasDesignListPermission = Boolean.valueOf(PropertyUtil.getServerProperty("hasDesignListPermission"));
        _hasInsertPermission = Boolean.valueOf(PropertyUtil.getServerProperty("hasInsertPermission"));
        _hasDeleteListPermission =  Boolean.valueOf(PropertyUtil.getServerProperty("hasDeleteListPermission"));
        boolean startInEdit = "#edit".equals(Window.Location.getHash()) || "1".equals(Window.Location.getParameter("edit"));

        //noinspection GwtToHtmlReferences
        _root = RootPanel.get("org.labkey.list.Designer-Root");
        clearLoading(_root);

        _propTable = new _ListPropertiesEditor(_root, new DomainListSaveable(this), getService());
        setReadOnly(!startInEdit || !_hasDeleteListPermission);

        _buttons = new FlexTable();
        _buttons.getElement().setClassName("gwt-ButtonBar");

        _saveButton = new SubmitButton();

        asyncGetListNames();

        // NOTE for now we're displaying list info w/ static HTML
        if (_listId == 0)
        {
            _list = new GWTList(0);
            showNewListUI();
        }
        else
        {
            asyncGetList(_listId, null);
        }

        Window.addWindowClosingHandler(new Window.ClosingHandler()
        {
            public void onWindowClosing(Window.ClosingEvent event)
            {
                if (isDirty())
                    event.setMessage("Changes have not been saved and will be discarded.");
            }
        });
    }


    public void refreshButtons()
    {
        _buttons.clear();

        int col=0;

        if (_listId == 0)
        {
            _buttons.setWidget(0, col++, new ImageButton("Create List", new ClickHandler(){
                public void onClick(ClickEvent event)
                {
                    if (null != _list && !isEmpty(_list.getName()))
                    {
                        setDirty(false);

                        //Issue 13457: dont actually create the list until later for excel import
                        if (_importFromFile.booleanValue())
                        {
                            showImporterUI();
                        }
                        else
                        {
                            _service.createList(_list, new ErrorDialogAsyncCallback<GWTList>(){
                                public void onSuccess(GWTList result)
                                {
                                    setReadOnly(false);
                                    setList(result);
                                }

                                protected void reportFailure(String message, Throwable caught)
                                {
                                    if(caught instanceof ListEditorService.ListImportException)
                                        message = caught.getMessage();

                                    Window.alert(message);
                                }
                            });
                        }
                    }
                }
            }));
        }
        else if (!_readonly)
        {
            _buttons.setWidget(0, col++, _saveButton);
        }
        else
        {
            if (canDesignList())
            {
                _buttons.setWidget(0, col++, new ImageButton("Edit Design", new ClickHandler(){
                    public void onClick(ClickEvent event)
                    {
                        setReadOnly(false);
                    }
                }));

                if (canDeleteList() && _listId != 0)
                {
                    _buttons.setWidget(0, col++, (new ImageButton("Delete List", new ClickHandler(){
                        public void onClick(ClickEvent event)
                        {
                            WindowUtil.setLocation(PropertyUtil.getRelativeURL("deleteListDefinition.view") + "?listId=" + _listId);
                        }
                    })));
                }
            }

            if (canInsert() && _listId != 0)
            {
                _buttons.setWidget(0, col++, new ImageButton("Import Data", new ClickHandler(){
                    public void onClick(ClickEvent event)
                    {
                        WindowUtil.setLocation(PropertyUtil.getRelativeURL("uploadListItems.view") + "?listId=" + _listId);
                    }
                }));
            }
        }

        if (_listId != 0 && _readonly)
        {
            _buttons.setWidget(0, col++, new ImageButton("Done", new ClickHandler(){
                public void onClick(ClickEvent event)
                {
                    done();
                }
            }));
        }
        else
        {
            _buttons.setWidget(0, col++, new ImageButton("Cancel", new ClickHandler(){
                public void onClick(ClickEvent event)
                {
                    cancel();
                }
            }));
        }
        //noinspection GWTStyleCheck
        _buttons.setStyleName("gwt-ButtonBar");
    }


    int fakePropertyId = -1;

    void refreshTitleListBox()
    {
        if (null == _titleListBox)
            return;
        int count = _propTable.getPropertyCount();

        String name = (null==_list || null==_list.getTitleField()) ? "" : _list.getTitleField();
        int id = _titlePropertyId;
        int titlePropByName = -1;
        int titlePropById = -1;
        int titlePropByRename = -1;
        _titleListBox.clear();
        _titleListBox.addItem("<AUTO>", "0|<AUTO>");

        for (int i = 0; i < count; i++)
        {
            if (_propTable.isDeleted(i))
                continue;
            GWTPropertyDescriptor pd = _propTable.getPropertyDescriptor(i);
            if (pd.getPropertyId() == 0)
                pd.setPropertyId(fakePropertyId--);
            if (!isEmpty(name))
            {
                if (name.equals(pd.getName()))
                    titlePropByName = _titleListBox.getItemCount();
                if (id != 0 && id == pd.getPropertyId())
                    titlePropById = _titleListBox.getItemCount();
                GWTPropertyDescriptor old = _propTable.getOriginalPropertyDescriptor(i);
                if (null != old && name.equals(old.getName()))
                    titlePropByRename = _titleListBox.getItemCount();
            }
            _titleListBox.addItem(pd.getName(), String.valueOf(pd.getPropertyId()) + "|" + pd.getName());
        }

        int titleProp = 0 < titlePropById ? titlePropById : 0 < titlePropByName ? titlePropByName : titlePropByRename;
        _titleListBox.setSelectedIndex(Math.max(0, titleProp));
    }


    private void setDirty(boolean dirty)
    {
        _dirty = dirty;
    }

    public boolean isDirty()
    {
        return _dirty || _propTable.isDirty();
    }


    public void setList(GWTList ds)
    {
        _listId = ds.getListId();
        _list = ds;
        Element el = DOM.getElementById("labkey-nav-trail-current-page");
        if (null != el && null != ds && null != ds.getName())
            el.setInnerText(ds.getName());
        asyncGetDefinition();
    }


    public void setDomain(GWTDomain d)
    {
        if (null == _root)
            return;

        d.setPhiNotAllowedFieldNames(Collections.singleton(_list.getKeyPropertyName()));

        _domain = d;
        _domain.setSchemaName("lists");
        _domain.setQueryName(d.getName());

        _propTable.init(new GWTDomain(d));
        if (null == d.getFields() || d.getFields().size() == 0)
            _propTable.addField(new GWTPropertyDescriptor());

        showDesignerUI();
    }


    private void showNewListUI()
    {
        if (0 == _listId)
        {
            clearLoading(_root);
            _root.add(_buttons);

            _list = new GWTList();
            CreateListPanel p = new CreateListPanel();
            _root.add(p);

            refreshButtons();
        }
    }


    private void showDesignerUI()
    {
        if (null != _domain && null != _list)
        {
            clearLoading(_root);
            _root.add(_buttons);

            _propertiesPanel = new ListPropertiesPanel(_readonly);
            _root.add(new WebPartPanel("List Properties", _propertiesPanel));

            _propTable.setReadOnly(_readonly);
            _schemaPanel = new ListSchema(_propTable);
            _root.add(new WebPartPanel("List Fields", _schemaPanel));

            refreshButtons();
        }
    }


    private void showImporterUI()
    {
        clearLoading(_root);

        VerticalPanel vPanel = new VerticalPanel();
        _root.add(vPanel);

        List<String> columnsToMap = Collections.emptyList();

        Set<String> baseColumnNames = new HashSet<String>();
        String baseColNamesString = PropertyUtil.getServerProperty("baseColumnNames");
        if (baseColNamesString == null)
            baseColNamesString = "";
        for (String s : baseColNamesString.split(","))
            baseColumnNames.add(s);

        DomainImporter domainImporter = new _DomainImporter(getImporterService(), columnsToMap, baseColumnNames);
        vPanel.add(domainImporter.getMainPanel());
    }


    private void setReadOnly(boolean ro)
    {
        _propTable.setReadOnly(ro);
        if (ro == _readonly)
            return;
        _readonly = ro;
        showDesignerUI();
    }
    

    class SubmitButton extends ImageButton
    {
        SubmitButton()
        {
            super("Save");
        }

        public void onClick(Widget sender)
        {
            saveListDefinition();
        }
    }


    public void save(final SaveListener<GWTList> listener)
    {
        // bug 6898: prevent user from double-clicking on save button, causing a race condition
        _saveButton.setEnabled(false);

        List<String> errors = new ArrayList<String>();

        _propertiesPanel.validate(errors);
        _schemaPanel.validate(errors);

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
                        listener.saveSuccessful(_list, getEditorURL());
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

        getService().updateListDefinition(_list, _domain, _propTable.getUpdates(), callback);
    }


    public String getEditorURL()
    {
        String url = PropertyUtil.getCurrentURL();
        if (_listId > 0)
        {
            if (-1 != (url.indexOf('?')))
                url = url.substring(0,url.indexOf('?'));
            url += "?listId=" + _listId  + "&edit=1";
        }
        return url;        
    }

    @Override
    public String getCurrentURL()
    {
        return PropertyUtil.getCurrentURL();
    }

    public void save()
    {
        save(null);
    }


    public void cancel()
    {
        if (!_readonly && _listId != 0)
        {
            //
            if (isDirty())
            {
                if (!Window.confirm("Changes have not been saved and will be discarded."))
                    return;
            }
            setDirty(false);
            setReadOnly(true);
            asyncGetList(_listId, null);
            return;
        }

        if (!isEmpty(_cancelURL))
            WindowUtil.setLocation(_cancelURL);
        else
            WindowUtil.setLocation(PropertyUtil.getRelativeURL("begin.view"));
    }


    public void done()
    {
        if (!_readonly && _listId != 0)
        {
            cancel();
            return;
        }

        if (!isEmpty(_returnURL))
            WindowUtil.setLocation(_returnURL);
        else
            WindowUtil.setLocation(PropertyUtil.getRelativeURL("begin.view"));
    }


    public void finish()
    {
        saveListDefinition();
    }


    public void saveListDefinition()
    {
        save(new SaveListener<GWTList>()
        {
            public void saveSuccessful(GWTList list, String designerUrl)
            {
                setReadOnly(true);
                asyncGetList(list.getListId(), "Save successful. Loading...");
            }
        });
    }


    /*
     * SERVER CALLBACKS
     */

    private ListEditorServiceAsync _service = null;
    private ListEditorServiceAsync getService()
    {
        if (_service == null)
        {
            _service = GWT.create(ListEditorService.class);
            ServiceUtil.configureEndpoint(_service, "listEditorService");
        }
        return _service;
    }


    void asyncGetListNames()
    {
        _service.getListNames(new ErrorDialogAsyncCallback<List<String>>(){
            public void onSuccess(List<String> result)
            {
                for (String name : result)
                    if (null != name)
                        _listNames.add(name.toLowerCase());
            }
        });
    }


    void asyncGetList(int id, String message)
    {
        loading(_root, message);
        setDirty(false);
        _list = null;
        _domain = null;
        getService().getList(id, new ErrorDialogAsyncCallback<GWTList>()
        {
            public void handleFailure(String message, Throwable caught)
            {
                _loading.setText("ERROR: " + message);
            }

            public void onSuccess(GWTList result)
            {
                setList(result);
            }
        });
    }

    void asyncGetDefinition()
    {
        asyncGetDefinition(null);
    }

    void asyncGetDefinition(final Saveable.SaveListener<GWTDomain> saveListener)
    {
        _propTable.setReadOnly(_readonly);
        
        getService().getDomainDescriptor(_list, new ErrorDialogAsyncCallback<GWTDomain>()
        {
            public void handleFailure(String message, Throwable caught)
            {
                _loading.setText("ERROR: " + message);
            }

            public void onSuccess(GWTDomain result)
            {
                GWTDomain domain = result;
                if (null == domain)
                {
                    domain = new GWTDomain();
                    Window.alert("Error editing list: " + _list.getName());
                }

                setDomain(domain);

                if (saveListener != null)
                    saveListener.saveSuccessful(domain, getEditorURL());
            }
        });
    }


    // CONSIDER: ListEditorService could implement DomainImporterService
    
    private DomainImporterServiceAsync _importerService = null;

    private DomainImporterServiceAsync getImporterService()
    {
        if (_importerService == null)
        {
            _importerService = GWT.create(DomainImporterService.class);
            ServiceUtil.configureEndpoint(_importerService, "domainImportService");
        }
        return _importerService;
    }


    /*
     * utils
     */

    boolean canDesignList()
    {
        return _hasDesignListPermission;
    }

    boolean canDeleteList()
    {
        return _hasDeleteListPermission;
    }

    boolean canInsert()
    {
        return _hasInsertPermission;
    }


    private class DirtySetter implements DirtyCallback
    {
        @Override
        public void setDirty(boolean dirty)
        {
            ListDesigner.this.setDirty(true);
        }
    }


    private DirtySetter dirtySetter = new DirtySetter();

    private class CreateListPanel extends VerticalPanel
    {
        final FlexTable _table = new FlexTable();

        public CreateListPanel()
        {
            super();
            createPanel();
        }

        private void createPanel()
        {
            String labelStyleName = "labkey-form-label"; // Pretty yellow background for labels
            HTMLTable.CellFormatter cellFormatter = _table.getCellFormatter();
            _table.setStyleName("lk-fields-table");

            int row = 0;

            add(_table);

            // NAME
            {
                Widget listNameTextBox = new _ListNameTextBox("Name", "ff_name", _list.name, dirtySetter);
                HorizontalPanel panel = new HorizontalPanel();
                panel.add(new Label("Name"));
                panel.add(new HelpPopup("Name", "Name of new list"));
                _table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);
                _table.setWidget(row, 1, listNameTextBox);
                row++;
            }

            // PK NAME
            {
                _list.setKeyPropertyName("Key");
                BoundTextBox name = new BoundTextBox("Primary Key Name", "ff_keyName", _list.keyPropertyName, dirtySetter);
                name.setRequired(true);
                HorizontalPanel panel = new HorizontalPanel();
                panel.add(new Label("Primary Key"));
                panel.add(new HelpPopup("Primary Key", "What is the name of the key in your list?"));
                _table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);
                _table.setWidget(row, 1, name);
                row++;
            }

            // PK TYPE
            {
                _list.keyPropertyType.set("AutoIncrementInteger");
                BoundListBox type = new BoundListBox("ff_keyType", false, _list.keyPropertyType, dirtySetter);
                type.addItem("Auto-Increment Integer", "AutoIncrementInteger");
                type.addItem("Integer", "Integer");
                type.addItem("Text (String)", "Varchar");
                HorizontalPanel panel = new HorizontalPanel();
                panel.add(new Label("Primary Key Type"));
                panel.add(new HelpPopup("Key Type", "Every item in a list has a key value which uniquely identifies that item. What is the data type of the key in your list?"));
                _table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);
                _table.setWidget(row, 1, type);
                row++;
            }

            // IMPORT
            {
                CheckBox importFile = new BoundCheckBox("fileImport", _importFromFile, dirtySetter);
                HorizontalPanel panel = new HorizontalPanel();
                panel.add(new Label("Import from file"));
                panel.add(new HelpPopup("Import from file", "Use this option if you have a spreadsheet that you would like uploaded as a list."));
                _table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);
                _table.setWidget(row, 1, importFile);
                row++;
            }
        }
    }


    private class ListPropertiesPanel extends VerticalPanel
    {
        private final String checkboxStyleName = "gwt-label";      // Standard checkbox style is 8pt... let's match label instead

        public ListPropertiesPanel(boolean readonly)
        {
            super();
            createPanel(readonly);
        }

        private void createPanel(boolean readonly)
        {
            String labelStyleName = "labkey-form-label"; // Pretty yellow background for labels
            FlexTable table = new FlexTable();
            HTMLTable.CellFormatter cellFormatter = table.getCellFormatter();
            table.setStyleName("lk-fields-table");

            int row = 0;

            add(table);

            // NAME
            {
                Widget listNameTextBox = readonly ?
                        new Label(_list.getName()) :
                        new _ListNameTextBox("Name", "ff_name", _list.name, dirtySetter);
                HorizontalPanel panel = new HorizontalPanel();
                panel.add(new Label("Name"));
                table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);
                table.setWidget(row, 1, listNameTextBox);
                row++;
            }

            // DESCRIPTION
            {
                Widget descriptionTextBox = readonly ?
                        new Label(_list.getDescription()) :
                        new BoundTextAreaBox("Description", "ff_description", _list.description, dirtySetter);
                HorizontalPanel panel = new HorizontalPanel();
                panel.add(new Label("Description"));
                table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);
                table.setWidget(row, 1, descriptionTextBox);
                row++;
            }

            // TITLE
            {
                String titleText = _list.getTitleField();
                if (isEmpty(titleText))
                    titleText = "<AUTO> (" + _list.getDefaultTitleField() + ")";
                Widget titleListBox = readonly ?
                        new Label(titleText) :
                        new ListBox(false);
                HorizontalPanel panel = new HorizontalPanel();
                panel.add(new Label("Title Field"));
                table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);
                table.setWidget(row, 1, titleListBox);
                row++;

                if (!_readonly)
                {
                    _titleListBox = (ListBox)titleListBox;
                    DOM.setElementProperty(_titleListBox.getElement(), "id", "ff_titleColumn");
                    _propTable.addChangeHandler(new ChangeHandler(){
                        public void onChange(ChangeEvent event)
                        {
                            refreshTitleListBox();
                        }
                    });
                    _titleListBox.addChangeHandler(new ChangeHandler(){
                        public void onChange(ChangeEvent event)
                        {
                            String value = _titleListBox.getValue(_titleListBox.getSelectedIndex());
                            int split = value.indexOf('|');
                            _titlePropertyId = Integer.parseInt(value.substring(0,split));
                            _list.setTitleField(value.substring(split+1));
                            setDirty(true);
                        }
                    });
                    refreshTitleListBox();
                }
            }

            // DISCUSSION LINKS
            {
                RadioButton none = new BoundRadioButton("ff_discussionSetting", "None", _list.discussionSetting, 0, dirtySetter);
                none.setEnabled(!readonly);
                RadioButton one = new BoundRadioButton("ff_discussionSetting", "Allow one discussion per item", _list.discussionSetting, 1, dirtySetter);
                one.setEnabled(!readonly);
                RadioButton multi = new BoundRadioButton("ff_discussionSetting", "Allow multiple discussions per item", _list.discussionSetting, 2, dirtySetter);
                multi.setEnabled(!readonly);
                Panel radios = new VerticalPanel();
                radios.add(none);
                radios.add(one);
                radios.add(multi);

                HorizontalPanel panel = new HorizontalPanel();
                panel.add(new Label("Discussion Links"));
                table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);
                table.setWidget(row, 1, radios);
                row++;
            }

            // ALLOWABLE ACTIONS
            {
                HorizontalPanel panel = new HorizontalPanel();
                panel.add(new Label("Allowable Actions"));
                table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);

                VerticalPanel allowablePanel = new VerticalPanel();
                table.setWidget(row, 1, allowablePanel);

                BoundCheckBox allowDelete = new BoundCheckBox("ff_allowDelete", _list.allowDelete, dirtySetter);
                allowDelete.setText("Delete");
                allowDelete.setStyleName(checkboxStyleName);
                allowDelete.setEnabled(!readonly);
                allowablePanel.add(allowDelete);

                BoundCheckBox allowUpload = new BoundCheckBox("ff_allowUpload", _list.allowUpload, dirtySetter);
                allowUpload.setText("Upload");
                allowUpload.setStyleName(checkboxStyleName);
                allowUpload.setEnabled(!readonly);
                allowablePanel.add(allowUpload);

                BoundCheckBox allowExport = new BoundCheckBox("ff_allowExport", _list.allowExport, dirtySetter);
                allowExport.setText("Export and print");
                allowExport.setStyleName(checkboxStyleName);
                allowExport.setEnabled(!readonly);
                allowablePanel.add(allowExport);

                row++;
            }

            // FULL-TEXT SEARCH INDEXING
            {
                HorizontalPanel panel = new HorizontalPanel();
                panel.add(new Label("Full-Text Search Indexing"));
                table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);

                VerticalPanel indexPanel = new VerticalPanel();
                table.setWidget(row, 1, indexPanel);

                VerticalPanel entireListSettings = new VerticalPanel();
                RadioButton metadata = new BoundRadioButton("ff_entireListSetting", "Metadata only (name and description of list and fields)", _list.entireListIndexSetting, 0, dirtySetter);
                metadata.setEnabled(!readonly);
                RadioButton data = new BoundRadioButton("ff_entireListSetting", "Data only", _list.entireListIndexSetting, 1, dirtySetter);
                data.setEnabled(!readonly);
                RadioButton both = new BoundRadioButton("ff_entireListSetting", "Metadata and data", _list.entireListIndexSetting, 2, dirtySetter);
                both.setEnabled(!readonly);
                entireListSettings.add(metadata);

                HelpPopup eachItemTitleHelp = null;
                HelpPopup eachItemBodyHelp = null;
                HelpPopup entireListTitleHelp = null;
                HelpPopup entireListBodyHelp = null;

                if (readonly)
                {
                    entireListSettings.add(data);
                    entireListSettings.add(both);
                }
                else
                {
                    String warningTitle = "Index Entire List Data";
                    String warningText = "Not recommend for large lists with frequent updates, since updating any item will cause re-indexing of the entire list";
                    HorizontalPanel hpData = new HorizontalPanel();
                    hpData.add(data);
                    hpData.add(new HelpPopup(warningTitle, warningText));
                    entireListSettings.add(hpData);

                    HorizontalPanel hpBoth = new HorizontalPanel();
                    hpBoth.add(both);
                    hpBoth.add(new HelpPopup(warningTitle, warningText));
                    entireListSettings.add(hpBoth);

                    eachItemTitleHelp = new HelpPopup("Example Title Template", getExampleTemplate(_list.getName() + " - ", 2));
                    eachItemBodyHelp = new HelpPopup("Example Body Template", getExampleTemplate("", 5));
                    entireListTitleHelp = new HelpPopup("Custom Title", "Any text you want displayed and indexed as the search result title");
                    entireListBodyHelp = new HelpPopup("Example Body Template", getExampleTemplate("", 5));
                }

                addIndexSettings(indexPanel, "eachItem", "Index each item as a separate document", _list.eachItemIndex, null,
                        _list.eachItemTitleSetting, _list.eachItemTitleTemplate, eachItemTitleHelp,
                        _list.eachItemBodySetting, _list.eachItemBodyTemplate, eachItemBodyHelp, readonly);
                addIndexSettings(indexPanel, "entireList", "Index entire list as a single document", _list.entireListIndex, entireListSettings,
                        _list.entireListTitleSetting, _list.entireListTitleTemplate, entireListTitleHelp,
                        _list.entireListBodySetting, _list.entireListBodyTemplate, entireListBodyHelp, readonly);

                //Add File Attachment Index checkbox
                final BoundCheckBox indexCheckBox = new BoundCheckBox("ff_attachmentsIndex", _list.fileAttachmentIndex, dirtySetter);
                indexCheckBox.setText("Index file attachments");
                indexCheckBox.setStyleName(checkboxStyleName);
                indexCheckBox.setEnabled(!readonly);
                indexPanel.add(indexCheckBox);

                row++;
            }
        }


        private void addIndexSettings(VerticalPanel panel, String type, String description, BooleanProperty indexProperty,
              Panel additionalOptions, IntegerProperty titleSetting, StringProperty titleTemplate, HelpPopup titleHelp,
              IntegerProperty bodySetting, StringProperty bodyTemplate, HelpPopup bodyHelp, boolean readonly)
        {
            final BoundCheckBox indexCheckBox = new BoundCheckBox("ff_" + type + "Index", indexProperty, dirtySetter);
            indexCheckBox.setText(description);
            indexCheckBox.setStyleName(checkboxStyleName);
            indexCheckBox.setEnabled(!readonly);
            panel.add(indexCheckBox);

            final VerticalPanel settingsPanel = new VerticalPanel();
            panel.add(settingsPanel);

            HorizontalPanel hPanel = new HorizontalPanel();
            settingsPanel.add(hPanel);

            hPanel.add(new Spacer());
            VerticalPanel vPanel = new VerticalPanel();
            hPanel.add(vPanel);

            if (null != additionalOptions)
            {
                vPanel.add(additionalOptions);
                vPanel.add(new Spacer());
            }

            vPanel.add(createOptionsAndTemplatePanel(new String[]{"Standard title", "Custom title"}, titleHelp, "ff_" + type + "TitleSetting", titleSetting, "ff_" + type + "TitleTemplate", titleTemplate, readonly));
            vPanel.add(new Spacer());
            vPanel.add(new InlineHTML("<strong>Warning:</strong> Fields annotated at a PHI level will not be indexed."));
            vPanel.add(createOptionsAndTemplatePanel(new String[]{"Index all text fields", "Index all fields (text, number, date and boolean)", "Index using custom template"}, bodyHelp, "ff_" + type + "BodySetting", bodySetting, "ff_" + type + "BodyTemplate", bodyTemplate, readonly));

            ClickHandler ch = new ClickHandler()
            {
                @Override
                public void onClick(ClickEvent event)
                {
                    settingsPanel.setVisible(indexCheckBox.getValue());
                }
            };

            ch.onClick(null);   // Set initial visibility based on current setting
            indexCheckBox.addClickHandler(ch);
        }


        private String getExampleTemplate(String prefix, int maxFields)
        {
            // Get the field names in the title list box, skipping the first one (<AUTO>)
            int count = Math.min(_titleListBox.getItemCount() - 1, maxFields);

            String template = prefix;

            for (int i = 1; i <= count; i++)
            {
                String value = _titleListBox.getValue(i);
                int idx = value.indexOf('|');

                if (idx > -1)
                {
                    template = template + " ${" + value.substring(idx + 1) + "}";
                }
            }

            return template;
        }


        private Panel createOptionsAndTemplatePanel(String[] radioCaptions, HelpPopup help, String radioName, final IntegerProperty radioProperty, String templateName, StringProperty templateProperty, boolean readonly)
        {
            Panel vPanel = new VerticalPanel();
            final HorizontalPanel templatePanel = new HorizontalPanel();
            final int lastTemplateOption = radioCaptions.length - 1;

            ClickHandler ch = new ClickHandler()
            {
                @Override
                public void onClick(ClickEvent event)
                {
                    templatePanel.setVisible(radioProperty.intValue() == lastTemplateOption);
                }
            };

            int i = 0;

            for (String caption : radioCaptions)
            {
                RadioButton radio = new BoundRadioButton(radioName, caption, radioProperty, i++, dirtySetter);
                radio.setEnabled(!readonly);
                radio.addClickHandler(ch);

                if (readonly || i < radioCaptions.length || null == help)
                {
                    vPanel.add(radio);
                }
                else
                {
                    HorizontalPanel hp = new HorizontalPanel();
                    hp.add(radio);
                    hp.add(help);
                    vPanel.add(hp);
                }
            }

            templatePanel.add(new Spacer());

            if (readonly)
            {
                templatePanel.add(new Label(templateProperty.getString()));
            }
            else
            {
                BoundTextAreaBox titleTemplate = new BoundTextAreaBox("Template", templateName, templateProperty, dirtySetter);
                templatePanel.add(titleTemplate);
            }

            vPanel.add(templatePanel);

            ch.onClick(null);  // Set initial visibility on template box
            return vPanel;
        }


        public void validate(List<String> errors)
        {
            if (isEmpty(_list.getName()))
                errors.add("List name cannot be empty.");

            if (isEmpty(_list.getKeyPropertyName()))
                errors.add("Please select a field name for the key column.");

            if (_list.getName().length() > ListEditorService.MAX_NAME_LENGTH)
                errors.add("List name cannot be longer than " + ListEditorService.MAX_NAME_LENGTH + " characters");
        }
    }


    private static class Spacer extends InlineHTML
    {
        public Spacer()
        {
            super("&nbsp;&nbsp;&nbsp;&nbsp;");
        }
    }


    private class ListSchema extends FlexTable
    {
        private PropertiesEditor _propEdit;

        public ListSchema(PropertiesEditor propEdit)
        {
            super();
            _propEdit = propEdit;
            createPanel();
        }

        private void createPanel()
        {
            Widget propTable = _propEdit.getWidget();
            setWidget(0, 0, propTable);
        }

        public void validate(List<String> errors)
        {
            errors.addAll(_propEdit.validate());
        }
    }


    class _ListNameTextBox extends BoundTextBox
    {
        String origName;

        _ListNameTextBox(String caption, String id, StringProperty prop, DirtyCallback dirtyCallback)
        {
            super(caption, id, prop, dirtyCallback);
            setRequired(true);
            origName = prop.getString();
            if (null == origName)
                origName = "";
        }

        @Override
        public String validateValue(String name)
        {
            String msg = super.validateValue(name);
            if (null != msg)
                return msg;
            if (null == name)
                return "The name cannot be null.";
            if (_listNames.contains(name.toLowerCase()) && !origName.equalsIgnoreCase(name))
                return "The name '" + name + "' is already in use.";
            return null;
        }
    }


    private class _ListPropertiesEditor extends PropertiesEditor
    {
        _ListPropertiesEditor(RootPanel rootPanel, Saveable<GWTDomain> owner, LookupServiceAsync lookup)
        {
            super(rootPanel, owner, lookup, new GWTPropertyDescriptor(), true);
        }

        private boolean isKeyRow(PropertiesEditor.Row row)
        {
            if (null == _list)
                return false;
            String name = (null == row.orig ? row.edit.getName() : row.orig.getName());
            if (null == name)
                return false;
            return name.equalsIgnoreCase(_list.getKeyPropertyName());
        }

        @Override
        protected Widget getDecorationImage(FieldStatus status, PropertiesEditor.Row row)
        {
            if (isKeyRow(row))
            {
                String name = (null == row.orig ? row.edit.getName() : row.orig.getName());
                if (name.equalsIgnoreCase(_list.getKeyPropertyName()))
                {
                    HTML html = new HTML("<span class='labkey-link gwt-FontImage fa fa-key'></span>");
                    Tooltip.addTooltip(html, "primary key");
                    return html;
                }
            }
            return super.getDecorationImage(status, row);
        }

        @Override
        protected boolean canDelete(Row row)
        {
            return !isKeyRow(row) && super.canDelete(row);
        }

        @Override
        protected boolean isTypeEditable(Row row)
        {
            if (!isKeyRow(row))
                return super.isTypeEditable(row);
            else return false;
        }

        @Override
        protected boolean isNameEditable(Row row)
        {
            if (!isKeyRow(row))
                return super.isNameEditable(row);
            else return true;
        }
    }


    private class _DomainImporter extends DomainImporter
    {
        _DomainImporter(DomainImporterServiceAsync service, List<String> columnsToMap, Set<String> baseColumnNames)
        {
            super(service, columnsToMap, baseColumnNames);
        }

        @Override
        protected void onFinish()
        {
            getService().getDomainDescriptor(_list, new ErrorDialogAsyncCallback<GWTDomain>()
            {
                @Override
                public void onSuccess(GWTDomain result)
                {
                    // Need to escapeURIComponent each part of the container path
                    StringBuilder loc = new StringBuilder(PropertyUtil.getContextPath());
                    loc.append("/list");
                    for (String part : PropertyUtil.getContainerPath().split("/"))
                    {
                        if(!"".equals(part))
                        {
                            loc.append("/").append(URL.encodePathSegment(part));
                        }
                    }
                    loc.append("/grid.view?listId=").append(_list.getListId());
                    WindowUtil.setLocation(loc.toString());
                }
            });
        }

        @Override
        protected void onCancel()
        {
            if(!_importFromFile.booleanValue())
            {
                setReadOnly(true);
                asyncGetList(_list.getListId(), null);
            }
            else
            {
                //if an error happens when trying to infer the list from an excel file
                //rather than abort back to the list designer with no columns, we
                //re-render that UI
                //we also delete the partial list
                if(_list.getListId() != 0)
                {
                    _service.deleteList(_list, new ErrorDialogAsyncCallback<Void>()
                    {
                        @Override
                        public void reportFailure(String message, Throwable caught)
                        {
                            if(caught instanceof ListEditorService.ListImportException)
                                message = caught.getMessage();

                            Window.alert(message);
                        }

                        @Override
                        public void onSuccess(Void v)
                        {
                            _list._listId(0);
                        }
                    });
                }

                showImporterUI();
            }
        }

        @Override
        protected String getTypeURI()
        {
            return _list.getTypeURI();
        }

        @Override
        protected void importData()
        {
            //create list if it does not exist
            if(_list.getListId() == 0)
            {
                _service.createList(_list, new ErrorDialogAsyncCallback<GWTList>(){
                    public void onSuccess(GWTList result)
                    {
                        //NOTE: setList() will reload the definition, so we do not call it
                        _listId = result.getListId();
                        _list = result;

                        importData();
                    }

                    protected void reportFailure(String message, Throwable caught)
                    {
                        if(caught instanceof ListEditorService.ListImportException)
                            message = caught.getMessage();

                        Window.alert(message);
                    }
                });
            }
            else
            {
                super.importData();
            }
        }
    }

    private static boolean isEmpty(String s)
    {
        return null == s || s.length() == 0;
    }


    public static void _log(String s)
    {
        PropertiesEditor._log(s);
    }
}
