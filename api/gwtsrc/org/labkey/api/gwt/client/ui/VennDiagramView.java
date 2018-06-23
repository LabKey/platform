/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.api.gwt.client.ui;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.LoadEvent;
import com.google.gwt.event.dom.client.LoadHandler;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.DOM;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.model.GWTComparisonMember;
import org.labkey.api.gwt.client.model.GWTComparisonGroup;
import org.labkey.api.gwt.client.model.GWTComparisonResult;

import java.util.Map;
import java.util.HashMap;

/**
 * User: jeckels
 * Date: Jun 6, 2007
 */
public abstract class VennDiagramView extends HorizontalPanel
{
    private static final String GROUP_A_COLOR = "ff9955";
    private static final String GROUP_B_COLOR = "7fc659";
    private static final String GROUP_C_COLOR = "75a4fb";
    private static final String OVERLAP_AB_COLOR = "7fa420";
    private static final String OVERLAP_AC_COLOR = "7582c2";
    private static final String OVERLAP_BC_COLOR = "4a91c4";
    private static final String OVERLAP_ABC_COLOR = "4a86b1";

    private FlexTable _groupWebPart;
    private Grid _groupGrid;
    private Image _vennDiagram;
    private ListBox[] _groupListBoxes = new ListBox[3];

    private HTML _group1Label = new HTML(" ");
    private HTML _group2Label = new HTML(" ");
    private HTML _group3Label = new HTML(" ");

    private Label _group1CountLabel = new Label(" ");
    private Label _group2CountLabel = new Label(" ");
    private Label _group3CountLabel = new Label(" ");
    private Label _overlapABLabel = new Label(" ");
    private Label _overlapACLabel = new Label(" ");
    private Label _overlapBCLabel = new Label(" ");
    private Label _overlapABCLabel = new Label(" ");

    private static final String APPROXIMATE_CHART_DISCLAIMER = "Please note: diagram is only approximate.";

    private Label _warningLabel = new Label(APPROXIMATE_CHART_DISCLAIMER);

    private GWTComparisonGroup[] _groups;
    private int _totalCount;
    private GWTComparisonMember[] _members;
    private Map<String, GWTComparisonGroup> _idsToGroups = new HashMap<String, GWTComparisonGroup>();
    private String _memberDescription;

