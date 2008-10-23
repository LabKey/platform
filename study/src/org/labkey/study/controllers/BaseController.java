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

package org.labkey.study.controllers;

import org.apache.beehive.netui.pageflow.FormData;
import org.apache.beehive.netui.pageflow.Forward;
import org.labkey.api.data.Container;
import org.labkey.api.jsp.JspBase;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.view.*;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;

import javax.servlet.ServletException;
import java.util.List;


/**
 * User: brittp
 * Date: Feb 2, 2006
 * Time: 1:31:04 PM
 */
public class BaseController extends ViewController
{

    protected int[] toIntArray(List<String> intStrings)
    {
        if (intStrings == null)
            return null;
        int[] converted = new int[intStrings.size()];
        int index = 0;
        for (String intString : intStrings)
            converted[index++] = Integer.parseInt(intString);
        return converted;
    }

    protected int[] toIntArray(String[] intStrings)
    {
        if (intStrings == null)
            return null;
        int[] converted = new int[intStrings.length];
        int index = 0;
        for (String intString : intStrings)
            converted[index++] = Integer.parseInt(intString);
        return converted;
    }

    public Study getStudy() throws ServletException
    {
        return getStudy(false);
    }

    public Study getStudy(boolean allowNullStudy) throws ServletException
    {
        // UNDONE: see https://cpas.fhcrc.org/Issues/home/issues/details.view?issueId=1137
        Container c = getContainer();
        Study study = StudyManager.getInstance().getStudy(c);
        if (!allowNullStudy && study == null)
        {
            // redirect to the study home page, where admins will see a 'create study' button,
            // and non-admins will simply see a message that no study exists.
            HttpView.throwRedirect(new ActionURL(StudyController.BeginAction.class, c));
        }
        return study;
    }

    protected Forward _renderInTemplate(JspBase page, String title, NavTree... navtrail) throws Exception
    {
        return _renderInTemplate(new JspView(page), title, navtrail);
    }

    protected Forward _renderInTemplate(JspBase page, String title, String helpTopic, NavTree... navtrail) throws Exception
    {
        return _renderInTemplate(new JspView(page), title, helpTopic, navtrail);
    }

    public Forward _renderInTemplate(HttpView view, String title, NavTree... navtrail) throws Exception
    {
        return _renderInTemplate(view, title, null, navtrail);
    }

    public Forward _renderInTemplate(HttpView view, String title, String helpTopic, NavTree... navtrail) throws Exception
    {
        NavTrailConfig trailConfig = new NavTrailConfig(getViewContext());
        if (title != null)
            trailConfig.setTitle(title);
        if (helpTopic != null)
            trailConfig.setHelpTopic(new HelpTopic(helpTopic, HelpTopic.Area.STUDY));
        trailConfig.setExtraChildren(navtrail);
        return includeView(new HomeTemplate(getViewContext(), getContainer(), view, trailConfig));
    }

    public static <T> boolean nullSafeEqual(T first, T second)
    {
        return BaseStudyController.nullSafeEqual(first, second);
    }

    public static class IdForm extends ViewForm
    {
        private int _id;

        public IdForm()
        {
        }

        public IdForm(int id)
        {
            _id = id;
        }

        public int getId()
        {
            return _id;
        }

        public void setId(int id)
        {
            _id = id;
        }
    }

    
    public static class TSVForm extends FormData
    {
        private String _content;

        public String getContent()
        {
            return _content;
        }

        public void setContent(String content)
        {
            _content = content;
        }
    }

    public static class BulkEditForm extends FormData
    {
        private String _newLabel;
        private String _newId;
        private String _nextPage;
        private String _order;
        private int[] _ids;
        private String[] _labels;

        public String getNewLabel()
        {
            return _newLabel;
        }

        public void setNewLabel(String newLabel)
        {
            _newLabel = newLabel;
        }

        public String getNextPage()
        {
            return _nextPage;
        }

        public void setNextPage(String nextPage)
        {
            _nextPage = nextPage;
        }

        public String getOrder()
        {
            return _order;
        }

        public void setOrder(String order)
        {
            _order = order;
        }

        public String[] getLabels()
        {
            return _labels;
        }

        public void setLabels(String[] labels)
        {
            _labels = labels;
        }

        public int[] getIds()
        {
            return _ids;
        }

        public void setIds(int[] ids)
        {
            _ids = ids;
        }

        public String getNewId()
        {
            return _newId;
        }

        public void setNewId(String newId)
        {
            _newId = newId;
        }
    }

    public static class PipelineForm extends FormData
    {
        private String _path;
        private boolean _deleteLogfile;

        public PipelineForm()
        {
//            System.err.println("PipelineForm");
        }

        public String getPath()
        {
            return _path;
        }

        public void setPath(String path)
        {
            this._path = path;
        }

        public boolean isDeleteLogfile()
        {
            return _deleteLogfile;
        }

        public void setDeleteLogfile(boolean deleteLogfile)
        {
            _deleteLogfile = deleteLogfile;
        }
    }
}
