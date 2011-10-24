package org.labkey.study.writer;

import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.viewCategory.CategoriesDocument;
import org.labkey.study.xml.viewCategory.CategoryType;
import org.labkey.study.xml.viewCategory.ViewCategoryType;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Oct 21, 2011
 * Time: 2:48:09 PM
 */
public class ViewCategoryWriter implements InternalStudyWriter
{
    public static final String FILE_NAME = "view_categories.xml";
    public static final String DATA_TYPE = "Categories";

    @Override
    public String getSelectionText()
    {
        return DATA_TYPE;
    }

    @Override
    public void write(StudyImpl object, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        ViewCategory[] categories = ViewCategoryManager.getInstance().getCategories(ctx.getContainer(), ctx.getUser());

        if (categories.length > 0)
        {
            CategoriesDocument doc = CategoriesDocument.Factory.newInstance();
            ViewCategoryType categoryType = doc.addNewCategories();

            for (ViewCategory category : categories)
            {
                CategoryType ct = categoryType.addNewCategory();

                ct.setLabel(category.getLabel());
                ct.setDisplayOrder(category.getDisplayOrder());
            }
            vf.saveXmlBean(FILE_NAME, doc);
        }
    }
}