    public void initialize(Panel rootPanel)
    {
        VerticalPanel panel = new VerticalPanel();
        panel.setVerticalAlignment(HasAlignment.ALIGN_TOP);
        panel.setHorizontalAlignment(HasAlignment.ALIGN_LEFT);

        _groupWebPart = new FlexTable();
        panel.add(_groupWebPart);

        _vennDiagram = new Image();
        _vennDiagram.setHeight("300px");
        _vennDiagram.setWidth("300px");
        _vennDiagram.setVisible(false);
        _vennDiagram.addLoadHandler(new LoadHandler()
        {
            public void onLoad(LoadEvent e)
            {
                _vennDiagram.setVisible(true);
                _warningLabel.setText(APPROXIMATE_CHART_DISCLAIMER);
            }
        });

        ChangeHandler changeHandler = new ChangeHandler()
        {
            public void onChange(ChangeEvent e)
            {
                refreshDiagram();
            }
        };

        for (int i = 0; i < _groupListBoxes.length; i++)
        {
            _groupListBoxes[i] = new ListBox();
            _groupListBoxes[i].addChangeHandler(changeHandler);
        }

        FlexTable selectionTable = new FlexTable();
        selectionTable.setStylePrimaryName("lk-fields-table");
        selectionTable.setCellSpacing(5);
        
        int row = 0;
        int col = 0;

        Label setNameLabel = new Label("Set Name");
        DOM.setStyleAttribute(setNameLabel.getElement(), "fontWeight", "bold");
        selectionTable.setWidget(row, col++, setNameLabel);
        Label countLabel = new Label("Count");
        DOM.setStyleAttribute(countLabel.getElement(), "fontWeight", "bold");
        selectionTable.setWidget(row, col++, countLabel);
        Label description = new Label("Description");
        DOM.setStyleAttribute(description.getElement(), "fontWeight", "bold");
        selectionTable.setWidget(row, col++, description);
        row++;
        col = 0;

        selectionTable.setWidget(row, col++, _group1Label);
        setGroupLabel(_group1Label, " ", "ffffff");
        selectionTable.setWidget(row, col++, _group1CountLabel);
        selectionTable.setWidget(row, col++, _groupListBoxes[0]);
        row++;
        col = 0;

        selectionTable.setWidget(row, col++, _group2Label);
        setGroupLabel(_group2Label, " ", "ffffff");
        selectionTable.setWidget(row, col++, _group2CountLabel);
        selectionTable.setWidget(row, col++, _groupListBoxes[1]);
        row++;
        col = 0;

        selectionTable.setWidget(row, col++, _group3Label);
        setGroupLabel(_group3Label, " ", "ffffff");
        selectionTable.setWidget(row, col++, _group3CountLabel);
        selectionTable.setWidget(row, col++, _groupListBoxes[2]);
        row++;
        col = 0;
        
        selectionTable.setWidget(row, col++, setGroupLabel(new HTML(), "A&amp;B", OVERLAP_AB_COLOR));
        selectionTable.setWidget(row, col++, _overlapABLabel);
        selectionTable.setWidget(row, col++, new Label("Overlap between groups A and B"));
        row++;
        col = 0;

        selectionTable.setWidget(row, col++, setGroupLabel(new HTML(), "A&amp;C", OVERLAP_AC_COLOR));
        selectionTable.setWidget(row, col++, _overlapACLabel);
        selectionTable.setWidget(row, col++, new Label("Overlap between groups A and C"));
        row++;
        col = 0;

        selectionTable.setWidget(row, col++, setGroupLabel(new HTML(), "B&amp;C", OVERLAP_BC_COLOR));
        selectionTable.setWidget(row, col++, _overlapBCLabel);
        selectionTable.setWidget(row, col++, new Label("Overlap between groups B and C"));
        row++;
        col = 0;

        selectionTable.setWidget(row, col++, setGroupLabel(new HTML(), "A&amp;B&amp;C", OVERLAP_ABC_COLOR));
        selectionTable.setWidget(row, col++, _overlapABCLabel);
        selectionTable.setWidget(row, col, new Label("Overlap between all three groups"));
        row++;

        selectionTable.setWidget(row++, 2, _warningLabel);

        setupCountLabel(_group1CountLabel);
        setupCountLabel(_group2CountLabel);
        setupCountLabel(_group3CountLabel);
        setupCountLabel(_overlapABLabel);
        setupCountLabel(_overlapACLabel);
        setupCountLabel(_overlapBCLabel);
        setupCountLabel(_overlapABCLabel);

        HorizontalPanel diagramPanel = new HorizontalPanel();
        diagramPanel.setVerticalAlignment(HasAlignment.ALIGN_TOP);
        diagramPanel.setHorizontalAlignment(HasAlignment.ALIGN_LEFT);
        diagramPanel.add(selectionTable);
        diagramPanel.add(_vennDiagram);

        panel.add(diagramPanel);

        rootPanel.add(panel);

        refreshDiagram();
    }

    private void setupCountLabel(Widget widget)
    {
        DOM.setStyleAttribute(widget.getElement(), "textAlign", "right");
        DOM.setStyleAttribute(widget.getElement(), "padding", "2px");
        DOM.setStyleAttribute(widget.getElement(), "fontFamily", "courier");
    }

    public void requestComparison()
    {
        String originalURL = PropertyUtil.getServerProperty("originalURL");
        String comparisonGroup = PropertyUtil.getServerProperty("comparisonName");
        AsyncCallback<GWTComparisonResult> callbackHandler = new AsyncCallback<GWTComparisonResult>()
        {
            public void onFailure(Throwable caught)
            {
                _warningLabel.setText("ERROR: " + caught.toString());
            }

            public void onSuccess(GWTComparisonResult result)
            {
                setupTable(result);
            }
        };
        requestComparison(originalURL, comparisonGroup, callbackHandler);
    }

