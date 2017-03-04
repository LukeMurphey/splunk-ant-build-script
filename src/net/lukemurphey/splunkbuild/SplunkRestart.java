package net.lukemurphey.splunkbuild;

import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;
import java.util.Base64;

import net.lukemurphey.splunkbuild.SplunkTask;

/**
 * This class performs a bump operation against SplunkWeb in order to invalidate the cached web-resources.
 * 
 * @author luke.murphey
 *
 */
public class SplunkRestart extends SplunkTask {

    /**
     * Perform the Splunk restart operation.
     */
    public void execute() {
    	
        try{
            restartSplunk(this.splunkweburl, this.username, this.password);
        }
        catch(Exception e){
        	handleErrorOutput("Unable to restart Splunk due to an exception");
            log(e.toString());
        }
        
    }
    
    /**
     * Get the encoded data for the Splunk restart form.
     */
    public byte[] getRestartFormBytes(String operation) throws UnsupportedEncodingException{
    	
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("operation", "restart_server");
        
        return this.getFormBytes(params);
    }

    /**
     * Tell Splunk to restart.
     */
    public boolean restartSplunk(String url, String username, String password) throws Exception {

    	// Remove the trailing slash on the URL if necessary
        url = removeTrailingSlash(url);
    	
        installSSLValidator();

        // Do the restart
        HttpURLConnection restartConnection = (HttpURLConnection) new URL("https://127.0.0.1:8089/services/server/control/restart").openConnection();
        restartConnection.setRequestMethod("POST");
        String userCredentials = username + ":" + password;
        String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userCredentials.getBytes()));
        restartConnection.setRequestProperty ("Authorization", basicAuth);

        restartConnection.setDoOutput(true);
        
        int responseCode = restartConnection.getResponseCode();

        if(responseCode == 200){
            log("Restart request successfully sent");
        	return true;
        }
        else{
            handleErrorOutput("Splunk could not be restarted)");
            return false;
        }
    }

    /**
     * Tell Splunk to restart.
     */
    public boolean restartSplunk2(String url, String username, String password) throws Exception {

    	// Remove the trailing slash on the URL if necessary
        url = removeTrailingSlash(url);
    	
    	// Step 1: Log into Splunk
    	loginToSplunk(url, username, password);

        String formkey = getFormKey(url);

        //log(formkey);
        if(formkey == null){
            return false;
        }

        log(formkey);

        // Do the restart
        HttpURLConnection restartConnection = (HttpURLConnection) new URL(url + "/en-US/api/manager/control").openConnection();
        restartConnection.setRequestMethod("POST");

        restartConnection.setRequestProperty("X-Splunk-Form-Key", formkey);

        restartConnection.setRequestProperty( "Content-type", "application/x-www-form-urlencoded");
        restartConnection.setRequestProperty( "Accept", "*/*" );
        restartConnection.setDoOutput(true);
        restartConnection.getOutputStream().write(getRestartFormBytes("restart_server"));
        log(restartConnection.getRequestMethod());
        int responseCode = restartConnection.getResponseCode();

        if(responseCode == 200){
            log("Restart request successfully sent");
        	return true;
        }
        else{
            handleErrorOutput("Splunk could not be restarted)");
            return false;
        }

    }
    
    /**
     * Restart Splunk by asking Splunk to restart through Splunkd.
     * 
     * @throws Exception
     */
    public void restartSplunk() throws Exception {
    	restartSplunk("http://localhost:8000", "admin", "changeme");
    }
}