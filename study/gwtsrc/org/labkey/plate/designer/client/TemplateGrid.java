/*
 * Copyright (c) 2007 LabKey Corporation
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

package org.labkey.plate.designer.client;

import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.DOM;

import java.util.Set;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

import org.labkey.plate.designer.client.model.GWTPosition;
import org.labkey.plate.designer.client.model.GWTWellGroup;

/**
 * User: brittp
 * Date: Feb 6, 2007
 * Time: 3:58:52 PM
 */
public class TemplateGrid extends Grid
{
    public static final char[] ALPHABET = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
            'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};
    private TemplateView _view;

    public TemplateGrid(TemplateView view, String activeType)
    {
        super(view.getPlate().getRows() + 1, view.getPlate().getCols() + 1);
        _view = view;
        setBorderWidth(0);
        setCellPadding(0);
        setCellSpacing(3);
        for (int row = 0; row < getRowCount(); row++)
        {
            Widget cellWidget = null;
            for (int col = 0; col < getColumnCount(); col++)
            {
                if (row == 0 && col > 0)
                    cellWidget = new Label("" + col);
                else if (col == 0 && row > 0)
                    cellWidget = new Label("" + ALPHABET[row - 1]);
                else if (col > 0 && row > 0)
                {
                    GWTPosition position = new GWTPosition(row - 1, col - 1);
                    Set groups = (Set) view.getPlate().getPositionToGroupsMap().get(position);
                    cellWidget = new TemplateGridCell(_view, position, groups, activeType);
                }
                if (cellWidget != null)
                {
                    cellWidget.setSize("35px", "25px");
                    setWidget(row, col, cellWidget);
                    DOM.setStyleAttribute(cellWidget.getElement(), "textAlign", "center");
                }
            }
        }
    }

    public void highlightGroup(GWTWellGroup group, boolean on)
    {
        for (Iterator it = group.getPositions().iterator(); it.hasNext(); )
        {
            GWTPosition position = (GWTPosition) it.next();
            getTemplateGridCell(position).setHighlight(on);
        }
    }

    public TemplateGridCell getTemplateGridCell(GWTPosition position)
    {
        return (TemplateGridCell) getWidget(position.getRow() + 1, position.getCol() + 1);
    }

    public List getAllCells()
    {
        List cells = new ArrayList();
        for (int row = 0; row < getRowCount(); row++)
        {
            for (int col = 0; col < getColumnCount(); col++)
            {
                if (col > 0 && row > 0)
                    cells.add(getWidget(row, col));
            }
        }
        return cells;
    }
}
