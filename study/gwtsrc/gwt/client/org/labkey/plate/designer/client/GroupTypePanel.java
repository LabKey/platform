/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

import com.extjs.gxt.ui.client.event.ComponentEvent;
import com.extjs.gxt.ui.client.event.KeyListener;
import com.extjs.gxt.ui.client.event.SelectionChangedEvent;
import com.extjs.gxt.ui.client.event.SelectionChangedListener;
import com.extjs.gxt.ui.client.widget.form.ComboBox;
import com.extjs.gxt.ui.client.widget.form.Field;
import com.extjs.gxt.ui.client.widget.form.SimpleComboBox;
import com.extjs.gxt.ui.client.widget.form.TextField;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.IncrementalCommand;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ScrollPanel;
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

        KeyListener fieldKeyListener = new KeyListener(){

            @Override
            public void componentKeyDown(ComponentEvent event)
            {
                if (event.getKeyCode() == KeyCodes.KEY_ENTER)
                {
                    _view.createWellGroup(_newGroupField.getRawValue(), _type);
                }
                super.componentKeyDown(event);
            }

            @Override
            public void componentKeyUp(ComponentEvent event)
            {
                Field cmp = event.getComponent();
                if (cmp != null)
                {
                    String value = cmp.getRawValue();

                    boolean enable = (value.length() > 0);

                    _createButton.setEnabled(enable);
                    _multiCreateButton.setEnabled(enable);
                }
                super.componentKeyUp(event);
            }
        };

        // if the group type is furnishing any default group names, populate them into a combo box
        // else just use a text field
        Map<String, List<String>> typeToGroupMap = _view.getPlate().getTypesToDefaultGroups();

        if (!typeToGroupMap.containsKey(_type))
        {
            final TextField textField = new TextField();

            textField.setEmptyText(NEW_GROUP_DEFAULT_TEXT);
            textField.enableEvents(true);
            textField.addKeyListener(fieldKeyListener);

            _newGroupField = textField;
        }
        else
        {
            List<String> defaults = typeToGroupMap.get(_type);

            final SimpleComboBox selector = new SimpleComboBox<String>();

            selector.setEmptyText(NEW_GROUP_DEFAULT_TEXT);
            selector.enableEvents(true);
            selector.setTriggerAction(ComboBox.TriggerAction.ALL);
            selector.addKeyListener(fieldKeyListener);
            selector.addSelectionChangedListener(new SelectionChangedListener(){
                @Override
                public void selectionChanged(SelectionChangedEvent event)
                {
                    String value = selector.getRawValue();

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
                _view.createWellGroup(_newGroupField.getRawValue(), _type);
            }
        });

        _multiCreateButton = new ImageButton("Create multiple...");
        _multiCreateButton.addClickHandler(new ClickHandler()
        {
            public void onClick(ClickEvent event)
            {
                String defaultBaseName = _newGroupField.getRawValue();
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
