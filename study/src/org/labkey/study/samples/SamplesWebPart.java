package org.labkey.study.samples;

import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.data.CompareType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.study.model.SpecimenTypeSummary;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Jul 28, 2006
 * Time: 10:50:05 AM
 */
public class SamplesWebPart extends JspView<SamplesWebPart.SamplesWebPartBean>
{
    public SamplesWebPart()
    {
        this(false);
    }

    public SamplesWebPart(boolean wide)
    {
        super("/org/labkey/study/view/samples/webPart.jsp", new SamplesWebPartBean(wide));
        getModelBean().setViewContext(getViewContext());
        setTitle("Specimens");
    }

    public static class SamplesWebPartBean
    {
        private boolean _wide;
        private ViewContext _viewContext;

        public SamplesWebPartBean(boolean wide)
        {
            _wide = wide;
        }

        public void setViewContext(ViewContext viewContext)
        {
            _viewContext = viewContext;
        }

        public boolean isWide()
        {
            return _wide;
        }

        private String getTypeListInnerHtml(List<? extends SpecimenTypeSummary.TypeCount> types, ActionURL parentURL)
        {
            StringBuilder builder = new StringBuilder();
            builder.append("<table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\">");
            for (SpecimenTypeSummary.TypeCount count : types)
            {
                ActionURL url = parentURL.clone();
                List<? extends SpecimenTypeSummary.TypeCount> children = count.getChildren();



                builder.append("<tr class=\"header\"><td style=\"padding-top:5px\">");
                if (!children.isEmpty())
                {
                    builder.append("<a href=\"#\" onclick=\"return toggleLink(this, false);\">");
                    builder.append("<img border=\"0\" src=\"").append(_viewContext.getContextPath()).append("/_images/plus.gif\"></a>");
                }
                else
                {
                    builder.append("<img width=\"9\" border=\"0\" src=\"").append(_viewContext.getContextPath()).append("/_.gif\"/>");
                }
                builder.append("</td>");

                if (count.getLabel() != null && count.getLabel().length() > 0)
                {
                    url.addFilter("SpecimenDetail", FieldKey.fromParts(count.getSpecimenViewFilterColumn(),
                            "Description"), CompareType.EQUAL, count.getLabel());
                }
                else
                {
                    url.addFilter("SpecimenDetail", FieldKey.fromParts(count.getSpecimenViewFilterColumn(),
                            "Description"), CompareType.ISBLANK, null);
                }

                builder.append("<td style=\"width:100%;padding-top:3px;padding-left:3px\"><a href=\"").append(url).append("\">");
                if (count.getLabel() != null && count.getLabel().length() > 0)
                    builder.append(PageFlowUtil.filter(count.getLabel()));
                else
                    builder.append("[unknown]");
                builder.append("</a></td>\n\t<td align=\"right\" style=\"padding-top:3px\">");
                builder.append(count.getVialCount());
                builder.append("</td></tr>\n");
                if (!children.isEmpty())
                {
                    builder.append("<tr style=\"display:none\"><td></td><td colspan=\"2\">\n");
                    builder.append(getTypeListInnerHtml(children, url));
                    builder.append("</td></tr>\n");
                }
            }
            builder.append("</table>\n");
            return builder.toString();
        }

        private String getTypeListHtml(List<? extends SpecimenTypeSummary.TypeCount> types)
        {
            ActionURL baseURL = new ActionURL(SpringSpecimenController.SamplesAction.class, _viewContext.getContainer());
            baseURL.addParameter(SpringSpecimenController.SampleViewTypeForm.PARAMS.showVials, Boolean.TRUE.toString());
            return getTypeListInnerHtml(types, baseURL);

        }
        public String getPrimaryTypeListHtml()
        {
            SpecimenTypeSummary summary = SampleManager.getInstance().getSpecimenTypeSummary(_viewContext.getContainer());
            return getTypeListHtml(summary.getPrimaryTypes());
        }

        public String getDerivativeTypeListHtml()
        {
            SpecimenTypeSummary summary = SampleManager.getInstance().getSpecimenTypeSummary(_viewContext.getContainer());
            return getTypeListHtml(summary.getDerivatives());
        }

    }
}
