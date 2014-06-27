/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

package org.labkey.study.specimen;

import org.labkey.api.view.JspView;
import org.labkey.study.model.StudyImpl;

/**
 * User: Mark Igra
 * Date: Jul 28, 2006
 * Time: 10:50:05 AM
 */
public class SpecimenWebPart extends JspView<SpecimenWebPart.SpecimenWebPartBean>
{
    public SpecimenWebPart()
    {
        this(false, null);
    }

    public SpecimenWebPart(boolean wide, StudyImpl study)
    {
        super("/org/labkey/study/view/specimen/webPart.jsp", new SpecimenWebPartBean(wide,
                (null != study) && study.getRepositorySettings().isSpecimenDataEditable()));
        setTitle("Specimens");
    }

    public static class SpecimenWebPartBean
    {
        private final boolean _wide;
        private final boolean _isEditableSpecimens;

        public SpecimenWebPartBean(boolean wide, boolean isEditableSpecimens)
        {
            _wide = wide;
            _isEditableSpecimens = isEditableSpecimens;
        }

        public boolean isWide()
        {
            return _wide;
        }

        public boolean isEditableSpecimens()
        {
            return _isEditableSpecimens;
        }
    }
}
