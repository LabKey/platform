/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
package org.labkey.study.writer;

import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.viewCategory.CategoriesDocument;
import org.labkey.study.xml.viewCategory.CategoryType;
import org.labkey.study.xml.viewCategory.ViewCategoryType;

import java.util.List;

/**
 * User: klum
 * Date: Oct 21, 2011
 * Time: 2:48:09 PM
 */
public class ViewCategoryWriter implements InternalStudyWriter
{
    public static final String FILE_NAME = "view_categories.xml";

    @Override
    public String getDataType()
    {
        return StudyArchiveDataTypes.VIEW_CATEGORIES;
    }

    @Override
    public void write(StudyImpl object, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        List<ViewCategory> categories = ViewCategoryManager.getInstance().getAllCategories(ctx.getContainer());

        if (!categories.isEmpty())
        {
            CategoriesDocument doc = CategoriesDocument.Factory.newInstance();
            ViewCategoryType categoryType = doc.addNewCategories();

            for (ViewCategory category : categories)
            {
                CategoryType ct = categoryType.addNewCategory();

                ct.setLabel(category.getLabel());
                ct.setDisplayOrder(category.getDisplayOrder());

                if (category.getParentCategory() != null)
                    ct.setParent(category.getParentCategory().getLabel());
            }
            vf.saveXmlBean(FILE_NAME, doc);
        }
    }
}
