package org.labkey.experiment.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.IdentifiableBase;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.api.ExpLineage;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.util.DOM;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import static org.labkey.api.util.DOM.A;
import static org.labkey.api.util.DOM.Attribute.href;
import static org.labkey.api.util.DOM.LI;
import static org.labkey.api.util.DOM.UL;
import static org.labkey.api.util.DOM.at;

public class LineageTreeDisplayColumn extends DataColumn
{
    private final boolean _parents;

    public LineageTreeDisplayColumn(ColumnInfo col, boolean parents)
    {
        super(col);
        _parents = parents;
        setTextAlign("left");
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object o = getValue(ctx);
        if (o instanceof Integer)
        {
            Integer objectId = (Integer)o;
            OntologyObject oo = OntologyManager.getOntologyObject(objectId);
            if (oo != null)
            {
                IdentifiableBase identifiable = new IdentifiableBase(oo);

                ExpLineageOptions options = new ExpLineageOptions();
                options.setForLookup(false);
                options.setParents(_parents);
                options.setChildren(!_parents);
                //options.setCpasType(_cpasType);
                //options.setExpType(_expType);
                //if (_depth != null)
                //    options.setDepth(_depth);

                options.setUseObjectIds(true);

                ExpLineage lineage = ExperimentService.get().getLineage(ctx.getContainer(), ctx.getViewContext().getUser(), identifiable, options);
                if (_parents)
                    renderInvertedLineageTree(ctx, out, lineage, identifiable);
                else
                    renderLineageTree(ctx, out, lineage, identifiable);
            }
        }
    }

    public void renderInvertedLineageTree(RenderContext ctx, Writer out, ExpLineage lineage, Identifiable curr) throws IOException
    {
    }

    public void renderLineageTree(RenderContext ctx, Writer out, ExpLineage lineage, Identifiable curr) throws IOException
    {
        DOM.Renderable tree = renderLineageTree(ctx, lineage, curr);
        tree.appendTo(out);
    }

    protected DOM.Renderable renderLineageTree(RenderContext ctx, ExpLineage lineage, Identifiable curr)
    {
        Set<Identifiable> children = lineage.getNodeChildren(curr);
        if (children.isEmpty())
            return null;

        return UL(children.stream().map(node -> LI(
                        A(at(href, node.detailsURL()), node.getName()),
                        renderLineageTree(ctx, lineage, node))));

//        out.write("<ul>");
//        for (Identifiable node : children)
//        {
//            out.write("<li>");
//
//            ActionURL detailsURL = node.detailsURL();
//            String name = node.getName();
//
//            StringBuilder sb = new StringBuilder();
//            A(at(href, detailsURL), name).appendTo(sb);
//            out.write(sb.toString());
//
//            renderLineageTree(ctx, out, lineage, node);
//        }
//        out.write("</ul>");
    }

}
