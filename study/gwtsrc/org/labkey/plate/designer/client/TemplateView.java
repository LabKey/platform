/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.plate.designer.client;

import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.WindowResizeListener;
import com.google.gwt.user.client.WindowCloseListener;
import com.google.gwt.core.client.GWT;
import org.labkey.plate.designer.client.model.GWTPlate;
import org.labkey.plate.designer.client.model.GWTWellGroup;
import org.labkey.plate.designer.client.model.GWTPosition;
import org.labkey.api.gwt.client.util.ServiceUtil;
import org.labkey.api.gwt.client.util.ColorGenerator;
import org.labkey.api.gwt.client.util.PropertyUtil;

import java.util.*;

/**
 * User: brittp
 * Date: Feb 6, 2007
 * Time: 3:56:25 PM
 */
public class TemplateView extends HorizontalPanel
{
    private RootPanel _rootPanel;
    private String _templateName;
    private TemplateGrid _grid;
    private List _groupListeners = new ArrayList();
    private GWTWellGroup _activeGroup;
    private StatusBar _statusBar;
    private GWTPlate _plate;
    private GroupTypesTabPanel _typePanel;
    private ColorGenerator _colorGenerator = new ColorGenerator();
    private Map _groupToColorMap = new HashMap();
    private PlateDataServiceAsync _testService;
    private boolean _dirty = false;
    private Set _existingTemplateNames;
    private String _originalTemplateName;
    private TextBox _nameBox;
    private boolean _copyMode;
    private Map _cellWarnings = new HashMap();
    private TabPanel _propertyTabPanel;
    private WarningPanel _warningPanel;
    private WellGroupPropertyPanel _wellGroupPropertyPanel;
    private PlatePropertyPanel _platePropertyPanel;
    private String _assayTypeName;
    private String _templateTypeName;
    private SimplePanel _remainderPanel;

    public TemplateView(RootPanel rootPanel, String plateName, String plateTypeName, String templateName)
    {
        _rootPanel = rootPanel;
        _templateName = plateName;
        _assayTypeName = plateTypeName;
        _templateTypeName = templateName;
        _copyMode = Boolean.valueOf(PropertyUtil.getServerProperty("copyTemplate")).booleanValue();
    }

    public void showAsync()
    {
        _rootPanel.clear();
        _rootPanel.add(new Label("Loading..."));
        getService().getTemplateDefinition(_templateName, _assayTypeName, _templateTypeName, new AsyncCallback()
        {
            public void onFailure(Throwable throwable)
            {
                VerticalPanel mainPanel = new VerticalPanel();
                mainPanel.add(new Label("Unable to load plate template: " + throwable.getMessage()));
                _rootPanel.add(mainPanel);
            }

            public void onSuccess(Object object)
            {
                _plate = (GWTPlate) object;
                show();
            }
        });
    }

    private PlateDataServiceAsync getService()
    {
        if (_testService == null)
        {
            _testService = (PlateDataServiceAsync) GWT.create(PlateDataService.class);
            ServiceUtil.configureEndpoint(_testService, "designerService");
        }
        return _testService;
    }

    private void resize(int width, int height)
    {
        // Set the size of main body scroll panel so that it fills the browser.
        int fullWidth = Math.max(width - getAbsoluteLeft() - 20, 0);
        int rightPanelWidth = Math.min(fullWidth - _typePanel.getOffsetWidth(), 500);

//        setSize(null, Math.max(height - getAbsoluteTop() - 10, 0) + "px");

        _propertyTabPanel.setWidth(rightPanelWidth + "px");
    }