    protected abstract void requestComparison(String originalURL, String comparisonGroup, AsyncCallback<GWTComparisonResult> callbackHandler);

    private void setupTable(GWTComparisonResult comparisonResult)
    {
        _members = comparisonResult.getMembers();
        _groups = new GWTComparisonGroup[comparisonResult.getGroups().length + 1];

        // Create an implicit group that holds all the members
        GWTComparisonGroup allGroup = new GWTComparisonGroup();
        allGroup.setName("All");
        for (GWTComparisonMember _member : _members)
        {
            allGroup.addMember(_member);
        }
        _groups[0] = allGroup;

        for (int i = 0; i < comparisonResult.getGroups().length; i++)
        {
            _groups[i + 1] = comparisonResult.getGroups()[i];
        }

        _memberDescription = comparisonResult.getMemberDescription();

        for (ListBox _groupListBoxe : _groupListBoxes)
        {
            setupListBox(_groupListBoxe);
        }

        int index = 0;
        // Select the first three groups, if available
        while (index < _groups.length && index < 3)
        {
            _groupListBoxes[index].setSelectedIndex(index++);
        }

        int memberIndex = 0;
        // Fill up the rest of the selection with individual members, if available
        while (memberIndex < _members.length && index < 3)
        {
            // One blank line between the groups and the individual members
            _groupListBoxes[index].setSelectedIndex(1 + index++);
            memberIndex++;
        }

        _totalCount = comparisonResult.getTotalCount();

        refreshTables();
    }

    private void setupListBox(ListBox listBox)
    {
        listBox.clear();
        _idsToGroups = new HashMap<String, GWTComparisonGroup>();
        for (int i = 0; i < _groups.length; i++)
        {
            GWTComparisonGroup group = _groups[i];
            listBox.addItem(group.getName() + "  (Group)", "group" + i);
            _idsToGroups.put("group" + i, group);
        }
        listBox.addItem("");
        for (int i = 0; i < _members.length; i++)
        {
            GWTComparisonGroup group = new GWTComparisonGroup();
            group.addMember(_members[i]);
            group.setName(_members[i].getName() + "(Transient group)");
            listBox.addItem(_members[i].getName(), "member" + i);
            _idsToGroups.put("member" + i, group);
        }
    }

    private void refreshGroupsTable()
    {
        if (_groups.length > 0)
        {
            _groupGrid.resize(_groups.length + 2, 6);

            _groupGrid.setCellPadding(2);
            _groupGrid.setCellSpacing(3);
            Label groupNameLabel = new Label("Group");
            _groupGrid.setWidget(0, 0, groupNameLabel);
            DOM.setStyleAttribute(groupNameLabel.getElement(), "fontWeight", "bold");

            Label membersInGroupLabel = new Label(_memberDescription);
            _groupGrid.setWidget(0, 1, membersInGroupLabel);
            DOM.setStyleAttribute(membersInGroupLabel.getElement(), "fontWeight", "bold");

            for (int i = 0; i < _groupGrid.getColumnCount(); i++)
            {
                Widget widget = _groupGrid.getWidget(0, i);
                if (widget == null)
                {
                    widget = new HTML("&nbsp;");
                    _groupGrid.setWidget(0, i, widget);
                }
            }

            for (int x = 0; x < _groups.length; x++)
            {
                GWTComparisonGroup group = _groups[x];
                if (group.getURL() != null)
                {
                    _groupGrid.setWidget(x + 1, 0, new HTML("<a href=\"" + group.getURL() + "\">" + group.getName() + "</a>"));
                }
                else
                {
                    _groupGrid.setWidget(x + 1, 0, new Label(group.getName()));
                }

                String separator = "";
                StringBuffer name = new StringBuffer();
                for (int i = 0; i < group.getMembers().size(); i++)
                {
                    GWTComparisonMember member = (GWTComparisonMember) group.getMembers().get(i);
                    name.append(separator);
                    if (member.getUrl() != null)
                    {
                        name.append("<a href=\"");
                        name.append(member.getUrl());
                        name.append("\">");
                    }
                    name.append(member.getName());
                    if (member.getUrl() != null)
                    {
                        name.append("</a>");
                    }
                    separator = "<br/>";
                }
                _groupGrid.setWidget(x + 1, 1, new HTML(name.toString()));
            }

            for (int row = 0; row < _groupGrid.getRowCount(); row++)
            {
                for (int col = 0; col < _groupGrid.getColumnCount(); col++)
                {
                    Widget w = _groupGrid.getWidget(row, col);
                    if (w != null)
                    {
                        DOM.setStyleAttribute(DOM.getParent(w.getElement()), "verticalAlign", "top");
                    }
                }
            }
        }
        else
        {
            _groupGrid.resize(1, 1);
            _groupGrid.setWidget(0, 0, new Label("None of the selected " + _memberDescription + " are part of a group."));
        }
    }

