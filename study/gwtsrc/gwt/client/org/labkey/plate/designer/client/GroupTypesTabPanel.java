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

package gwt.client.org.labkey.plate.designer.client;

import com.google.gwt.user.client.ui.TabPanel;
import com.google.gwt.user.client.ui.TabListener;
import com.google.gwt.user.client.ui.SourcesTabEvents;
import com.google.gwt.user.client.ui.Grid;

import java.util.List;
import java.util.Iterator;

/**
 * User: brittp
 * Date: Feb 7, 2007
 * Time: 11:14:32 AM
 */
public class GroupTypesTabPanel extends TabPanel
{
    private TemplateView _view;
    private List _types;
    private int _gridTab = -1;

    public GroupTypesTabPanel(TemplateView view)
    {
        setHeight("100%");
        _view = view;
        _types = view.getPlate().getGroupTypes();
        for (Iterator it = _types.iterator(); it.hasNext(); )
        {
            String type = (String) it.next();
            Grid tabPanelGrid = new Grid(2, 1);
            tabPanelGrid.setWidget(1, 0, new GroupTypePanel(view, type));
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
                _view.setActiveType((String) _types.get(tabIndex));
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
        currentPanel.setWidget(0, 0, _view.getGrid());
        _gridTab = selectedTab;
    }

    public void redraw()
    {
        Grid panel = (Grid) getWidget(getTabBar().getSelectedTab());
        GroupTypePanel typesPanel = (GroupTypePanel) panel.getWidget(1, 0);
        typesPanel.redraw();
    }
}
