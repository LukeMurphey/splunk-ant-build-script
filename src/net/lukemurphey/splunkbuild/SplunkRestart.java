package net.lukemurphey.splunkbuild;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.Base64;

import org.apache.tools.ant.BuildException;
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
    public void execute() throws BuildException{
    	
        try{
            restartSplunk(this.splunkweburl, this.username, this.password);
        }
        catch(BuildException e){
            throw e;
        }
        catch(Exception e){
        	throw new BuildException("Unable to restart Splunk due to an exception: " + e.toString());
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
    public void restartSplunk(String url, String username, String password) throws NoSuchAlgorithmException, ProtocolException, MalformedURLException, IOException, KeyManagementException {

    	// Remove the trailing slash on the URL if necessary
        url = removeTrailingSlash(url);
    	
        installSSLValidator();

        try{

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
            }
            else{
                throw new BuildException("Splunk could not be restarted");
            }
        }
        catch(SocketException e){
            throw new BuildException("Unable to connect to Splunk (connection failed)");
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