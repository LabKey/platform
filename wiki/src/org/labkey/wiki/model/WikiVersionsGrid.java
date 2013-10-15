package org.labkey.wiki.model;

import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.view.GridView;
import org.labkey.wiki.WikiController;
import org.springframework.validation.Errors;

/**
 * User: kevink
 * Date: 10/14/13
 */
public class WikiVersionsGrid extends GridView
{
    public WikiVersionsGrid(Wiki wiki, WikiVersion wikiVersion, Errors errors)
    {
        super(new HighlightCurrentVersionDataRegion(), errors);

        HighlightCurrentVersionDataRegion dr = (HighlightCurrentVersionDataRegion)getDataRegion();
        dr.setVersion(wikiVersion.getVersion());

        TableInfo tinfoVersions = CommSchema.getInstance().getTableInfoPageVersions();
        TableInfo tinfoPages = CommSchema.getInstance().getTableInfoPages();

        // look up page name
        LookupColumn entityIdLookup = new LookupColumn(tinfoVersions.getColumn("PageEntityId"),
                tinfoPages.getColumn("EntityId"), tinfoPages.getColumn("Name"));
        entityIdLookup.setLabel("Page Name");

        //look up container (for filter)
        LookupColumn containerLookup = new LookupColumn(tinfoVersions.getColumn("PageEntityId"),
                tinfoPages.getColumn("EntityId"), tinfoPages.getColumn("Container"));
        DataColumn containerData = new DataColumn(containerLookup);
        containerData.setVisible(false);

        //version url
        DataColumn versionData = new DataColumn(tinfoVersions.getColumn("Version"));
        dr.addDisplayColumn(versionData);

        dr.addDisplayColumn(new DataColumn(tinfoVersions.getColumn("Title")));
        dr.addColumn(entityIdLookup);
        dr.addDisplayColumn(containerData);

        ColumnInfo colCreatedBy = tinfoVersions.getColumn("CreatedBy");
        // Set a custom renderer for the CreatedBy column
        DisplayColumn dc = new WikiController.DisplayColumnCreatedBy(colCreatedBy);
        dr.addDisplayColumn(dc);

        dr.addDisplayColumn(new DataColumn(tinfoVersions.getColumn("Created")));

        ButtonBar buttonBar = new ButtonBar();
        dr.setButtonBar(buttonBar);

        //filter on container and page name
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(containerLookup.getName(), wiki.getContainerId());
        filter.addCondition(entityIdLookup.getName(), wiki.getName());
        setFilter(filter);

        //sort DESC on version number
        Sort sort = new Sort("-version");
        setSort(sort);
    }


    private static class HighlightCurrentVersionDataRegion extends DataRegion
    {
        int version = -1;

        public void setVersion(int version)
        {
            this.version = version;
        }

        @Override
        protected String getRowClass(RenderContext ctx, int rowIndex)
        {
            if (((Integer)ctx.get("Version")).intValue() == version)
                return "labkey-alternate-row";
            else
                return "labkey-row";
        }
    }
}
