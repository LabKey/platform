package org.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.old.JSONArray;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;

public class OldJsonTest extends Assert
{
    @Test
    public void oldJsonOrgViaJackson() throws IOException
    {
        // Test serializing org.json.old JSON classes via Jackson
        ObjectMapper mapper = JsonUtil.DEFAULT_MAPPER;

        final Date d = new GregorianCalendar(2011, Calendar.DECEMBER, 3).getTime();

        org.json.old.JSONObject obj = new org.json.old.JSONObject();
        obj.put("str", "hello");
        obj.put("arr", new JSONArray(Arrays.asList("one", null, 3, new org.json.old.JSONObject(Collections.singletonMap("four", 4)))));
        obj.put("nul", (Object)null);
        obj.put("d", d);

        // Verify serializing org.json.old.JSONObject via Jackson is equivalent
        String jacksonToString = mapper.writeValueAsString(obj);
        String jsonOrgToString = obj.toString();
        assertEquals(jsonOrgToString, jacksonToString);

        // Verify deserializing org.json.old.JSONObject via Jackson is equivalent
        // NOTE: In both cases, the date value is deserialized as a string because JSON sucks
        org.json.old.JSONObject jsonOrgRoundTrip =  new org.json.old.JSONObject(jacksonToString);
        org.json.old.JSONObject jacksonRoundTrip = mapper.readValue(jacksonToString, org.json.old.JSONObject.class);
        assertEquals(jsonOrgRoundTrip, jacksonRoundTrip);
    }

    @Test
    public void jsonOrgTest()
    {
        ActionURL url = HttpView.currentContext().getActionURL();
        Map<String, Object> map = Map.of("url", url);
        assertEquals(new org.json.old.JSONObject(map).toString(), new org.json.JSONObject(map).toString());
        assertEquals("{\"url\":\"" + url + "\"}", new org.json.JSONObject(map).toString());
    }
}
