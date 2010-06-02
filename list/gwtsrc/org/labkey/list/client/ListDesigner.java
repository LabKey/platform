/*
 * Copyright (c) 2010 LabKey Corporation
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

import com.extjs.gxt.ui.client.core.XDOM;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.*;
import org.labkey.api.gwt.client.ui.domain.DomainImporter;
import org.labkey.api.gwt.client.ui.domain.DomainImporterService;
import org.labkey.api.gwt.client.ui.domain.DomainImporterServiceAsync;
import org.labkey.api.gwt.client.util.*;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
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
    private HashSet<String> _listNames = new HashSet<String>();
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
        panel.getElement().removeClassName(getLoadingStyle());
        panel.getElement().addClassName("extContainer");
    }


    public void loading(RootPanel panel, String message)
    {
        panel.clear();
        Element e = panel.getElement();
        panel.getElement().addClassName(getLoadingStyle());
        _loading = new Label(null==message?"Loading...":message);
        panel.add(_loading);
    }


    public void onModuleLoad()
    {
        _listId = Integer.parseInt(PropertyUtil.getServerProperty("listId"));
        _returnURL = PropertyUtil.getServerProperty("returnURL");
        _cancelURL = PropertyUtil.getServerProperty("cancelURL");
        _hasDesignListPermission = Boolean.valueOf(PropertyUtil.getServerProperty("hasDesignListPermission"));
        _hasInsertPermission = Boolean.valueOf(PropertyUtil.getServerProperty("hasInsertPermission"));
        _hasDeleteListPermission =  Boolean.valueOf(PropertyUtil.getServerProperty("hasDeleteListPermission"));
        boolean startInEdit = "#edit".equals(Window.Location.getHash()) || "1".equals(Window.Location.getParameter("edit"));

        _root = RootPanel.get("org.labkey.list.Designer-Root");
        clearLoading(_root);

        _propTable = new _ListPropertiesEditor(new DomainListSaveable(this), getService());
        setReadOnly(!startInEdit || !_hasDeleteListPermission);

        _buttons = new FlexTable();

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
                        _log("Create List clicked");
                        _service.createList(_list, new ErrorDialogAsyncCallback<GWTList>(){
                            public void onSuccess(GWTList result)
                            {
                                _log("Create List onSuccess");
                                if (_importFromFile.booleanValue())
                                {
                                    //setList() goes to DesignerUI
                                    _list = result;
                                    showImporterUI();
                                }
                                else
                                {
                                    setReadOnly(false);
                                    setList(result);
                                }
                            }
                        });
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
                            WindowUtil.setLocation("deleteListDefinition.view?listId=" + _listId);
                        }
                    })));
                }
            }

            if (canInsert() && _listId != 0)
            {
                _buttons.setWidget(0, col++, new ImageButton("Import Data", new ClickHandler(){
                    public void onClick(ClickEvent event)
                    {
                        WindowUtil.setLocation("uploadListItems.view?listId=" + _listId);
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
        for (int i=0 ; i<count ; i++)
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
        int titleProp = 0<titlePropById?titlePropById : 0<titlePropByName?titlePropByName : titlePropByRename;
        _titleListBox.setSelectedIndex(Math.max(0,titleProp));
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
        _log("setList");
        _listId = ds.getListId();
        _list = ds;
        Element el = DOM.getElementById("labkey-nav-trail-current-page");
        if (null != el && null != ds && null != ds.getName())
            el.setInnerText(ds.getName());
        asyncGetDefinition();
    }


    public void setDomain(GWTDomain d)
    {
        _log("setDomain");
        if (null == _root)
            return;

        _domain = d;

        _propTable.init(new GWTDomain(d));
        if (null == d.getFields() || d.getFields().size() == 0)
            _propTable.addField(new GWTPropertyDescriptor());

        showDesignerUI();
    }

    
    private void showNewListUI()
    {
        if (0 == _listId)
        {
            _log("showNewListUI");

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
        _log("showDesignerUI");

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
            _log("showDesignerUI ok");
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
        String[] baseColArray = baseColNamesString.split(",");
        for (String s : baseColArray)
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
            WindowUtil.setLocation("begin.view");
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
            WindowUtil.setLocation("begin.view");
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
            _service = (ListEditorServiceAsync) GWT.create(ListEditorService.class);
            ServiceUtil.configureEndpoint(_service, "listEditorService");
        }
        return _service;
    }


    void asyncGetListNames()
    {
        _service.getListNames(new ErrorDialogAsyncCallback<List<String>>(){
            public void onSuccess(List<String> result)
            {
                _listNames.addAll(result);
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
            _importerService = (DomainImporterServiceAsync) GWT.create(DomainImporterService.class);
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
            String labelStyleName="labkey-form-label"; // Pretty yellow background for labels
            HTMLTable.CellFormatter cellFormatter = _table.getCellFormatter();

            int row = 0;

            add(_table);

            // NAME
            {
                Widget listNameTextBox = new _ListNameTextBox("Name", "ff_name", _list.name);
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
                BoundTextBox name = new BoundTextBox("Primary Key Name", "ff_keyName", _list.keyPropertyName);
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
                BoundListBox type = new BoundListBox("ff_keyType", false, _list.keyPropertyType, null);
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
                CheckBox importFile = new BoundCheckBox("fileImport", _importFromFile, null);
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
        final FlexTable _table = new FlexTable();

        public ListPropertiesPanel(boolean readonly)
        {
            super();
            createPanel(readonly);
        }

        private void createPanel(boolean readonly)
        {
            String labelStyleName="labkey-form-label"; // Pretty yellow background for labels
            HTMLTable.CellFormatter cellFormatter = _table.getCellFormatter();

            int row = 0;

            add(_table);

            // NAME
            {
                Widget listNameTextBox = readonly ?
                        new Label(_list.getName()) :
                        new _ListNameTextBox("Name", "ff_name", _list.name);
                HorizontalPanel panel = new HorizontalPanel();
                panel.add(new Label("Name"));
                //panel.add(new HelpPopup("Name", "Name of this List"));
                _table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);
                _table.setWidget(row, 1, listNameTextBox);
                row++;
            }

            // DESCRIPTION
            {
                Widget descriptionTextBox = readonly ?
                        new Label(_list.getDescription()) :
                        new BoundTextAreaBox("Description", "ff_description", _list.description, null);
                HorizontalPanel panel = new HorizontalPanel();
                panel.add(new Label("Description"));
                //panel.add(new HelpPopup("Name", "Name of this List"));
                _table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);
                _table.setWidget(row, 1, descriptionTextBox);
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
                //panel.add(new HelpPopup("Name", "Name of this List"));
                _table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);
                _table.setWidget(row, 1, titleListBox);
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
                        }
                    });
                    refreshTitleListBox();
                }
            }

            // DISCUSSION LINKS
            {
                RadioButton none = new BoundRadioButton("ff_discussionSetting", "None", _list.discussionSetting, 0);
                none.setEnabled(!readonly);
                RadioButton one = new BoundRadioButton("ff_discussionSetting", "Allow one discussion per item", _list.discussionSetting, 1);
                one.setEnabled(!readonly);
                RadioButton multi = new BoundRadioButton("ff_discussionSetting", "Allow multiple discussions per item", _list.discussionSetting, 2);
                multi.setEnabled(!readonly);
                Panel radios = new VerticalPanel();
                radios.add(none);
                radios.add(one);
                radios.add(multi);

                HorizontalPanel panel = new HorizontalPanel();
                panel.add(new Label("Discussion Links"));
                _table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);
                _table.setWidget(row, 1, radios);
                row++;
            }

            // ALLOW
            {
                BoundCheckBox allow = new BoundCheckBox("ff_allowDelete", _list.allowDelete, null);
                allow.setEnabled(!readonly);
                HorizontalPanel panel = new HorizontalPanel();
                panel.add(new Label("Allow Delete"));
                _table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);
                _table.setWidget(row, 1, allow);
                row++;
            }

            {
                BoundCheckBox allow = new BoundCheckBox("ff_allowUpload", _list.allowUpload, null);
                allow.setEnabled(!readonly);
                HorizontalPanel panel = new HorizontalPanel();
                panel.add(new Label("Allow Upload"));
                _table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);
                _table.setWidget(row, 1, allow);
                row++;
            }

            {
                BoundCheckBox allow = new BoundCheckBox("ff_allowExport", _list.allowExport, null);
                allow.setEnabled(!readonly);
                HorizontalPanel panel = new HorizontalPanel();
                panel.add(new Label("Allow Export and Print"));
                _table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);
                _table.setWidget(row, 1, allow);
                row++;
            }

            {
                BoundCheckBox index = new BoundCheckBox("ff_indexMetaData", _list.indexMetaData, null);
                index.setEnabled(!readonly);
                HorizontalPanel panel = new HorizontalPanel();
                panel.add(new Label("Index list meta data"));
                _table.setWidget(row, 0, panel);
                cellFormatter.setStyleName(row, 0, labelStyleName);
                _table.setWidget(row, 1, index);
                row++;
            }
        }


        public void validate(List<String> errors)
        {
            if (isEmpty(_list.getName()))
                errors.add("List name cannot be empty.");

            if (isEmpty(_list.getKeyPropertyName()))
                errors.add("Please select a field name for the key column.");
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

        _ListNameTextBox(String caption, String id, StringProperty prop)
        {
            super(caption, id, prop);
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
            if (_listNames.contains(name) && !origName.equalsIgnoreCase(name))
                return "The name '" + name + "' is already in use.";
            return null;
        }
    }


    private class _ListPropertiesEditor extends PropertiesEditor
    {
        _ListPropertiesEditor(Saveable<GWTDomain> owner, LookupServiceAsync lookup)
        {
            super(owner, lookup, new GWTPropertyDescriptor());
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
        protected Image getDecorationImage(FieldStatus status, PropertiesEditor.Row row)
        {
            if (isKeyRow(row))
            {
                String name = (null == row.orig ? row.edit.getName() : row.orig.getName());
                if (name.equalsIgnoreCase(_list.getKeyPropertyName()))
                {
                    String src = PropertyUtil.getContextPath() + "/_images/key.gif";
                    Image i = new Image(src);
                    i.setPixelSize(14, 21);
                    Tooltip.addTooltip(i, "primary key");
                    return i;
                }
            }
            return super.getDecorationImage(status, row);
        }

        protected boolean canDelete(Row row)
        {
            return !isKeyRow(row) && super.canDelete(row);
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
            String loc = PropertyUtil.getContextPath() + "/list" + PropertyUtil.getContainerPath() + "/grid.view";
            WindowUtil.setLocation(loc + "?listId=" + _list.getListId());
        }

        @Override
        protected void onCancel()
        {
            setReadOnly(true);
            asyncGetList(_list.getListId(), null);
        }

        @Override
        protected String getTypeURI()
        {
            return _list.getTypeURI();
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