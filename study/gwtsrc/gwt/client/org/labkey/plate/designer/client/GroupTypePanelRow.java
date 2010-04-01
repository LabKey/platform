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

import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.Element;
import gwt.client.org.labkey.plate.designer.client.model.GWTWellGroup;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.TextBoxDialogBox;

/**
 * User: brittp
 * Date: Feb 7, 2007
 * Time: 3:28:08 PM
 */
public class GroupTypePanelRow extends GroupChangeListenerAdapter
{
    private TemplateView _view;
    private GWTWellGroup _group;
    private FocusPanel _colorPanel;
    private Element _tableRowElement;
    private RadioButton _radioButton;
    private ImageButton _deleteButton;
    private ImageButton _renameButton;

    public GroupTypePanelRow(TemplateView view, GWTWellGroup group)
    {
        _view = view;
        _group = group;
    }

    public void attach(FlexTable parent, int row)
    {
        int col = 0;

        _view.addGroupListener(this);
        _colorPanel = new FocusPanel();
        DOM.setStyleAttribute(_colorPanel.getElement(), "border", "1px solid white");
        DOM.setStyleAttribute(_colorPanel.getElement(), "backgroundColor", "#" + _view.getColor(_group));
        _colorPanel.setSize("35px", "25px");
        parent.setWidget(row, col++, _colorPanel);
        
        MouseListener hoverListener = new MouseListenerAdapter()
        {
            public void onMouseEnter(Widget sender)
            {
                DOM.setStyleAttribute(_colorPanel.getElement(), "border", "1px solid black");
                _view.setHighlightGroup(_group, true);
            }

            public void onMouseLeave(Widget sender)
            {
                DOM.setStyleAttribute(_colorPanel.getElement(), "border", "1px solid white");
                _view.setHighlightGroup(_group, false);
            }
        };

        ClickListener clickListener = new ClickListener()
        {
            public void onClick(Widget sender)
            {
                _view.setActiveGroup(_group);
            }
        };

        _colorPanel.addClickListener(clickListener);

        _radioButton = new RadioButton("wellGroup", _group.getName());
        FocusPanel radioButtonFocusPanel = new FocusPanel();
        radioButtonFocusPanel.addMouseListener(hoverListener);
        radioButtonFocusPanel.add(_radioButton);
        parent.setWidget(row, col++, radioButtonFocusPanel);
        _radioButton.addClickListener(clickListener);

        _colorPanel.addMouseListener(hoverListener);
  
        _deleteButton = new ImageButton("Delete");
        _deleteButton.setVisible(false);
        _deleteButton.addClickListener(new ClickListener()
        {
            public void onClick(Widget sender)
            {
                if (Window.confirm("Delete well group \"" + _group.getName() + "\"?"))
                    _view.deleteWellGroup(_group);
            }
        });
        parent.setWidget(row, col++, _deleteButton);

        _renameButton = new ImageButton("Rename");
        _renameButton.setVisible(false);
        _renameButton.addClickListener(new ClickListener()
        {
            public void onClick(Widget sender)
            {
                TextBoxDialogBox dialog = new TextBoxDialogBox("Rename Well Group", "Name")
                {
                    protected boolean commit(String value)
                    {
                        if (value.length() == 0)
                        {
                            Window.alert("You must specify a well group name.");
                            return false;
                        }
                        _group.setName(value);
                        _radioButton.setText(value);
                        _view.markAsDirty();
                        return true;
                    }
                };
                dialog.show(_group.getName());
            }
        });
        parent.setWidget(row, col++, _renameButton);

        _tableRowElement = parent.getRowFormatter().getElement(row);
    }

    public void activeGroupChanged(GWTWellGroup previouslyActive, GWTWellGroup currentlyActive)
    {
        if (_group == currentlyActive)
        {
            _radioButton.setChecked(true);
            _deleteButton.setVisible(true);
            _renameButton.setVisible(true);
            DOM.setStyleAttribute(_tableRowElement, "backgroundColor", "#DDDDDD");
            DOM.setStyleAttribute(_tableRowElement, "border", "1px solid black");
        }
        else if (_group == previouslyActive)
        {
            _radioButton.setChecked(false);
            _deleteButton.setVisible(false);
            _renameButton.setVisible(false);
            DOM.setStyleAttribute(_tableRowElement, "backgroundColor", "#FFFFFF");
            DOM.setStyleAttribute(_tableRowElement, "border", "1px solid white");
        }
    }
}
