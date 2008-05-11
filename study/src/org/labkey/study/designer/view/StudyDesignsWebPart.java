package org.labkey.study.designer.view;

import org.labkey.api.view.GridView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.*;
import org.labkey.api.security.ACL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.study.designer.StudyDesignManager;
import org.labkey.study.controllers.designer.DesignerController;

import java.io.IOException;
import java.io.Writer;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Feb 15, 2007
 * Time: 4:45:19 PM
 */
public class StudyDesignsWebPart extends GridView
{
    public StudyDesignsWebPart(ViewContext ctx, boolean inPortal)
    {
        super(new DataRegion());
        
        TableInfo table = StudyDesignManager.get().getStudyDesignTable();
        DataRegion dr = getDataRegion();
        dr.setSelectionKey(DataRegionSelection.getSelectionKey(table.getSchema().getName(), table.getName(), null, "StudyDesigns"));
        dr.addColumns(table.getColumns("StudyId", "Label", "Modified", "PublicRevision","Container", "Active"));
        dr.getDisplayColumn("StudyId").setCaption("Id");

        ActionURL helper = new ActionURL(DesignerController.DesignerAction.class, ctx.getContainer());
        dr.getDisplayColumn("Label").setURL(helper.toString() + "&studyId=${studyId}");
        dr.getDisplayColumn("PublicRevision").setCaption("Revision");
        dr.getDisplayColumn("Container").setVisible(false);
        dr.getDisplayColumn("Active").setVisible(false);
        final ActionURL studyFolderUrl = PageFlowUtil.urlProvider(ProjectUrls.class).urlStart(ctx.getContainer());
        dr.addDisplayColumn(new DataColumn(table.getColumn("Active")) {
            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                boolean value = ((Boolean)getValue(ctx)).booleanValue();
                if (value)
                {
                    Container c = ContainerManager.getForId((String) ctx.get("Container"));
                    studyFolderUrl.setExtraPath(c.getPath());
                    out.write("[<a style='white-space:nowrap' href='");
                    out.write(PageFlowUtil.filter(studyFolderUrl));
                    out.write("'>Go To Study Folder</a>]");
                }
                else
                {
                    out.write("&nbsp;");
                }
            }

            @Override
            public String getCaption()
            {
                return "";
            }
        });
        ButtonBar bb = new ButtonBar();
        if (ctx.getContainer().hasPermission(ctx.getUser(), ACL.PERM_INSERT))
        {
            helper.addParameter("edit", "true");
            bb.add(new ActionButton("New Protocol", helper));
        }
        if (inPortal)
        {
            if (ctx.getContainer().hasPermission(ctx.getUser(), ACL.PERM_UPDATE))
            {
                ActionURL adminURL = new ActionURL(DesignerController.BeginAction.class, ctx.getContainer());
                bb.add(new ActionButton("Manage Protocols", adminURL));
            }
        }
        else
        {
            if (ctx.getContainer().hasPermission(ctx.getUser(), ACL.PERM_DELETE))
            {
                dr.setShowRecordSelectors(true);
                bb.add(ActionButton.BUTTON_DELETE);
            }
            else
                dr.setShowRecordSelectors(false);
            
            if (ctx.getContainer().getProject().hasPermission(ctx.getUser(), ACL.PERM_ADMIN))
            {
                ActionURL templateHelper = helper.clone();
                templateHelper.setAction(DesignerController.EditTemplateAction.class);
                bb.add(new ActionButton("Edit Template for Project", templateHelper));
            }
        }

        dr.setButtonBar(bb);
        setTitle("Vaccine Study Protocols");
        setSort(new Sort("Label"));
        getRenderContext().setUseContainerFilter(false);
        setFilter(new SimpleFilter("SourceContainer", ctx.getContainer()));
    }
}
