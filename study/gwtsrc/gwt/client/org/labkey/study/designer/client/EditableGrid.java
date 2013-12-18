/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

package gwt.client.org.labkey.study.designer.client;

import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import org.labkey.api.gwt.client.util.StringUtils;

/**
 * User: Mark Igra
 * Date: Dec 14, 2006
 * Time: 2:38:59 PM
 */
public abstract class EditableGrid extends FlexTable
{
    private boolean ghostRow = true;
    boolean readOnly = false;
    int rowSelected = -1;

    protected EditableGrid()
    {
        setCellSpacing(0);
        addTableListener(new RowHeaderClickListener());
        setStyleName("labkey-data-region labkey-show-borders");
    }
    
    public void updateAll()
    {
        int headerRowCount = getHeaderRows();

        //Make bigger or smaller as needed.
        if (getRowCount() < getDataRowCount() + headerRowCount + (isGhostRow() ? 1 : 0))
            prepareRow(getDataRowCount() + headerRowCount);
        else
            while (getRowCount() > getDataRowCount() + headerRowCount + (isGhostRow() ? 1 : 0))
                removeRow(getRowCount() - 1);

        if (getCellCount(getRowCount() - 1) < getDataColumnCount())
            prepareCell(getDataRowCount() + headerRowCount, getDataColumnCount());
        else
            for (int i = 0; i < getRowCount(); i++)
                removeCells(i, getDataColumnCount() + 1, getCellCount(i) - getDataColumnCount() - 1);

        for (int i = headerRowCount; i < getDataRowCount() + headerRowCount ; i++)
            initRow(i);

        if (isGhostRow())
            initGhostRow();

        for (int row = 0; row < getHeaderRows(); row++)
        {
            for (int i = 0; i < getDataColumnCount(); i++)
            {
                Widget header = getColumnHeader(row, i);
                if (null != header)
                {
                    setWidget(row, i + 1, header);
                    getCellFormatter().setStyleName(row, i + 1, "labkey-col-header");
                }
            }
            getCellFormatter().setStyleName(row, 0, "labkey-col-header");
        }
    }

    public boolean isReadOnly()
    {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly)
    {
        this.readOnly = readOnly;
        setStyleName(readOnly ? "labkey-read-only labkey-data-region labkey-show-borders" : "labkey-data-region labkey-show-borders");
    }

    private void initRow(int row)
    {
        getCellFormatter().setStyleName(row, 0,  "labkey-row-header");
        setWidget(row, 0, getRowNumberWidget(row - getHeaderRows()));

        for (int col = 0; col < getDataColumnCount(); col++)
        {
            Widget widget;
            if (readOnly)
                widget = getReadOnlyWidget(row - getHeaderRows(), col);
            else
                widget =  getCellWidget(row - getHeaderRows(), col);

            getCellFormatter().setStyleName(row, col + 1, isReadOnly() ? "assay-row-padded-view" : "assay-row-padded");
            setWidget(row, col + 1, widget);
        }
    }

    private boolean isInGhostRow(Widget widget)
    {
        int ghostRowIndex = getDataRowCount() + getHeaderRows();
        for (int i = 0; i < getCellCount(ghostRowIndex); i++)
            if (widget == getWidget(ghostRowIndex, i))
                return true;

        return false;
    }

    private ChangeListener ghostChangeListener = new ChangeListener() {
        public void onChange(Widget sender) {
            if (isInGhostRow(sender))
                adjustGhostRow();
        }
    };

    private void initGhostRow()
    {
        int row = getRowCount() - 1;
        getCellFormatter().setStyleName(row, 0, "labkey-row-header");
        setWidget(row, 0, getGhostRowHeaderWidget());
        for (int col = 0; col < getDataColumnCount(); col++)
        {
            Widget widget =  getGhostRowWidget(col);
            if (widget instanceof SourcesChangeEvents)
                ((SourcesChangeEvents) widget).addChangeListener(ghostChangeListener);
            setWidget(row, col + 1, widget);
        }

    }

    public void adjustGhostRow()
    {
        int rows = getRowCount();
        makeGhostRowReal();
        setWidget(rows - 1, 0, getRowNumberWidget(rows - getHeaderRows() - 1));
        prepareRow(rows);
        initGhostRow();
    }

    public static final Widget EMPTY_LABEL = new Label("");
    public Widget getRowNumberWidget(final int row)
    {
        if (readOnly)
            return EMPTY_LABEL;

        final Label rowLabel = new Label(Integer.toString(row + 1));
        rowLabel.setTitle("Click to delete " + getRowNoun());

        return rowLabel;
    }

    public Widget getGhostRowHeaderWidget()
    {
        final Label rowLabel = new Label("+");
        rowLabel.setTitle("Use this row to add a new " + getRowNoun());

        return rowLabel;
    }

    public void showPopupMenu(PopupMenu popupMenu, int row, int col)
    {
        Widget w = getWidget(row, col);

        Element cell = getCellFormatter().getElement(row, col);
        int left = DOM.getAbsoluteLeft(cell);
        int top = w == null ? DOM.getAbsoluteTop(cell) + 15 : w.getAbsoluteTop() + w.getOffsetHeight();
        popupMenu.setPopupPosition(left, top);

        popupMenu.show();
    }

    public PopupMenu getRowPopupMenu(final int dataRow)
    {
        final PopupMenu popupMenu = new PopupMenu();
        popupMenu.addItem("Delete " + getRowNoun(), new Command() {
            public void execute()
            {
                deleteRow(dataRow);
                popupMenu.hide();
            }
        });

        return popupMenu;
    }

    public boolean isGhostRow()
    {
        return ghostRow && !isReadOnly();
    }

    public void setGhostRow(boolean ghostRow)
    {
        this.ghostRow = ghostRow;
    }

    public static class PopupMenu extends PopupPanel
    {
        MenuBar menuBar = new MenuBar(true);

        public PopupMenu()
        {
            super(true);
            addPopupListener(menuBar);
            add(menuBar);
        }

        public void addItem(String title, final Command command)
        {
            MenuItem item = new MenuItem(title, new Command() {
                public void execute()
                {
                    command.execute();
                    hide();
                }
            });
            menuBar.addItem(item);
        }
    }

    public Widget getReadOnlyWidget(int row, int col)
    {
        Object o = getCellValue(row, col);
        if (null == o)
            o = "";
        Label l = new Label(StringUtils.trimToEmpty(o.toString()));
        l.setWordWrap(false);
        return l;
    }
    
    public String getRowNoun()
    {
        return "row";
    }

    class RowHeaderClickListener implements TableListener
    {
        public void onCellClicked(SourcesTableEvents sender, int row, int cell)
        {
            if (cell == 0 && row >= getHeaderRows() && row < getDataRowCount() + getHeaderRows())
            {
                PopupMenu popup = EditableGrid.this.getRowPopupMenu(row - EditableGrid.this.getHeaderRows());
                if (null != popup)
                    showPopupMenu(popup, row, cell);
            }
        }
    }

    abstract int getDataColumnCount();
    abstract int getDataRowCount();
    abstract Widget getCellWidget(int row, int col);
    abstract Object getCellValue(int row, int col);
    abstract Widget getGhostRowWidget(int col);
    abstract int getHeaderRows();
    abstract Widget getColumnHeader(int row, int column);
    abstract void makeGhostRowReal();
    abstract void deleteRow(int dataRow);
}
