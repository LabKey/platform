/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
            builder.append("<table class=\"labkey-study-expandable-nav\">");
            for (SpecimenTypeSummary.TypeCount count : types)
            {
                ActionURL url = parentURL.clone();
                List<? extends SpecimenTypeSummary.TypeCount> children = count.getChildren();

                builder.append("<tr class=\"labkey-header\"><td class=\"labkey-nav-tree-node\">");
                if (!children.isEmpty())
                {
                    builder.append("<a href=\"#\" onclick=\"return toggleLink(this, false);\">");
                    builder.append("<img src=\"").append(_viewContext.getContextPath()).append("/_images/plus.gif\"></a>");
                }
                else
                {
                    builder.append("<img width=\"9\" src=\"").append(_viewContext.getContextPath()).append("/_.gif\"/>");
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

                builder.append("<td class=\"labkey-nav-tree-text\" width=\"100%\"><a href=\"").append(url.getEncodedLocalURIString()).append("\">");
                if (count.getLabel() != null && count.getLabel().length() > 0)
                    builder.append(PageFlowUtil.filter(count.getLabel()));
                else
                    builder.append("[unknown]");
                builder.append("</a></td>\n\t<td align=\"right\" class=\"labkey-nav-tree-total\">");
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
