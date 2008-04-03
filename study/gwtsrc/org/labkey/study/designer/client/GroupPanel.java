package org.labkey.study.designer.client;

import com.google.gwt.user.client.ui.*;

import org.labkey.study.designer.client.model.GWTStudyDefinition;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Dec 15, 2006
 * Time: 3:23:17 PM
 * To change this template use File | Settings | File Templates.
 */
public class GroupPanel extends VerticalPanel
{
    public GroupGrid gridModel;

    public GroupPanel(GWTStudyDefinition studyDefinition)
    {
        setHorizontalAlignment(DockPanel.ALIGN_LEFT);

        add(new DescriptionWidget());
        add(new HTML("Enter one row for each group of participants in the protocol."));

        EditableGrid groupGrid = new GroupGrid(studyDefinition);
        groupGrid.updateAll();
        add(groupGrid);
    }
}
