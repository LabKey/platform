package org.labkey.announcements.config;

import org.apache.commons.lang.math.NumberUtils;
import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.message.settings.AbstractConfigTypeProvider;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.util.Set;

public class AnnouncementEmailConfig extends AbstractConfigTypeProvider implements MessageConfigService.ConfigTypeProvider 
{
    public static final String TYPE = "messages";

    @Override
    public String getType()
    {
        return TYPE;
    }

    public String getName()
    {
        // appears in the config tab
        return getType();
    }

    @Override
    public HttpView createConfigPanel(ViewContext context, MessageConfigService.PanelInfo info) throws Exception
    {
        EmailConfigForm form = new EmailConfigForm();

        form.setDefaultEmailOption(AnnouncementManager.getDefaultEmailOption(context.getContainer()));
        form.setEmailOptions(AnnouncementManager.getEmailOptions());
        form.setDataRegionSelectionKey(info.getDataRegionSelectionKey());
        form.setReturnUrl(new ReturnURLString(info.getReturnUrl().getLocalURIString()));

        return new JspView<EmailConfigForm>("/org/labkey/announcements/view/announcementNotifySettings.jsp", form);
    }

    @Override
    public void validateCommand(ViewContext context, Errors errors)
    {
        Set<String> selected = DataRegionSelection.getSelected(context, false);

        if (selected.isEmpty())
            errors.reject(SpringActionController.ERROR_MSG, "There are no users selected for this update.");
    }

    @Override
    public boolean handlePost(ViewContext context, BindException errors) throws Exception
    {
        Object selectedOption = context.get("selectedEmailOption");

        if (selectedOption != null)
        {
            int newOption = NumberUtils.toInt((String)selectedOption);
            for (String selected : DataRegionSelection.getSelected(context, true))
            {

                User projectUser = UserManager.getUser(Integer.parseInt(selected));
                int currentEmailOption = AnnouncementManager.getUserEmailOption(context.getContainer(), projectUser);

                //has this projectUser's option changed? if so, update
                //creating new record in EmailPrefs table if there isn't one, or deleting if set back to folder default
                if (currentEmailOption != newOption)
                {
                    AnnouncementManager.saveEmailPreference(context.getUser(), context.getContainer(), projectUser, newOption);
                }
            }
            return true;
        }
        return false;
    }

    public static class EmailConfigForm extends ReturnUrlForm
    {
        int _defaultEmailOption;
        int _individualEmailOption;
        MessageConfigService.NotificationOption[] _emailOptions;
        String _dataRegionSelectionKey;

        public int getDefaultEmailOption()
        {
            return _defaultEmailOption;
        }

        public void setDefaultEmailOption(int defaultEmailOption)
        {
            _defaultEmailOption = defaultEmailOption;
        }

        public MessageConfigService.NotificationOption[] getEmailOptions()
        {
            return _emailOptions;
        }

        public void setEmailOptions(MessageConfigService.NotificationOption[] emailOptions)
        {
            _emailOptions = emailOptions;
        }

        public int getIndividualEmailOption()
        {
            return _individualEmailOption;
        }

        public void setIndividualEmailOption(int individualEmailOption)
        {
            _individualEmailOption = individualEmailOption;
        }

        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        public void setDataRegionSelectionKey(String dataRegionSelectionKey)
        {
            _dataRegionSelectionKey = dataRegionSelectionKey;
        }
    }
}