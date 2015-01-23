/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.authentication.saml;

import com.onelogin.AccountSettings;
import com.onelogin.AppSettings;
import com.onelogin.saml.AuthRequest;
import com.onelogin.saml.Response;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * User: tgaluhn
 * Date: 1/20/2015
 *
 * Work in progress prototype. Makes use of the OneLogin Java SAML toolkit available at:
 * https://github.com/onelogin/java-saml
 *
 *
 * The code here is modeled on the sample web app found in the toolkit.
 *
 * No configuration options have been provided yet. This is hardcoded to go against Tony's instance of the test IdP at onelogin.com.
 * See Tony for assistance if you want to test this.
 *
 */
public class SamlManager
{
    private static final Logger LOG = Logger.getLogger(SamlManager.class);

    static boolean sendSamlRequest(HttpServletRequest request, HttpServletResponse response)
    {
        // the appSettings object contain application specific settings used by the SAML library
        AppSettings appSettings = new AppSettings();

        // set the URL of the consume.jsp (or similar) file for this app. The SAML Response will be posted to this URL
        // For LK purposes, we're allowing arbitrary URL's; whatever page the user hit that redirected to authentication
        appSettings.setAssertionConsumerServiceUrl(request.getRequestURL().toString());

        // set the issuer of the authentication request. This would usually be the URL of the issuing web application
        appSettings.setIssuer("http://localhost:8080/labkey");
        //appSettings.setIssuer("https://app.onelogin.com/saml/metadata/420876");

        // the accSettings object contains settings specific to the users account.
        // At this point, your application must have identified the users origin
        AccountSettings accSettings = new AccountSettings();

        // The URL at the Identity Provider where to the authentication request should be sent
        accSettings.setIdpSsoTargetUrl("https://app.onelogin.com/trust/saml2/http-post/sso/420876"); // TODO: Hardcoded to test IdP, needs config

        // Generate an AuthRequest and send it to the identity provider
        AuthRequest authReq = new AuthRequest(appSettings, accSettings);
        try
        {
            String reqString = accSettings.getIdp_sso_target_url()+"?SAMLRequest=" + URLEncoder.encode(authReq.getRequest(AuthRequest.base64), StandardCharsets.UTF_8.toString());
            response.sendRedirect(reqString);
        }
        catch (Exception e)
        {
            LOG.error("Exception on SAML request", e);
            return false;
        }

        return true; // TODO: proper exception handling
    }

    static String getUserFromSamlResponse(HttpServletRequest request)
    {
        // TODO: this should be kept in the encrypted property store, and configured on config page
        String certificateS ="MIIELDCCAxSgAwIBAgIUBhIs1cNyHTg44KJJf6RN0Q5c/r0wDQYJKoZIhvcNAQEF" +
                "BQAwXzELMAkGA1UEBhMCVVMxGDAWBgNVBAoMD0xhYktleSBTb2Z0d2FyZTEVMBMG" +
                "A1UECwwMT25lTG9naW4gSWRQMR8wHQYDVQQDDBZPbmVMb2dpbiBBY2NvdW50IDU1" +
                "MTY0MB4XDTE1MDExODAzMjU0NVoXDTIwMDExOTAzMjU0NVowXzELMAkGA1UEBhMC" +
                "VVMxGDAWBgNVBAoMD0xhYktleSBTb2Z0d2FyZTEVMBMGA1UECwwMT25lTG9naW4g" +
                "SWRQMR8wHQYDVQQDDBZPbmVMb2dpbiBBY2NvdW50IDU1MTY0MIIBIjANBgkqhkiG" +
                "9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4iwRTz4stzwT/i9uUdglnOwJv2pCC92kSnBE" +
                "O3Mtr7Z4Jo0E3P5oytPIhbnNdRHWVmymoSpi9wU6jjYGkb33GjY640IqfLRNPOHF" +
                "XVYl6BWV6r6b6hELktRwXlMAwptcYt568juIadFuHb2iOyezz3/bnJHBNORN5DlY" +
                "h1N7bO6ehI/gQIdHQopADKz9ITKqkuVoSPKNJk/fj0gkoIgwtY+3EI1PG5duTD+W" +
                "DDy7J8rqWu0j7z6l9h4asnQph2i3rwdPnIRlPFyrZxHmzYpLa1fu14ZHMjVK7PT+" +
                "KGxgBnBxPTu/y/YKKp/wg61/zSc6ptFL5RteI3bP6GqzIARiwwIDAQABo4HfMIHc" +
                "MAwGA1UdEwEB/wQCMAAwHQYDVR0OBBYEFJT1WdWvG0gdRxJoiV5Z9Xuo/V4cMIGc" +
                "BgNVHSMEgZQwgZGAFJT1WdWvG0gdRxJoiV5Z9Xuo/V4coWOkYTBfMQswCQYDVQQG" +
                "EwJVUzEYMBYGA1UECgwPTGFiS2V5IFNvZnR3YXJlMRUwEwYDVQQLDAxPbmVMb2dp" +
                "biBJZFAxHzAdBgNVBAMMFk9uZUxvZ2luIEFjY291bnQgNTUxNjSCFAYSLNXDch04" +
                "OOCiSX+kTdEOXP69MA4GA1UdDwEB/wQEAwIHgDANBgkqhkiG9w0BAQUFAAOCAQEA" +
                "P8yelgNlYbTYV8tvjdbqT9CcsGGLpZywlwI6Va441EhfKmxqLRatRZGHfsNXAxSl" +
                "jVbamBr6ed28+Q6GVxgMj5i3QPMdv33M4cybN37cKScLotLiYtRBR3CF/TYsjOZ3" +
                "rV1saLK6gls1O/ej6iS3Xa/ofLpGJ/jayqKKIcn11J/HfFTH3dbbLr7qzrF9EwNr" +
                "awELWlfffWlTBp+3QzrENTIW/9j9rXR61UCXCIGn+9oJw2netqKgnQJhIxrV/Xm8" +
                "l7lQm1rJX2A2XoZg45arPiE1GbtxrvTPdZVhsisKxjkxloMRmJ3Fz562zt6sDVKJ" +
                "KNOiY77XiBuV/LjOfykulg==";

        // user account specific settings. Import the certificate here
        AccountSettings accountSettings = new AccountSettings();
        accountSettings.setCertificate(certificateS);

        try
        {
            Response samlResponse = new Response(accountSettings);
            samlResponse.loadXmlFromBase64(request.getParameter("SAMLResponse"));
            samlResponse.setDestinationUrl(request.getRequestURL().toString());

            if (samlResponse.isValid(false)) // allowing arbitrary destination URL
            {
                return samlResponse.getNameId();
            }
            else
                return null;
        }
        catch (Exception e)
        {
            LOG.error("Exception processing SAML response", e); // TODO: Proper exception handling
            return null;
        }
    }
}
