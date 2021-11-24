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

package org.labkey.api.study.publish;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:09:28 PM
*/
@RequiresPermission(InsertPermission.class)
public abstract class AbstractPublishStartAction<FORM extends PublishStartForm> extends SimpleViewAction<FORM>
{
    /**
     * Issue : 44085, the max number of data rows to link before an error is shown
     */
    public static final int MAX_ROWS_TO_LINK = 9950;

    /**
     * Returns the ActionURL to post to on success
     */
    protected abstract ActionURL getSuccessUrl(FORM form);

    /**
     * Returns the data row IDs that will be candidates for linking to the target study.
     */
    protected abstract List<Integer> getDataIDs(FORM form);

    /**
     * Return the set of Study containers that are implicitly associated with this form. For assays, this
     * would be the target assay run property and can be used to short circuit the target study selection
     * when the choice is unambiguous.
     */
    protected Set<Container> getAssociatedStudyContainers(FORM form)
    {
        return Collections.emptySet();
    }

    /**
     * If an alternate to individual result rows are being provided this would be the ID of the batch
     * definition. For assays, this could be a run and for samples could be the sample type.
     */
    protected abstract List<Integer> getBatchIds();
    protected abstract String getBatchNoun();

    @Override
    public ModelAndView getView(FORM form, BindException errors)
    {
        boolean nullsFound = false;
        boolean insufficientPermissions = false;

        Set<Container> containers = getAssociatedStudyContainers(form);
        Iterator<Container> i = containers.iterator();
        while (i.hasNext())
        {
            Container c = i.next();
            if (c == null)
            {
                nullsFound = true;
                i.remove();
            }
            else if (!c.hasPermission(getUser(), InsertPermission.class))
            {
                insufficientPermissions = true;
                i.remove();
            }
        }

        return new JspView<>("/org/labkey/api/study/publish/publishChooseStudy.jsp",
                new PublishBean(getSuccessUrl(form),
                    getDataIDs(form),
                    form.getDataRegionSelectionKey(),
                    containers,
                    nullsFound,
                    insufficientPermissions,
                    form.getReturnActionURL(),
                    form.getContainerFilterName(),
                    getBatchIds(),
                    getBatchNoun(),
                    form.isAutoLinkEnabled()));
    }

    public static List<Integer> getCheckboxIds(ViewContext context)
    {
        Set<String> idStrings = DataRegionSelection.getSelected(context, null, false);

        DataRegionSelection.clearAll(context, null);
        DataRegionSelection.setSelected(context, null, idStrings, true);

        List<Integer> ids = new ArrayList<>();
        for (String rowIdStr : idStrings)
        {
            try
            {
                ids.add(Integer.parseInt(rowIdStr));
            }
            catch (NumberFormatException e)
            {
                throw new NotFoundException("Unable to parse selected RowId value: " + rowIdStr);
            }
        }
        return ids;
    }
}
