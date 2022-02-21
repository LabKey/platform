/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.specimen.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.specimen.SpecimenQuerySchema;
import org.labkey.api.specimen.SpecimenRequestStatus;
import org.labkey.api.specimen.query.BaseSpecimenQueryView;
import org.labkey.api.specimen.security.permissions.RequestSpecimensPermission;
import org.labkey.api.specimen.settings.SettingsManager;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUtils;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.specimen.SpecimenRequestManager;
import org.labkey.specimen.actions.SpecimenController.DeleteRequestAction;
import org.labkey.specimen.actions.SpecimenController.ManageRequestAction;
import org.labkey.specimen.actions.SpecimenController.SubmitRequestAction;
import org.labkey.specimen.security.permissions.ManageRequestsPermission;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * User: brittp
 * Date: Apr 20, 2007
 * Time: 2:49:42 PM
 */
public class SpecimenRequestQueryView extends BaseSpecimenQueryView
{
    private NavTree[] _extraLinks;
    private boolean _allowSortAndFilter = true;
    private boolean _showOptionLinks = true;

    private static class RequestOptionDisplayColumn extends SimpleDisplayColumn
    {
        private final ColumnInfo _colCreatedBy;
        private final ColumnInfo _colStatus;
        private final boolean _showOptionLinks;
        private final NavTree[] _extraLinks;
        private final boolean _userIsSpecimenManager;
        private final boolean _userCanRequest;
        private final int _userId;
        private final boolean _cartEnabled;

        private Integer _shoppingCartStatusRowId;

        public RequestOptionDisplayColumn(ViewContext context, boolean showOptionLinks, ColumnInfo colCreatedBy, ColumnInfo colStatus, NavTree... extraLinks)
        {
            _colStatus = colStatus;
            _colCreatedBy = colCreatedBy;
            User user = context.getUser();
            _userId = user.getUserId();
            _userIsSpecimenManager = context.getContainer().hasPermission(user, ManageRequestsPermission.class);
            _userCanRequest = context.getContainer().hasPermission(user, RequestSpecimensPermission.class);
            _showOptionLinks = showOptionLinks;
            _extraLinks = extraLinks;
            setTextAlign("right");
            setNoWrap(true);
            setWidth("175em");
            _cartEnabled = SettingsManager.get().isSpecimenShoppingCartEnabled(context.getContainer());
        }

        @Override
        public void addQueryColumns(Set<ColumnInfo> set)
        {
            set.add(_colCreatedBy);
            set.add(_colStatus);
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            StringBuilder content = new StringBuilder();
            // Use a div to set spacing around the buttons- otherwise they overlap the edges of their data grid cells:
            content.append("<div style=\"padding: 0.4em\">");
            if (_extraLinks != null)
            {
                for (NavTree link : _extraLinks)
                    content.append(PageFlowUtil.button(link.getText()).href(link.getHref())).append(" ");
            }

            if (_showOptionLinks)
            {
                if (_cartEnabled)
                {
                    if (_shoppingCartStatusRowId == null)
                    {
                        SpecimenRequestStatus cartStatus = SpecimenRequestManager.get().getRequestShoppingCartStatus(ctx.getContainer(),
                                ctx.getViewContext().getUser());
                        _shoppingCartStatusRowId = cartStatus.getRowId();
                    }

                    Integer statusId = (Integer) _colStatus.getValue(ctx);
                    Integer ownerId = (Integer) _colCreatedBy.getValue(ctx);
                    boolean canEdit = _userIsSpecimenManager || (_userCanRequest && ownerId == _userId);

                    if (statusId.intValue() == _shoppingCartStatusRowId.intValue() && canEdit)
                    {
                        ActionURL submitUrl = new ActionURL(SubmitRequestAction.class, ctx.getContainer()).addParameter("id", "${requestId}");;
                        ActionURL cancelUrl = new ActionURL(DeleteRequestAction.class, ctx.getContainer()).addParameter("id", "${requestId}");;

                        content.append(PageFlowUtil.button("Submit").href(submitUrl).usePost(StudyUtils.SUBMISSION_WARNING));
                        content.append(PageFlowUtil.button("Cancel").href(cancelUrl).usePost(StudyUtils.CANCELLATION_WARNING));
                    }
                }

                ActionURL detailsUrl = new ActionURL(ManageRequestAction.class, ctx.getContainer()).addParameter("id", "${requestId}");

                content.append(PageFlowUtil.button("Details").href(detailsUrl));
            }
            content.append("</div>");
            setDisplayHtml(content.toString());
            super.renderGridCellContents(ctx, out);
        }
    }

    protected SpecimenRequestQueryView(UserSchema schema, QuerySettings settings, SimpleFilter filter, Sort sort, NavTree... extraLinks)
    {
        super(schema, settings, filter, sort);
        _extraLinks = extraLinks;
    }

    public static SpecimenRequestQueryView createView(ViewContext context)
    {
        return createView(context, null);
    }

    public static SpecimenRequestQueryView createView(ViewContext context, @Nullable SimpleFilter filter)
    {
        Study study = StudyService.get().getStudy(context.getContainer());
        UserSchema schema = SpecimenQuerySchema.get(study, context.getUser());
        String queryName = "SpecimenRequest";
        QuerySettings qs = schema.getSettings(context, queryName, queryName);

        return new SpecimenRequestQueryView(schema, qs, addFilterClauses(filter), createDefaultSort());
    }
    
    private static Sort createDefaultSort()
    {
        return new Sort("-Created");
    }

    private static SimpleFilter addFilterClauses(@Nullable SimpleFilter filter)
    {
        if (filter == null)
            filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("Hidden"), Boolean.FALSE);
        return filter;
    }

    @Override
    protected DataRegion.ButtonBarPosition getButtonBarPosition()
    {
        return DataRegion.ButtonBarPosition.TOP;
    }

    @Override
    protected DataRegion createDataRegion()
    {
        DataRegion rgn = super.createDataRegion();
        DataColumn commentsDC = (DataColumn) rgn.getDisplayColumn("Comments");
        if (commentsDC != null)
        {
            commentsDC.setWidth("300px");
            commentsDC.setPreserveNewlines(true);
        }

        if (_showOptionLinks || (_extraLinks != null && _extraLinks.length > 0))
        {
            RequestOptionDisplayColumn optionCol =
                    new RequestOptionDisplayColumn(getViewContext(), _showOptionLinks, getTable().getColumn("CreatedBy"),
                            getTable().getColumn("Status"), _extraLinks);
            rgn.addDisplayColumn(0, optionCol);
        }
        rgn.setSortable(_allowSortAndFilter);
        rgn.setShowFilters(_allowSortAndFilter);
        return rgn;
    }

    public void setExtraLinks(boolean showOptionLinks, NavTree... extraLinks)
    {
        _showOptionLinks = showOptionLinks;
        _extraLinks = extraLinks;
    }

    public void setAllowSortAndFilter(boolean allowSortAndFilter)
    {
        _allowSortAndFilter = allowSortAndFilter;
    }

    public void setShowCustomizeLink(boolean showCustomizeLink)
    {
        getSettings().setAllowCustomizeView(showCustomizeLink);
    }
}
