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

package gwt.client.org.labkey.plate.designer.client;

import com.google.gwt.user.client.ui.*;

import java.util.List;

import gwt.client.org.labkey.plate.designer.client.model.GWTWellGroup;
import org.labkey.api.gwt.client.ui.ImageButton;

/**
 * User: brittp
 * Date: Feb 7, 2007
 * Time: 11:16:28 AM
 */
public class GroupTypePanel extends ScrollPanel implements GroupChangeListener
{
    private TemplateView _view;
    private String _type;
    private static final String NEW_GROUP_DEFAULT_TEXT = "Enter Group Name";

    public GroupTypePanel(TemplateView view, String type)
    {
        _type = type;
        _view = view;
        _view.addGroupListener(this);
        redraw();
    }

    public void redraw()
    {
        clear();
        List wellgroups = (List) _view.getPlate().getTypeToGroupsMap().get(_type);
        FlexTable groupList = new FlexTable();
        groupList.setCellPadding(3);
        groupList.setCellSpacing(0);
        if (wellgroups != null)
        {
            for (int i = 0; i < wellgroups.size(); i++)
            {
                GWTWellGroup group = (GWTWellGroup) wellgroups.get(i);
                GroupTypePanelRow row = new GroupTypePanelRow(_view, group);
                row.attach(groupList, i);
            }
        }
        else
            groupList.setWidget(0, 1, new Label("No groups defined."));
        add(groupList);
        final TextBox newGroupTextBox = new TextBox();

        final ImageButton createButton = new ImageButton("Create");
        createButton.setEnabled(false);
        createButton.addClickListener(new ClickListener()
        {
            public void onClick(Widget sender)
            {
                _view.createWellGroup(newGroupTextBox.getText(), _type);
            }
        });

        newGroupTextBox.setText(NEW_GROUP_DEFAULT_TEXT);
        newGroupTextBox.addFocusListener(new FocusListener()
        {
            public void onFocus(Widget sender)
            {
                if (NEW_GROUP_DEFAULT_TEXT.equals(newGroupTextBox.getText()))
                    newGroupTextBox.setText("");
            }

            public void onLostFocus(Widget sender)
            {
                if ("".equals(newGroupTextBox.getText()))
                    newGroupTextBox.setText(NEW_GROUP_DEFAULT_TEXT);
            }
        });

        newGroupTextBox.addKeyboardListener(new KeyboardListenerAdapter()
        {
            public void onKeyUp(Widget sender, char keyCode, int modifiers)
            {
                boolean enable = (newGroupTextBox.getText().length() > 0 && !NEW_GROUP_DEFAULT_TEXT.equals(newGroupTextBox.getText()));
                createButton.setEnabled(enable);
            }
        });

        newGroupTextBox.addKeyboardListener(new KeyboardListenerAdapter()
        {
            public void onKeyDown(Widget sender, char keyCode, int modifiers)
            {
                if (keyCode == KeyboardListener.KEY_ENTER)
                {
                    _view.createWellGroup(newGroupTextBox.getText(), _type);
                }
            }
        });

        groupList.setWidget(groupList.getRowCount(), 0, new Label("New:"));
        groupList.setWidget(groupList.getRowCount() - 1, 1, newGroupTextBox);
        groupList.setWidget(groupList.getRowCount() - 1, 2, createButton);
    }


    public void activeGroupChanged(GWTWellGroup previouslyActive, GWTWellGroup currentlyActive)
    {
    }

    public void activeGroupTypeChanged(String type)
    {
    }

    public void groupAdded(GWTWellGroup group)
    {
        redraw();
    }

    public void groupRemoved(GWTWellGroup group)
    {
        redraw();
    }
}