    private void show()
    {
        VerticalPanel mainPanel = new VerticalPanel();
        List groupTypes = _plate.getGroupTypes();
        _originalTemplateName = _plate.getName();

        // status bar
        _statusBar = new StatusBar(this, PropertyUtil.getRelativeURL("begin"));
        mainPanel.add(_statusBar);
        mainPanel.setCellHeight(_statusBar, "30px");

        // plate name textbox:
        _nameBox = new TextBox();
        _nameBox.setVisibleLength(40);
        String plateName = PropertyUtil.getServerProperty("defaultPlateName");
        if (plateName == null)
        {
            plateName = "";
        }
        _nameBox.setText(plateName);
        _plate.setName(plateName);
        _nameBox.addKeyboardListener(new KeyboardListenerAdapter()
        {
            public void onKeyPress(Widget sender, char keyCode, int modifiers)
            {
                setDirty(true);
            }
        });

        _nameBox.addChangeListener(new ChangeListener()
        {
            public void onChange(Widget sender)
            {
                String newName = _nameBox.getText();
                _plate.setName(newName.trim());
                if (!_nameBox.getText().equals(_plate.getName()))
                {
                    _nameBox.setText(_plate.getName());
                }
            }
        });

        HorizontalPanel namePanel = new HorizontalPanel();
        Label templateNameLabel = new Label("Template Name: ");
        namePanel.add(templateNameLabel);
        SimplePanel spacer = new SimplePanel();
        spacer.setWidth("5px");
        namePanel.add(spacer);
        namePanel.add(_nameBox);
        mainPanel.add(namePanel);
        namePanel.setCellVerticalAlignment(templateNameLabel, HasVerticalAlignment.ALIGN_MIDDLE);
        mainPanel.setCellHeight(namePanel, "30px");

        // well groups and well grid
        _grid = new TemplateGrid(this, (String) groupTypes.get(0));
        _typePanel = new GroupTypesTabPanel(this);
        HorizontalPanel hpanel = new HorizontalPanel();
        hpanel.add(_typePanel);
        mainPanel.add(hpanel);
        mainPanel.setCellHeight(hpanel, "100%");

        add(mainPanel);

        _propertyTabPanel = new TabPanel();
        _propertyTabPanel.setWidth("500px");

        _platePropertyPanel = new PlatePropertyPanel(this);
        _propertyTabPanel.add(_platePropertyPanel, "Plate Properties");

        _wellGroupPropertyPanel = new WellGroupPropertyPanel(this);
        //_propertyTabPanel.add(_wellGroupPropertyPanel, "Well Group Properties");

        _warningPanel = new WarningPanel();
        _propertyTabPanel.add(_warningPanel, "Warnings");

        _propertyTabPanel.selectTab(0);

        SimplePanel spacer2 = new SimplePanel();
        spacer2.setWidth("5px");
        add(spacer2);

        add(_propertyTabPanel);

        refreshWarnings(_grid.getAllCells());
        _rootPanel.clear();
        _rootPanel.add(this);
        resize(Window.getClientWidth(), Window.getClientHeight());
        Window.addWindowResizeListener(new WindowResizeListener()
        {
            public void onWindowResized(int width, int height)
            {
                resize(width, height);
            }

        });
        Window.addWindowCloseListener(new WindowCloseListener()
        {
            public void onWindowClosed()
            {
            }

            public String onWindowClosing()
            {
                if (_dirty)
                    return "Changes have not been saved and will be discarded.";
                else
                    return null;
            }
        });

        if (_copyMode)
            setDirty(true);
    }

    private Set getAllTemplateNames()
    {
        if (_existingTemplateNames == null)
        {
            String current;
            _existingTemplateNames = new HashSet();
            int idx = 0;
            do
            {
                current = PropertyUtil.getServerProperty("templateName[" + idx++ + "]");
                if (current != null)
                    _existingTemplateNames.add(current);
            } while (current != null);
        }
        return _existingTemplateNames;
    }

    private void setDirty(boolean dirty)
    {
        _dirty = dirty;
        _statusBar.setDirty(dirty);
    }

    public void markAsDirty()
    {
        setDirty(true);
    }

    public void saveChanges()
    {
        String templateName = _nameBox.getText().trim();
        if (templateName == null || templateName.length() == 0)
        {
            Window.alert("A plate name must be specified.");
            return;
        }
        if (_copyMode || !templateName.equals(_originalTemplateName))
        {
            Set allTemplateNames = getAllTemplateNames();
            if (allTemplateNames.contains(templateName))
            {
                Window.alert("A plate template with this name already exists.  Please choose another name.");
                return;
            }
        }
        _plate.setPlateProperties(_platePropertyPanel.getProperties());
        if (_activeGroup != null)
        {
            _activeGroup.setProperties(_wellGroupPropertyPanel.getProperties());
        }
        setStatus("Saving...");
        getService().saveChanges(_plate,  !_copyMode, new AsyncCallback()
        {
            public void onFailure(Throwable throwable)
            {
                setStatus("Save failed: " + throwable.getMessage());
                setDirty(true);
            }

            public void onSuccess(Object object)
            {
                _copyMode = false;
                setStatus("Saved.");
            }
        });
        setDirty(false);
    }

    public void setStatus(String status)
    {
        _statusBar.setStatus(status);
    }

    public void onMouseEnterCell(TemplateGridCell cell)
    {
    }

    public void onMouseLeaveCell(TemplateGridCell cell)
    {
    }

