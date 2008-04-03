package org.labkey.api.security;

import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.emailTemplate.EmailTemplate;

import javax.mail.internet.MimeMessage;

public class SecurityMessage
{
    private String _verificationURL;
    private String _from;
    private String _to;
    private String _type;
    private String _messagePrefix;
    private boolean _hideContact;
    private SecurityManager.SecurityEmailTemplate _template;

    public SecurityMessage(){}

    public void setEmailTemplate(SecurityManager.SecurityEmailTemplate template)
    {
        _template = template;
    }

    public MimeMessage createMailMessage() throws Exception
    {
        if (_template != null)
        {
            MailHelper.MultipartMessage m = MailHelper.createMultipartMessage();

            _template.setVerificationUrl(getVerificationURL());
            _template.setEmailAddress(getFrom());
            _template.setRecipient(getTo());
            _template.setHideContact(getHideContact());
            _template.setOptionPrefix(getMessagePrefix());

            String body = _template.renderBody();
            m.setBodyContent(body, "text/plain");
            m.setBodyContent(PageFlowUtil.filter(body, true, true), "text/html");

            m.setSubject(_template.renderSubject());

            return m;
        }
        throw new IllegalStateException("An email template has not been set for this message yet.");
    }

    public EmailTemplate getEmailTemplate(){return _template;}

    public void setVerificationURL(String url)
    {
        _verificationURL = url;
    }
    public String getVerificationURL()
    {
        return _verificationURL;
    }
    public String getFrom()
    {
        return _from;
    }
    public void setFrom(String from)
    {
        _from = from;
    }

    public String getType()
    {
        return _type;
    }
    public void setType(String type)
    {
        _type = type;
    }

    public String getMessagePrefix()
    {
        return _messagePrefix;
    }

    public void setMessagePrefix(String messagePrefix)
    {
        _messagePrefix = messagePrefix;
    }

    public boolean getHideContact()
    {
        return _hideContact;
    }

    public void setHideContact(boolean hideContact)
    {
        _hideContact = hideContact;
    }

    public void setTo(String to){_to = to;}
    public String getTo(){return _to;}
}