    private int getOverlapCount(GWTComparisonGroup group1, GWTComparisonGroup group2)
    {
        int result = 0;
        for (int i = 0; i < _totalCount; i++)
        {
            if ((group1 != null && group1.contains(i)) && (group2 != null && group2.contains(i)))
            {
                result++;
            }
        }
        return result;
    }

    private int getOverlapCount(GWTComparisonGroup group1, GWTComparisonGroup group2, GWTComparisonGroup group3)
    {
        int result = 0;
        for (int i = 0; i < _totalCount; i++)
        {
            if ((group1 != null && group1.contains(i)) && (group2 != null && group2.contains(i)) && (group3 != null && group3.contains(i)))
            {
                result++;
            }
        }
        return result;
    }

    private HTML setGroupLabel(HTML groupLabel, String name, String color)
    {
        groupLabel.setHTML("<div style='width: 5em; text-align: center; padding: 2px; background: #" + color + "'>" + name + "</div>");
        return groupLabel;
    }

    private void refreshDiagram()
    {
        GWTComparisonGroup group1 = getSelectedGroup(_groupListBoxes[0]);
        GWTComparisonGroup group2 = getSelectedGroup(_groupListBoxes[1]);
        GWTComparisonGroup group3 = getSelectedGroup(_groupListBoxes[2]);

        int size1 = group1 == null ? 0 : group1.getCount();
        int size2 = group2 == null ? 0 : group2.getCount();
        int size3 = group3 == null ? 0 : group3.getCount();

        int sizeA;
        int sizeB;
        int sizeC;
        GWTComparisonGroup groupA;
        GWTComparisonGroup groupB;
        GWTComparisonGroup groupC;
        // Need to reorder the groups since Google Charts chooses colors based on size
        if (size1 >= size2 && size1 >= size3)
        {
            // 1 is biggest
            sizeA = size1;
            groupA = group1;
            setGroupLabel(_group1Label, "A", GROUP_A_COLOR);
            if (size2 >= size3)
            {
                // 2 is middle, 3 is smallest
                sizeB = size2;
                groupB = group2;
                setGroupLabel(_group2Label, "B", GROUP_B_COLOR);
                sizeC = size3;
                groupC = group3;
                setGroupLabel(_group3Label, "C", GROUP_C_COLOR);
            }
            else
            {
                // 3 is middle, 2 is smallest
                sizeB = size3;
                groupB = group3;
                setGroupLabel(_group3Label, "B", GROUP_B_COLOR);
                sizeC = size2;
                groupC = group2;
                setGroupLabel(_group2Label, "C", GROUP_C_COLOR);
            }
        }
        else if (size2 >= size1 && size2 >= size3)
        {
            // 2 is biggest
            sizeA = size2;
            groupA = group2;
            setGroupLabel(_group2Label, "A", GROUP_A_COLOR);
            if (size1 >= size3)
            {
                // 1 is middle, 3 is smallest
                sizeB = size1;
                groupB = group1;
                setGroupLabel(_group1Label, "B", GROUP_B_COLOR);
                sizeC = size3;
                groupC = group3;
                setGroupLabel(_group3Label, "C", GROUP_C_COLOR);
            }
            else
            {
                // 3 is middle, 1 is smallest
                sizeB = size3;
                groupB = group3;
                setGroupLabel(_group3Label, "B", GROUP_B_COLOR);
                sizeC = size1;
                groupC = group1;
                setGroupLabel(_group1Label, "C", GROUP_C_COLOR);
            }
        }
        else
        {
            // 3 is biggest
            sizeA = size3;
            groupA = group3;
            setGroupLabel(_group3Label, "A", GROUP_A_COLOR);
            if (size1 >= size2)
            {
                // 1 is middle, 2 is smallest
                sizeB = size1;
                groupB = group1;
                setGroupLabel(_group1Label, "B", GROUP_B_COLOR);
                sizeC = size2;
                groupC = group2;
                setGroupLabel(_group2Label, "C", GROUP_C_COLOR);
            }
            else
            {
                // 2 is middle, 1 is smallest
                sizeB = size2;
                groupB = group2;
                setGroupLabel(_group2Label, "B", GROUP_B_COLOR);
                sizeC = size1;
                groupC = group1;
                setGroupLabel(_group1Label, "C", GROUP_C_COLOR);
            }
        }

        int overlapAB = getOverlapCount(groupA, groupB);
        int overlapAC = getOverlapCount(groupA, groupC);
        int overlapBC = getOverlapCount(groupB, groupC);
        int overlapABC = getOverlapCount(groupA, groupB, groupC);

        _group1CountLabel.setText(Integer.toString(size1));
        _group2CountLabel.setText(Integer.toString(size2));
        _group3CountLabel.setText(Integer.toString(size3));
        _overlapABLabel.setText(Integer.toString(overlapAB));
        _overlapACLabel.setText(Integer.toString(overlapAC));
        _overlapBCLabel.setText(Integer.toString(overlapBC));
        _overlapABCLabel.setText(Integer.toString(overlapABC));

        double maxSize = Math.max(sizeA, Math.max(sizeB, sizeC));
        double scale = maxSize / 100;

        int scaledSizeA = (int)(sizeA / scale);
        int scaledSizeB = (int)(sizeB / scale);
        int scaledSizeC = (int)(sizeC / scale);
        int scaledOverlapAB = (int)(overlapAB / scale);
        int scaledOverlapAC = (int)(overlapAC / scale);
        int scaledOverlapBC = (int)(overlapBC / scale);
        int scaledOverlapABC = (int)(overlapABC / scale);

        _vennDiagram.setUrl("http://chart.apis.google.com/chart?cht=v&chd=t:" + scaledSizeA + "," + scaledSizeB + "," + scaledSizeC + "," + scaledOverlapAB + "," + scaledOverlapAC + "," + scaledOverlapBC + "," + scaledOverlapABC + "&chs=300x300&chco=" + GROUP_A_COLOR + "," + GROUP_B_COLOR + "," + GROUP_C_COLOR);
    }