    public void onClickCell(TemplateGridCell cell)
    {
        if (_activeGroup != null)
        {
            if (cell.getActiveGroup() == _activeGroup)
                cell.replaceActiveGroup(null);
            else
                cell.replaceActiveGroup(_activeGroup);
            setDirty(true);
            List cellList = new ArrayList();
            cellList.add(cell);
            refreshWarnings(cellList);
        }
    }

    private void refreshWarnings(List modifiedCells)
    {
        boolean warningsChanged = false;
        for (Iterator cellIt = modifiedCells.iterator(); cellIt.hasNext(); )
        {
            TemplateGridCell cell = (TemplateGridCell) cellIt.next();
            List warnings = cell.getWarnings();
            String key = cell.toString();
            if (warnings == null)
            {
                if (_cellWarnings.containsKey(key))
                {
                    _cellWarnings.remove(key);
                    warningsChanged = true;
                }
            }
            else
            {
                _cellWarnings.put(key, warnings);
                warningsChanged = true;
            }
        }
        if (warningsChanged)
        {
            _warningPanel.update(_cellWarnings);
            int currentTab = _propertyTabPanel.getTabBar().getSelectedTab();
            _propertyTabPanel.remove(_warningPanel);
            if (_cellWarnings.isEmpty())
            {
                _propertyTabPanel.add(_warningPanel, "Warnings");
            }
            else
            {
                _propertyTabPanel.add(_warningPanel, "<font class=\"labkey-error\">Warnings (" + _cellWarnings.size() + ")</a>", true);
            }
            _propertyTabPanel.selectTab(currentTab);
        }
    }

    public void deleteWellGroup(GWTWellGroup group)
    {
        setDirty(true);
        _plate.removeGroup(group);
        List listenersCopy = new ArrayList(_groupListeners);
        for (Iterator it = listenersCopy.iterator(); it.hasNext();)
            ((GroupChangeListener) it.next()).groupRemoved(group);
        _groupToColorMap.remove(group);
        setActiveGroup(null);
        List cells = new ArrayList();
        for (Iterator positionIt = group.getPositions().iterator(); positionIt.hasNext(); )
            cells.add(_grid.getTemplateGridCell((GWTPosition) positionIt.next()));
        refreshWarnings(cells);
    }

    public void createWellGroup(String groupName, String type)
    {
        setDirty(true);
        Map properties = getPropertiesForType(type);
        properties.put("Type", type);
        properties.put("Name", groupName);
        GWTWellGroup group = new GWTWellGroup(type, groupName, new ArrayList(), properties);
        _plate.addGroup(group);
        List listenersCopy = new ArrayList(_groupListeners);
        for (Iterator it = listenersCopy.iterator(); it.hasNext();)
            ((GroupChangeListener) it.next()).groupAdded(group);
        setActiveGroup(group);
    }

    public void setActiveType(String activeType)
    {
        setActiveGroup(null);
        List listenersCopy = new ArrayList(_groupListeners);
        for (Iterator it = listenersCopy.iterator(); it.hasNext();)
            ((GroupChangeListener) it.next()).activeGroupTypeChanged(activeType);
    }

    public void addGroupListener(GroupChangeListener listener)
    {
        _groupListeners.add(listener);
    }

    public void removeGroupListener(GroupChangeListener listener)
    {
        _groupListeners.remove(listener);
    }

    public void setActiveGroup(GWTWellGroup group)
    {
        GWTWellGroup previous = _activeGroup;
        _activeGroup = group;
        List listenersCopy = new ArrayList(_groupListeners);
        for (Iterator it = listenersCopy.iterator(); it.hasNext();)
            ((GroupChangeListener) it.next()).activeGroupChanged(previous, group);
    }

    public GWTPlate getPlate()
    {
        return _plate;
    }

    public String getColor(GWTWellGroup group)
    {
        String color = (String) _groupToColorMap.get(group);
        if (color == null)
        {
            color = _colorGenerator.next();
            _groupToColorMap.put(group, color);
        }
        return color;
    }

    private Map getPropertiesForType(String type)
    {
        List groups = (List) _plate.getTypeToGroupsMap().get(type);
        if (groups != null && groups.size() > 0)
            return ((GWTWellGroup) groups.get(0)).getProperties();
        else
            return new HashMap();
    }
    
    public TemplateGrid getGrid()
    {
        return _grid;
    }

    public void setHighlightGroup(GWTWellGroup group, boolean on)
    {
        _grid.highlightGroup(group, on);
    }
}
