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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import gwt.client.org.labkey.plate.designer.client.model.GWTPosition;
import gwt.client.org.labkey.plate.designer.client.model.GWTWellGroup;
import org.labkey.api.gwt.client.ui.ImageButton;

/**
 * User: brittp
 * Date: Aug 9, 2010 5:56:35 PM
 */
public class ShiftPanel extends FlexTable
{
    private TemplateView _view;

    private class ShiftButton extends ImageButton
    {
        public ShiftButton(String text, final int vertical, final int horizontal)
        {
            super(text);
            addClickHandler(new ClickHandler()
            {
                public void onClick(ClickEvent event)
                {
                    shift(vertical, horizontal);
                }
            });
        }
    }

    public ShiftPanel(TemplateView view)
    {
        _view = view;
        addCenteredWidget(0, 1, new ShiftButton("Up", 1, 0));
        addCenteredWidget(2, 1, new ShiftButton("Down", -1, 0));
        addCenteredWidget(1, 2, new ShiftButton("Right", 0, -1));
        addCenteredWidget(1, 0, new ShiftButton("Left", 0, 1));
        Label shiftLabel = new Label("Shift");
        shiftLabel .setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        addCenteredWidget(1, 1,shiftLabel);
    }

    private void addCenteredWidget(int row, int column, Widget widget)
    {
        setWidget(row, column, widget);
        getFlexCellFormatter().setHorizontalAlignment(0, 1, HasHorizontalAlignment.ALIGN_CENTER);
    }

    private void shift(int verticalShift, int horizontalShift)
    {
        TemplateGrid grid = _view.getGrid();
        GWTWellGroup[][] snapshot = new GWTWellGroup[grid.getTemplateGridRowCount()][grid.getTemplateGridColumnCount()];
        for (int row = 0; row < snapshot.length; row++)
        {
            for (int col = 0; col < snapshot[row].length; col++)
            {
                snapshot[row][col] = grid.getTemplateGridCell(new GWTPosition(row, col)).getActiveGroup();
            }
        }

        for (int row = 0; row < snapshot.length; row++)
        {
            for (int col = 0; col < snapshot[row].length; col++)
            {
                int sourceRow = (row + verticalShift) % grid.getTemplateGridRowCount();
                if (sourceRow < 0)
                    sourceRow += grid.getTemplateGridRowCount();
                int sourceCol = (col + horizontalShift) % grid.getTemplateGridColumnCount();
                if (sourceCol < 0)
                    sourceCol += grid.getTemplateGridColumnCount();
                GWTWellGroup newGroup = snapshot[sourceRow][sourceCol];
                grid.getTemplateGridCell(new GWTPosition(row, col)).replaceActiveGroup(newGroup);
            }
        }
        _view.refreshWarningsAsync();
    }
}
