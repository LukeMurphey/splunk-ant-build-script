package net.lukemurphey.splunkbuild;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;
import java.util.Base64;

import net.lukemurphey.splunkbuild.SplunkTask;

/**
 * This class installs apps via the Splunkd endpoint: /services/apps/appinstall
 * 
 * @author luke.murphey
 *
 */
public class SplunkAppInstall extends SplunkTask {

    String appname = null;

    /**
     * Sets the name of the app to install.
     * 
     * @param password The Splunk password
     */
    public void setName(String appname) {
    	this.appname = appname;
    }

    /**
     * Perform the bump operation.
     */
    public void execute() {
    	
        try{
            installApp(this.appname, this.splunkweburl, this.username, this.password);
        }
        catch(Exception e){
            log(e.toString());
        	handleErrorOutput("Unable to install the Splunk app");
        }
        
    }

    /**
     * Get the encoded data for the Splunk install app form.
     */
    public byte[] getInstallFormBytes(String app) throws UnsupportedEncodingException{
    	
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("name", app);
        params.put("output_mode", "json");
        params.put("update", "true");
        
        return this.getFormBytes(params);
    }

    /**
     * Tell Splunk to restart.
     */
    public boolean installApp(String app, String url, String username, String password) throws Exception {

    	// Remove the trailing slash on the URL if necessary
        url = removeTrailingSlash(url);
    	
        installSSLValidator();

        // Do the restart
        HttpURLConnection connection = (HttpURLConnection) new URL("https://127.0.0.1:8089/services/apps/appinstall").openConnection();
        connection.setRequestMethod("POST");
        String userCredentials = username + ":" + password;
        String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userCredentials.getBytes()));
        connection.setRequestProperty ("Authorization", basicAuth);

        connection.setRequestProperty( "Content-type", "application/x-www-form-urlencoded");
        connection.setRequestProperty( "Accept", "*/*" );
        connection.setDoOutput(true);
        connection.getOutputStream().write(getInstallFormBytes(app));
        
        int responseCode = connection.getResponseCode();

        if(responseCode == 200 || responseCode == 201){
        	return true;
        }
        else{
            handleErrorOutput("App could not be installed (returned " + responseCode + ")");
            outputResponse(connection);
            return false;
        }

    }

}