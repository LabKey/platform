package org.labkey.api.settings;

import org.labkey.api.data.PropertyManager;
import java.util.Map;

/**
 * Created by jimpiper on 9/1/15.
 */
public class FooterProperties
{

    public static final String FOOTER_CONFIGS = "FooterProperties";
    public static final String SHOW_FOOTER_PROPERTY_NAME = "ShowFooter";

    public static boolean isShowFooter() {
        String showFooter = "TRUE"; // default is to show the footer
        Map<String, String> map = PropertyManager.getProperties(FOOTER_CONFIGS);
        if (null != map && !map.isEmpty())
        {
            showFooter = map.get(SHOW_FOOTER_PROPERTY_NAME);
        }
        return ("FALSE".equalsIgnoreCase(showFooter)) ? false : true;
    }

    public static void setShowFooter(boolean isShowFooter) {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(FOOTER_CONFIGS, true);
        map.put(SHOW_FOOTER_PROPERTY_NAME, (isShowFooter) ? "TRUE" : "FALSE");
        map.save();
    }
 }
