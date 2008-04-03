package org.labkey.api.action;

import org.apache.beehive.netui.pageflow.FormData;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletRequest;

/**
 * User: kevink
 * Date: Jan 10, 2008 12:46:47 PM
 */
public class SharedActions
{

    @RequiresPermission(ACL.PERM_READ)
    public static class SelectNoneAction extends ApiAction<SelectNoneForm>
    {
        public SelectNoneAction()
        {
            super(SelectNoneForm.class);
        }

        public ApiResponse execute(final SelectNoneForm form, BindException errors) throws Exception
        {
            DataRegionSelection.clearAll(getViewContext(), form.getKey());
            return new SelectionResponse(0);
        }
    }

    public static class SelectNoneForm extends FormData
    {
        protected String key;

        public String getKey()
        {
            return key;
        }

        public void setKey(String key)
        {
            this.key = key;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public static class SetCheckAction extends ApiAction<SetCheckForm>
    {
        public SetCheckAction()
        {
            super(SetCheckForm.class);
        }

        public ApiResponse execute(final SetCheckForm form, BindException errors) throws Exception
        {
            String[] ids = form.getId(getViewContext().getRequest());
            int count = DataRegionSelection.setSelected(
                    getViewContext(), form.getKey(),
                    ids, form.isChecked());
            return new SelectionResponse(count);
        }

    }

    public static class SetCheckForm extends FormData
    {
        protected String key;
        protected String[] ids;
        protected boolean checked;

        public String getKey()
        {
            return key;
        }

        public void setKey(String key)
        {
            this.key = key;
        }

        public String[] getId(HttpServletRequest request)
        {
            // 5025 : DataRegion checkbox names may contain comma
            // Beehive parses a single parameter value with commas into an array
            // which is not what we want.
            return request.getParameterValues("id");
        }

        public void setId(String[] ids)
        {
            this.ids = ids;
        }

        public boolean isChecked()
        {
            return checked;
        }

        public void setChecked(boolean checked)
        {
            this.checked = checked;
        }
    }

    public static class SelectionResponse extends ApiSimpleResponse
    {
        public SelectionResponse(int count)
        {
            super("count", count);
        }
    }

}