    private GWTComparisonGroup getSelectedGroup(ListBox listBox)
    {
        int index = listBox.getSelectedIndex();
        if (index == -1)
        {
            return null;
        }
        String id = listBox.getValue(index);
        return _idsToGroups.get(id);
    }

    private void refreshTables()
    {
        _groupGrid = new Grid();
        
        refreshGroupsTable();
        refreshDiagram();

        _groupWebPart.setWidget(1, 0, _groupGrid);
    }

    private void highlightGroupMembers(String color1, int group1Index, String color2, int group2Index)
    {
        if (color1 != null && group1Index >= 0)
        {
            setBackground(_groupGrid.getWidget(group1Index + 1, 1), color1);
            setBackground(_groupGrid.getWidget(group1Index + 1, 0), color1);
//            setBackground(_overlapGrid.getWidget(group1Index, 0), color1);
        }

        if (color2 != null && group2Index >= 0)
        {
            setBackground(_groupGrid.getWidget(group2Index + 1, 1), color2);
            setBackground(_groupGrid.getWidget(group2Index + 1, 0), color2);
//            setBackground(_overlapGrid.getWidget(0, group2Index + 1), color2);
        }
    }

    private void setBackground(Widget widget, String color)
    {
        DOM.setStyleAttribute(widget.getElement(), "backgroundColor", color);
    }

}
