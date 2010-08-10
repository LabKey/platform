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
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.DOM;

import java.util.*;

import gwt.client.org.labkey.plate.designer.client.model.GWTWellGroup;
import gwt.client.org.labkey.plate.designer.client.model.GWTPosition;

/**
 * User: brittp
 * Date: Feb 6, 2007
 * Time: 7:14:17 PM
 */
public class TemplateGridCell extends FocusPanel
{
    private Set<GWTWellGroup> _groups;
    private String _activeType;
    private TemplateView _view;
    private GWTPosition _position;

    public TemplateGridCell(TemplateView view, GWTPosition position, Set<GWTWellGroup> groups, String activeType)
    {
        _view = view;
        _position = position;
        _groups = groups;
        _activeType = activeType;
        addMouseOverHandler(new MouseOverHandler()
        {
            public void onMouseOver(MouseOverEvent event)
            {
                GWTWellGroup activeGroup = getActiveGroup();
                if (activeGroup != null)
                    _view.setStatus("Well " + TemplateGrid.ALPHABET[_position.getRow()] + (_position.getCol() + 1) + ": " + activeGroup.getName());
                setHighlight(true);
                _view.onMouseOverCell((TemplateGridCell) event.getSource());
            }
        });

        addMouseOutHandler(new MouseOutHandler()
        {
            public void onMouseOut(MouseOutEvent event)
            {
                _view.setStatus("");
                setHighlight(false);
            }
        });

        addMouseDownHandler(new MouseDownHandler()
        {
            public void onMouseDown(MouseDownEvent event)
            {
                _view.onMouseDownCell((TemplateGridCell) event.getSource());
            }
        });

        _view.addGroupListener(new GroupChangeListenerAdapter()
        {
            public void activeGroupTypeChanged(String type)
            {
                _activeType = type;
                redraw();
            }

            public void groupRemoved(GWTWellGroup group)
            {
                if (_groups.contains(group))
                {
                    getGroups().remove(group);
                    redraw();
                }
            }
        });

        redraw();
    }

    public void setHighlight(boolean on)
    {
        Widget rowLabel = ((Grid) getParent()).getWidget(_position.getRow() + 1, 0);
        Widget colLabel = ((Grid) getParent()).getWidget(0, _position.getCol() + 1);
        DOM.setStyleAttribute(rowLabel.getElement(), "backgroundColor", on ? "#DDDDDD" : "#FFFFFF");
        DOM.setStyleAttribute(colLabel.getElement(), "backgroundColor", on ? "#DDDDDD" : "#FFFFFF");
        DOM.setStyleAttribute(getElement(), "border", on ? "1px solid black" : "1px solid gray");
    }

    private Set<GWTWellGroup> getGroups()
    {
        if (_groups == null)
            _groups = new HashSet<GWTWellGroup>();
        return _groups;
    }

    private void redraw()
    {
        String color = null;
        for (Iterator it = getGroups().iterator(); it.hasNext() && color == null;)
        {
            GWTWellGroup group = (GWTWellGroup) it.next();
            if (group.getType().equals(_activeType))
                color = _view.getColor(group);
        }
        if (color == null)
            color = "FFFFFF";
        DOM.setStyleAttribute(getElement(), "backgroundColor", "#" + color);
        DOM.setStyleAttribute(getElement(), "border", "1px solid gray");
    }

    public void replaceActiveGroup(GWTWellGroup group)
    {
        GWTWellGroup activeGroup = getActiveGroup();
        if (activeGroup != null)
        {
            getGroups().remove(activeGroup);
            activeGroup.removePosition(_position);
        }
        if (group != null)
        {
            group.addPosition(_position);
            getGroups().add(group);
        }
        redraw();
    }

    public List<String> getWarnings()
    {
        boolean replicate = false;
        boolean specimen = false;
        boolean control = false;
        for (GWTWellGroup group : getGroups())
        {
            if (group.getType().equals("SPECIMEN"))
                specimen = true;
            else if (group.getType().equals("CONTROL"))
                control = true;
            else if (group.getType().equals("REPLICATE"))
                replicate = true;
        }
        List<String> warnings = null;
        if (replicate && !(specimen || control))
            warnings = addWarning(warnings, "Well is a replicate, but is not part of a specimen or control group.");
        if (control && specimen)
            warnings = addWarning(warnings, "Well is in both a specimen and a control group.");
        return warnings;
    }

    private List<String> addWarning(List<String> warnings, String warning)
    {
        if (warnings == null)
            warnings = new ArrayList<String>();
        warnings.add(warning);
        return warnings;
    }

    public GWTWellGroup getActiveGroup()
    {
        for (GWTWellGroup group : getGroups())
        {
            if (group.getType().equals(_activeType))
                return group;
        }
        return null;
    }

    public GWTPosition getPosition()
    {
        return _position;
    }

    public String toString()
    {
        return TemplateGrid.ALPHABET[getPosition().getRow()] + "" + (getPosition().getCol() + 1);
    }
}
