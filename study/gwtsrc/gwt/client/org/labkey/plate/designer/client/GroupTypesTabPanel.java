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
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.TabPanel;
import com.google.gwt.user.client.ui.TabListener;
import com.google.gwt.user.client.ui.SourcesTabEvents;
import com.google.gwt.user.client.ui.Grid;

import java.util.List;

/**
 * User: brittp
 * Date: Feb 7, 2007
 * Time: 11:14:32 AM
 */
public class GroupTypesTabPanel extends TabPanel
{
    private TemplateView _view;
    private List<String> _types;
    private int _gridTab = -1;

    public GroupTypesTabPanel(TemplateView view)
    {
        setHeight("100%");
        _view = view;
        _types = view.getPlate().getGroupTypes();
        for (String type : _types)
        {
            Grid tabPanelGrid = new Grid(2, 1);
            HorizontalPanel lowerPane = new HorizontalPanel();
            lowerPane.setWidth("100%");
            lowerPane.add(new GroupTypePanel(view, type));
            ShiftPanel shifter = new ShiftPanel(view);
            lowerPane.add(shifter);
            lowerPane.setCellHorizontalAlignment(shifter, HasHorizontalAlignment.ALIGN_RIGHT);
            tabPanelGrid.setWidget(1, 0, lowerPane);
            add(tabPanelGrid, type);
        }
        selectTab(0);
        addTabListener(new TabListener()
        {
            public boolean onBeforeTabSelected(SourcesTabEvents sender, int tabIndex)
            {
                return true;
            }

            public void onTabSelected(SourcesTabEvents sender, int tabIndex)
            {
                _view.setActiveType(_types.get(tabIndex));
                moveGrid(tabIndex);
            }
        });
    }

    public void selectTab(int index)
    {
        super.selectTab(index);
        moveGrid(index);
    }

    private void moveGrid(int selectedTab)
    {
        if (_gridTab >= 0)
        {
            Grid prevPanel = (Grid) getDeckPanel().getWidget(_gridTab);
            prevPanel.remove(_view.getGrid());
        }
        Grid currentPanel = (Grid) getDeckPanel().getWidget(selectedTab);

        TemplateGrid wellGrid = _view.getGrid();
        // Use a focus panel to prevent mouse events within the grid from being propagated to the browser.
        // This is necessary to enable click and drag functionality that doesn't perform browser-provided text selection.
        FocusPanel focusPanel = new FocusPanel(wellGrid);
        focusPanel.addMouseDownHandler(new MouseDownHandler()
        {
            public void onMouseDown(MouseDownEvent event)
            {
                event.preventDefault();
            }
        });
        focusPanel.addMouseUpHandler(new MouseUpHandler()
        {
            public void onMouseUp(MouseUpEvent event)
            {
                event.preventDefault();
            }
        });
        focusPanel.addMouseMoveHandler(new MouseMoveHandler()
        {
            public void onMouseMove(MouseMoveEvent event)
            {
                event.preventDefault();
            }
        });
        
        currentPanel.setWidget(0, 0, focusPanel);
        _gridTab = selectedTab;
    }

    public void redraw()
    {
        Grid panel = (Grid) getWidget(getTabBar().getSelectedTab());
        GroupTypePanel typesPanel = (GroupTypePanel) panel.getWidget(1, 0);
        typesPanel.redraw();
    }
}
