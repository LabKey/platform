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

import com.google.gwt.event.dom.client.*;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.IncrementalCommand;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;

import java.util.List;

import gwt.client.org.labkey.plate.designer.client.model.GWTWellGroup;
import org.labkey.api.gwt.client.ui.BoundTextBox;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.WidgetUpdatable;
import org.labkey.api.gwt.client.ui.WindowUtil;
import org.labkey.api.gwt.client.util.StringProperty;

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
        List wellgroups = _view.getPlate().getTypeToGroupsMap().get(_type);
        FlexTable groupList = new FlexTable();
        groupList.setCellPadding(5);
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
        createButton.addClickHandler(new ClickHandler()
        {
            public void onClick(ClickEvent event)
            {
                _view.createWellGroup(newGroupTextBox.getText(), _type);
            }
        });

        final ImageButton multiCreateButton = new ImageButton("Create multiple...");
        multiCreateButton.addClickHandler(new ClickHandler()
        {
            public void onClick(ClickEvent event)
            {
                String defaultBaseName = newGroupTextBox.getText();
                if (NEW_GROUP_DEFAULT_TEXT.equals(defaultBaseName))
                    defaultBaseName = "";

                MultiCreatePopupPanel multiCreatePanel = new MultiCreatePopupPanel(_view, defaultBaseName, _type);
                multiCreatePanel.center();
                multiCreatePanel.show();
            }
        });

        newGroupTextBox.setText(NEW_GROUP_DEFAULT_TEXT);
        newGroupTextBox.addFocusHandler(new FocusHandler()
        {
            public void onFocus(FocusEvent event)
            {
                if (NEW_GROUP_DEFAULT_TEXT.equals(newGroupTextBox.getText()))
                    newGroupTextBox.setText("");
            }
        });

        newGroupTextBox.addBlurHandler(new BlurHandler()
        {
            public void onBlur(BlurEvent event)
            {
                if ("".equals(newGroupTextBox.getText()))
                    newGroupTextBox.setText(NEW_GROUP_DEFAULT_TEXT);
            }
        });

        newGroupTextBox.addKeyUpHandler(new KeyUpHandler()
        {
            public void onKeyUp(KeyUpEvent event)
            {
                boolean enable = (newGroupTextBox.getText().length() > 0 && !NEW_GROUP_DEFAULT_TEXT.equals(newGroupTextBox.getText()));
                createButton.setEnabled(enable);
                multiCreateButton.setEnabled(enable);
            }
        });

        newGroupTextBox.addKeyDownHandler(new KeyDownHandler()
        {
            public void onKeyDown(KeyDownEvent event)
            {
                if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER)
                {
                    _view.createWellGroup(newGroupTextBox.getText(), _type);
                }
            }
        });

        groupList.setWidget(groupList.getRowCount(), 0, new Label("New:"));
        groupList.setWidget(groupList.getRowCount() - 1, 1, newGroupTextBox);
        groupList.setWidget(groupList.getRowCount() - 1, 2, createButton);
        groupList.setWidget(groupList.getRowCount() - 1, 3, multiCreateButton);
    }

    private static class MultiCreatePopupPanel extends DialogBox
    {
        StringProperty _count = new StringProperty("2");
        StringProperty _baseName = new StringProperty();
        public MultiCreatePopupPanel(final TemplateView view, final String baseName, final String type)
        {
            super(true, false);
            _baseName.set(baseName);
            setText("Create Multiple Groups");
            int row = 0;
            FlexTable options = new FlexTable();

            ImageButton createButton = new ImageButton("Create");
            createButton.addClickHandler(new ClickHandler()
            {
                public void onClick(ClickEvent event)
                {
                    try
                    {
                        final int count = Integer.parseInt(_count.getString());
                        MultiCreatePopupPanel.this.clear();
                        final Label status = new Label("Creating " + _baseName + " 1...");
                        MultiCreatePopupPanel.this.add(status);
                        // Use a deferred command to allow the UI to update after each well group is added:
                        DeferredCommand.addCommand(new IncrementalCommand()
                        {
                            private int _created = 0;
                            public boolean execute()
                            {
                                String groupName = _baseName + " " + (_created + 1);
                                view.createWellGroup(groupName, type);
                                if (++_created < count)
                                {
                                    // Set the status for the next item.  The UI will refresh now, after we return, but before
                                    // the next item is created.  The more intuitive approach of setting the status right before
                                    // the call to createWellGroup doesn't work, since the UI won't refresh between the calls.
                                    status.setText("Creating " + _baseName + " " + (_created + 1) + "...");
                                    return true;
                                }
                                else
                                {
                                    MultiCreatePopupPanel.this.hide();
                                    return false;
                                }
                            }
                        });
                    }
                    catch (NumberFormatException e)
                    {
                        Window.alert("\"" + _count.getString() + "\" is not a valid count.");
                    }
                }
            });

            ImageButton cancelButton = new ImageButton("Cancel");
            cancelButton.addClickHandler(new ClickHandler()
            {
                public void onClick(ClickEvent event)
                {
                    MultiCreatePopupPanel.this.hide();
                }
            });

            options.setWidget(row, 0, new Label("Base Name"));
            options.setWidget(row++, 1, new BoundTextBox("Base Name", "baseName", _baseName));

            options.setWidget(row, 0, new Label("Count"));
            options.setWidget(row++, 1, new BoundTextBox("Count", "createCount", _count)
            {
                @Override
                protected String validateValue(String text)
                {
                    try
                    {
                        int count = Integer.parseInt(text);
                        return super.validateValue(text);
                    }
                    catch (NumberFormatException e)
                    {
                        return "\"" + text + "\" is not a valid count.";
                    }
                }
            });

            HorizontalPanel buttonBar = new HorizontalPanel();
            buttonBar.add(cancelButton);
            buttonBar.add(createButton);
            options.setWidget(row++, 1, buttonBar);
            add(options);
        }

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
