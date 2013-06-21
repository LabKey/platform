/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
import org.labkey.study.model.StudyImpl;

/**
 * User: Mark Igra
 * Date: Jul 28, 2006
 * Time: 10:50:05 AM
 */
public class SamplesWebPart extends JspView<SamplesWebPart.SamplesWebPartBean>
{
    public SamplesWebPart()
    {
        this(false, null);
    }

    public SamplesWebPart(boolean wide, StudyImpl study)
    {
        super("/org/labkey/study/view/samples/webPart.jsp", new SamplesWebPartBean(wide,
                (null != study) ? study.getRepositorySettings().isSpecimenDataEditable() : false));
        getModelBean().setViewContext(getViewContext());
        setTitle("Specimens");
    }

    public static class SamplesWebPartBean
    {
        private boolean _wide;
        private ViewContext _viewContext;
        private boolean _isEditableSpecimens;

        public SamplesWebPartBean(boolean wide, boolean isEditableSpecimens)
        {
            _wide = wide;
            _isEditableSpecimens = isEditableSpecimens;
        }

        public void setViewContext(ViewContext viewContext)
        {
            _viewContext = viewContext;
        }

        public boolean isWide()
        {
            return _wide;
        }

        public boolean isEditableSpecimens()
        {
            return _isEditableSpecimens;
        }

        public void setEditableSpecimens(boolean editableSpecimens)
        {
            _isEditableSpecimens = editableSpecimens;
        }
    }
}
