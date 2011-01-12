package org.labkey.study.view;

import org.labkey.api.study.StudyService;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;

/**
 * Copyright (c) 2010 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * <p/>
 * User: brittp
 * Date: Oct 21, 2010 1:39:16 PM
 */
public class SubjectsWebPart extends JspView<SubjectsWebPart.SubjectsBean>
{
    public static class SubjectsBean
    {
        private ViewContext _viewContext;
        private int _rows = 5;
        private int _cols = 5;

        public ViewContext getViewContext()
        {
            return _viewContext;
        }

        public void setViewContext(ViewContext viewContext)
        {
            _viewContext = viewContext;
        }

        public int getRows()
        {
            return _rows;
        }

        public void setRows(int rows)
        {
            _rows = rows;
        }

        public int getCols()
        {
            return _cols;
        }

        public void setCols(int cols)
        {
            _cols = cols;
        }
    }

    public SubjectsWebPart(boolean wide)
    {
        super("/org/labkey/study/view/subjects.jsp", new SubjectsWebPart.SubjectsBean());
        getModelBean().setViewContext(getViewContext());
        getModelBean().setRows(wide ? 5 : 10);
        getModelBean().setCols(wide ? 6 : 2);
        String title = StudyService.get().getSubjectNounPlural(getViewContext().getContainer());
        setTitle(title);
    }
}
