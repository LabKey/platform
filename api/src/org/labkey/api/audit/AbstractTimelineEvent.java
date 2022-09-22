package org.labkey.api.audit;

import org.json.old.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.settings.LookAndFeelProperties;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractTimelineEvent
{
    public static final SimpleDateFormat auditDateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    protected AuditTypeEvent _event;
    protected User _currentUser;
    protected Container _container;

    protected Map<String, String> _oldDataMap;
    protected Map<String, String> _newDataMap;
    protected Map<String, String> _rawMetadataMap;

    protected Map<String, Object> _metadataObject = new LinkedHashMap<>();
    protected Object _entityObject;

    public static Object getUserObject(User user, User currentUser)
    {
        return createEntityObject(user.getUserId(), user.getDisplayName(currentUser), null, "user");
    }

    public static Object getTimestampObject(Date date, Container container)
    {
        String formattedDate = getFormattedDate(date, container);
        return createEntityObject(date, null, formattedDate, null);
    }

    public static Object createEntityObject(Object value, Object displayValue, Object formattedValue, String urlType)
    {
        return createEntityObject(value, displayValue, formattedValue, urlType, null);
    }

    public static Object createEntityObject(Object value, Object displayValue, Object formattedValue, String urlType, String urlProductKey)
    {
        if (displayValue == null && formattedValue == null && urlType == null)
            return value;

        JSONObject json = new JSONObject();
        json.put("value", value);
        if (displayValue != null)
            json.put("displayValue", displayValue);
        if (formattedValue != null)
            json.put("formattedValue", formattedValue);
        if (urlType != null)
            json.put("urlType", urlType);
        if (urlProductKey != null)
            json.put("urlProductKey", urlProductKey);

        return json;
    }

    public static String getFormattedDate(Date date, Container container)
    {
        return new SimpleDateFormat(LookAndFeelProperties.getInstance(container).getDefaultDateTimeFormat()).format(date);
    }

    public String getEventComment()
    {
        return _event.getComment();
    }

    public Map<String, Object> toMap()
    {
        Map<String, Object> map = new HashMap<>();

        map.put("rowId", _event.getRowId());
        map.put("eventType", getEventType());
        map.put("user", getUserObject(_event.getCreatedBy(), _currentUser));
        map.put("timestamp", getTimestampObject(_event.getCreated(), _container));
        map.put("summary", getEventComment());
        if (_oldDataMap != null || _newDataMap != null)
        {
            map.put("oldData", _oldDataMap);
            map.put("newData", _newDataMap);
        }
        if (null != _metadataObject && !_metadataObject.isEmpty())
            map.put("metadata", _metadataObject);
        if (null != _entityObject)
            map.put("entity", _entityObject);

        return map;
    }

    public abstract String getEventType();

    public AuditTypeEvent getEvent()
    {
        return _event;
    }

    public abstract void initMetadataEntityObject();

}
