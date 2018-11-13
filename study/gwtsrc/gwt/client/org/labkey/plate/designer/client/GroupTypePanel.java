/*
 * Copyright (c) 2010-2018 LabKey Corporation
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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.IncrementalCommand;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.sencha.gxt.cell.core.client.form.ComboBoxCell;
import com.sencha.gxt.data.shared.StringLabelProvider;
import com.sencha.gxt.widget.core.client.form.Field;
import com.sencha.gxt.widget.core.client.form.SimpleComboBox;
import com.sencha.gxt.widget.core.client.form.TextField;
import gwt.client.org.labkey.plate.designer.client.model.GWTWellGroup;
import org.labkey.api.gwt.client.ui.BoundTextBox;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.util.StringProperty;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private Field _newGroupField;
    private ImageButton _createButton;
    private ImageButton _multiCreateButton;

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
        Set<GWTWellGroup> wellgroups = _view.getPlate().getTypeToGroupsMap().get(_type);
        FlexTable groupList = new FlexTable();
        groupList.setCellPadding(5);
        if (wellgroups != null)
        {
            int i=0;
            for (GWTWellGroup group : wellgroups)
            {
                GroupTypePanelRow row = new GroupTypePanelRow(_view, group);
                row.attach(groupList, i++);
            }
        }
        else
            groupList.setWidget(0, 1, new Label("No groups defined."));
        add(groupList);

        KeyDownHandler fieldKeyDownListener = new KeyDownHandler()
        {
            @Override
            public void onKeyDown(KeyDownEvent event)
            {
                if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER)
                {
                    // we might want to remove this capability (create wellgroup using keyboard ENTER), the later
                    // versions of GWT don't allow access to the raw value and the current behavior is components
                    // don't update their value until a blur event
                    _view.createWellGroup(String.valueOf(_newGroupField.getValue()), _type);
                }
            }
        };

        KeyUpHandler fieldKeyUpListener = new KeyUpHandler()
        {
            @Override
            public void onKeyUp(KeyUpEvent event)
            {
                _createButton.setEnabled(true);
                _multiCreateButton.setEnabled(true);
            }
        };

        // if the group type is furnishing any default group names, populate them into a combo box
        // else just use a text field
        Map<String, List<String>> typeToGroupMap = _view.getPlate().getTypesToDefaultGroups();

        if (!typeToGroupMap.containsKey(_type))
        {
            final TextField textField = new TextField();

            textField.setEmptyText(NEW_GROUP_DEFAULT_TEXT);
            textField.enableEvents();
            textField.addKeyDownHandler(fieldKeyDownListener);
            textField.addKeyUpHandler(fieldKeyUpListener);

            _newGroupField = textField;
        }
        else
        {
            List<String> defaults = typeToGroupMap.get(_type);

            final SimpleComboBox selector = new SimpleComboBox(new StringLabelProvider());

            selector.setEmptyText(NEW_GROUP_DEFAULT_TEXT);
            selector.enableEvents();
            selector.setTriggerAction(ComboBoxCell.TriggerAction.ALL);
            selector.addKeyDownHandler(fieldKeyDownListener);
            selector.addKeyUpHandler(fieldKeyUpListener);
            selector.addSelectionHandler(new SelectionHandler()
            {
                @Override
                public void onSelection(SelectionEvent event)
                {
                    String value = String.valueOf(event.getSelectedItem());

                    boolean enable = (value.length() > 0);

                    _createButton.setEnabled(enable);
                    _multiCreateButton.setEnabled(enable);
                }
            });

            for (String name : defaults)
                selector.add(name);

            _newGroupField = selector;
        }

        _createButton = new ImageButton("Create");
        _createButton.setEnabled(false);
        _createButton.addClickHandler(new ClickHandler()
        {
            public void onClick(ClickEvent event)
            {
                _view.createWellGroup(String.valueOf(_newGroupField.getValue()), _type);
            }
        });

        _multiCreateButton = new ImageButton("Create multiple...");
        _multiCreateButton.addClickHandler(new ClickHandler()
        {
            public void onClick(ClickEvent event)
            {
                String defaultBaseName = String.valueOf(_newGroupField.getValue());
                if (NEW_GROUP_DEFAULT_TEXT.equals(defaultBaseName))
                    defaultBaseName = "";

                MultiCreatePopupPanel multiCreatePanel = new MultiCreatePopupPanel(_view, defaultBaseName, _type);
                multiCreatePanel.center();
                multiCreatePanel.show();
            }
        });

        groupList.setWidget(groupList.getRowCount(), 0, new Label("New:"));
        groupList.setWidget(groupList.getRowCount() - 1, 1, _newGroupField);
        groupList.setWidget(groupList.getRowCount() - 1, 2, _createButton);
        groupList.setWidget(groupList.getRowCount() - 1, 3, _multiCreateButton);
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
