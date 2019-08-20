/*
 * Copyright (c) 2010-2016 LabKey Corporation
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

package gwt.client.org.labkey.plate.designer.client;

import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.WindowResizeListener;
import com.google.gwt.user.client.WindowCloseListener;
import com.google.gwt.user.client.DOM;
import com.google.gwt.core.client.GWT;
import gwt.client.org.labkey.plate.designer.client.model.GWTPlate;
import gwt.client.org.labkey.plate.designer.client.model.GWTWellGroup;
import gwt.client.org.labkey.plate.designer.client.model.GWTPosition;
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
    private int _plateId;
    private String _templateName;
    private TemplateGrid _grid;
    private List<GroupChangeListener> _groupListeners = new ArrayList<GroupChangeListener>();
    private GWTWellGroup _activeGroup;
    private StatusBar _statusBar;
    private GWTPlate _plate;
    private GroupTypesTabPanel _typePanel;
    private ColorGenerator _colorGenerator = new ColorGenerator();
    private Map<GWTWellGroup, String> _groupToColorMap = new HashMap<GWTWellGroup, String>();
    private PlateDataServiceAsync _testService;
    private boolean _dirty = false;
    private Set<String> _existingTemplateNames;
    private String _originalTemplateName;
    private TextBox _nameBox;
    private boolean _copyMode;
    private Map<String, List<String>> _cellWarnings = new HashMap<String, List<String>>();
    private TabPanel _propertyTabPanel;
    private boolean _showWarningPanel;
    private WarningPanel _warningPanel;
    private WellGroupPropertyPanel _wellGroupPropertyPanel;
    private PlatePropertyPanel _platePropertyPanel;
    private String _assayTypeName;
    private String _templateTypeName;
    private int _rowCount;
    private int _columnCount;
    private boolean _mouseDown = false;
    private TemplateGridCell _selectionStartCell;
    private TemplateGridCell _prevSelectionEndCell;
    private boolean _setSelected;
    private GWTWellGroup[][] _assignmentShapshot;
    private final List<TemplateGridCell> _updatedCellList = new ArrayList<TemplateGridCell>();
    private Timer _warningUpdateTimer = new Timer()
    {
        @Override
        public void run()
        {
            synchronized (_updatedCellList)
            {
                if (!_updatedCellList.isEmpty())
                    refreshWarnings(_updatedCellList);
                _updatedCellList.clear();
            }
        }
    };

    public TemplateView(RootPanel rootPanel, int plateId, String plateName, String plateTypeName, String templateName, int rowCount, int columnCount)
    {
        _rootPanel = rootPanel;
        _plateId = plateId;
        _templateName = plateName;
        _assayTypeName = plateTypeName;
        _templateTypeName = templateName;
        _rowCount = rowCount;
        _columnCount = columnCount;
        _copyMode = Boolean.valueOf(PropertyUtil.getServerProperty("copyTemplate")).booleanValue();
    }

    public void showAsync()
    {
        _rootPanel.clear();
        _rootPanel.add(new Label("Loading..."));
        getService().getTemplateDefinition(_templateName, _plateId, _assayTypeName, _templateTypeName, _rowCount, _columnCount, new AsyncCallback<GWTPlate>()
        {
            public void onFailure(Throwable throwable)
            {
                VerticalPanel mainPanel = new VerticalPanel();
                mainPanel.add(new Label("Unable to load plate template: " + throwable.getMessage()));
                _rootPanel.add(mainPanel);
            }

            public void onSuccess(GWTPlate plate)
            {
                _plate = plate;
                show();
            }
        });
    }

    private PlateDataServiceAsync getService()
    {
        if (_testService == null)
        {
            _testService = GWT.create(PlateDataService.class);
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
        _showWarningPanel = _plate.isShowWarningPanel();

        // status bar
        _statusBar = new StatusBar(this, PropertyUtil.getRelativeURL("begin"));
        mainPanel.add(_statusBar);
        mainPanel.setCellHeight(_statusBar, "30px");

        // plate name textbox:
        _nameBox = new TextBox();
        _nameBox.setVisibleLength(40);
        DOM.setElementAttribute(_nameBox.getElement(), "id", "templateName");
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

        // Wrap the tabPanel in a focuspanel so we can track mouse events:
        FocusPanel focusPanel = new FocusPanel(_typePanel);
        focusPanel.addMouseDownHandler(new MouseDownHandler()
        {
            public void onMouseDown(MouseDownEvent event)
            {
                _mouseDown = true;
            }
        });
        focusPanel.addMouseUpHandler(new MouseUpHandler()
        {
            public void onMouseUp(MouseUpEvent event)
            {
                _mouseDown = false;
            }
        });

        hpanel.add(focusPanel);
        mainPanel.add(hpanel);
        mainPanel.setCellHeight(hpanel, "100%");

        add(mainPanel);

        _propertyTabPanel = new TabPanel();
        _propertyTabPanel.setWidth("500px");

        _platePropertyPanel = new PlatePropertyPanel(this);
        //_propertyTabPanel.add(_platePropertyPanel, "Plate Properties");

        _wellGroupPropertyPanel = new WellGroupPropertyPanel(this);
        _propertyTabPanel.add(_wellGroupPropertyPanel, "Well Group Properties");

        if (_showWarningPanel)
        {
            _warningPanel = new WarningPanel();
            _propertyTabPanel.add(_warningPanel, "Warnings");
        }
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
            _existingTemplateNames = new HashSet<String>();
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

    public void saveChanges(final AsyncCallback callback)
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
        getService().saveChanges(_plate, !_copyMode, new AsyncCallback()
        {
            public void onFailure(Throwable throwable)
            {
                setStatus("Save failed: " + throwable.getMessage());
                setDirty(true);
                callback.onFailure(throwable);
            }

            public void onSuccess(Object object)
            {
                _copyMode = false;
                setStatus("Saved.");
                setDirty(false);
                callback.onSuccess(object);
            }
        });
    }

    public void setStatus(String status)
    {
        _statusBar.setStatus(status);
    }

    public void onMouseOverCell(TemplateGridCell cell)
    {
        if (_mouseDown)
        {
            setStatus("Selection: " + _selectionStartCell + " to " + cell);
            // we appear to be doing a drag operation to select multiple cells- snapshot the well group assignments
            // now, so we can revert wells as the user drags around:
            if (_assignmentShapshot == null)
                _assignmentShapshot = snapshotWellAssignments();
            updateSelection(_selectionStartCell, cell, _prevSelectionEndCell, _setSelected);
            _prevSelectionEndCell = cell;
        }
        else
        {
            _selectionStartCell = null;
            _prevSelectionEndCell = null;
            _assignmentShapshot = null;
        }
    }

    private GWTWellGroup[][] snapshotWellAssignments()
    {
        GWTWellGroup[][] assignments = new GWTWellGroup[_grid.getTemplateGridRowCount()][_grid.getTemplateGridColumnCount()];
        for (int row = 0; row < assignments.length; row++)
        {
            for (int col = 0; col < assignments[row].length; col++)
                assignments[row][col] = _grid.getTemplateGridCell(new GWTPosition(row, col)).getActiveGroup();
        }
        return assignments;
    }

    public void onMouseDownCell(TemplateGridCell cell)
    {
        _selectionStartCell = cell;
        _setSelected = (cell.getActiveGroup() != _activeGroup);
        if (_activeGroup != null)
        {
            if (cell.getActiveGroup() == _activeGroup)
                cell.replaceActiveGroup(null);
            else
                cell.replaceActiveGroup(_activeGroup);
            setDirty(true);
            List<TemplateGridCell> cellList = new ArrayList<TemplateGridCell>();
            cellList.add(cell);
            refreshWarnings(cellList);
        }
    }

    public void refreshWarningsAsync()
    {
        refreshWarningsAsync(_grid.getAllCells());    
    }

    public void refreshWarningsAsync(List<TemplateGridCell> updatedCellList)
    {
        synchronized (_updatedCellList)
        {
            _updatedCellList.clear();
            _updatedCellList.addAll(updatedCellList);
            _warningUpdateTimer.cancel();
            _warningUpdateTimer.schedule(1000);
        }
    }

    private void updateSelection(TemplateGridCell startCell, TemplateGridCell endCell, TemplateGridCell prevEndCell, boolean setSelected)
    {
        // First, set the selection state of all cells in the current selection range:
        int startCol = Math.min(startCell.getPosition().getCol(), endCell.getPosition().getCol());
        int endCol = Math.max(startCell.getPosition().getCol(), endCell.getPosition().getCol());
        List<TemplateGridCell> cellList = new ArrayList<TemplateGridCell>();
        for (int col = startCol; col <= endCol; col++)
        {
            int startRow = Math.min(startCell.getPosition().getRow(), endCell.getPosition().getRow());
            int endRow = Math.max(startCell.getPosition().getRow(), endCell.getPosition().getRow());
            for (int row = startRow; row <= endRow; row++)
            {
                TemplateGridCell cell = this.getGrid().getTemplateGridCell(new GWTPosition(row, col));
                if (setSelected)
                    cell.replaceActiveGroup(_activeGroup);
                else
                    cell.replaceActiveGroup(null);
                cellList.add(cell);
            }
        }
        refreshWarningsAsync(cellList);

        // Second, de-select any cells that were previously selected in this same drag but which are no longer
        // selected.  (This can happen if the user drags a few columns/rows in one direction, overshoots, and backs
        // off one column/row.)  We know that the user has backed off if (and only if) the previous selection end is
        // no longer inside the rectangle defined by the current selection.
        if (prevEndCell != null && !prevEndCell.getPosition().inside(startCell.getPosition(), endCell.getPosition()))
        {
            for (int row = 0; row < _grid.getTemplateGridRowCount(); row++)
            {
                for (int col = 0; col < _grid.getTemplateGridColumnCount(); col++)
                {
                    GWTPosition pos = new GWTPosition(row, col);
                    // Any cells that were previously included in the selection range but which are now excluded need to be
                    // reset to their original state:
                    if (pos.inside(startCell.getPosition(), prevEndCell.getPosition()) && !pos.inside(startCell.getPosition(), endCell.getPosition()))
                        _grid.getTemplateGridCell(pos).replaceActiveGroup(_assignmentShapshot[row][col]);
                }
            }
        }
    }

    private void refreshWarnings(List<TemplateGridCell> modifiedCells)
    {
        boolean warningsChanged = false;
        for (TemplateGridCell cell : modifiedCells)
        {
            List<String> warnings = cell.getWarnings();
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
        if (warningsChanged && _showWarningPanel)
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
        List<GroupChangeListener> listenersCopy = new ArrayList<GroupChangeListener>(_groupListeners);
        for (GroupChangeListener listener : listenersCopy)
            listener.groupRemoved(group);
        _groupToColorMap.remove(group);
        setActiveGroup(null);
        List<TemplateGridCell> cells = new ArrayList<TemplateGridCell>();
        for (GWTPosition position : group.getPositions())
            cells.add(_grid.getTemplateGridCell(position));
        refreshWarnings(cells);
    }

    public void createWellGroup(String groupName, String type)
    {
        Map<String, Object> properties = getPropertiesForType(type);
        properties.put("Type", type);
        properties.put("Name", groupName);
        GWTWellGroup group = new GWTWellGroup(type, groupName, new ArrayList<GWTPosition>(), properties);
        if (_plate.addGroup(group))
        {
            setDirty(true);
            List<GroupChangeListener> listenersCopy = new ArrayList<GroupChangeListener>(_groupListeners);
            for (GroupChangeListener listener : listenersCopy)
                listener.groupAdded(group);
            setActiveGroup(group);
        }
    }

    public void setActiveType(String activeType)
    {
        setActiveGroup(null);
        List<GroupChangeListener> listenersCopy = new ArrayList<GroupChangeListener>(_groupListeners);
        for (GroupChangeListener listener : listenersCopy)
            listener.activeGroupTypeChanged(activeType);
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
        List<GroupChangeListener> listenersCopy = new ArrayList<GroupChangeListener>(_groupListeners);
        for (GroupChangeListener listener : listenersCopy)
            listener.activeGroupChanged(previous, group);
    }

    public GWTPlate getPlate()
    {
        return _plate;
    }

    public String getColor(GWTWellGroup group)
    {
        String color = _groupToColorMap.get(group);
        if (color == null)
        {
            color = _colorGenerator.next();
            _groupToColorMap.put(group, color);
        }
        return color;
    }

    private Map<String, Object> getPropertiesForType(String type)
    {
        Set<GWTWellGroup> groups = _plate.getTypeToGroupsMap().get(type);
        if (groups != null && !groups.isEmpty())
            return groups.iterator().next().getProperties();
        else
            return new HashMap<String, Object>();
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
