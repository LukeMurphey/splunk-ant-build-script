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

    public boolean isURLUp(String url) throws IOException, MalformedURLException{

        try{
            HttpURLConnection testConnection = (HttpURLConnection) new URL(url).openConnection();
            testConnection.getResponseCode();
            return true;
        }
        catch(SocketException e){
            return false;
        }
    }

    private String getSeconds(long startTime){

        long currentTime = System.nanoTime();

        long seconds = (currentTime - startTime)/1000000000;

        return Math.round(seconds) + "s";
    }

    public void waitUntilURLIsUp(String url, int maxSeconds, boolean waitForBounce, boolean requireBounce) throws IOException{

        int sleepTime = 200;
        int multiplier = 1000/sleepTime;

        boolean detectedDown = false;
        boolean isUp = false;

        // This tracks the time that we started waiting
        long startTime = System.nanoTime();

        for(int c = 0; c < (maxSeconds * multiplier); c++){

            // Determine if Splunk is up
            isUp = isURLUp(url);

            // Handle the case where Splunk is down
            if(!isUp){

                if(!detectedDown){
                    log("Splunk is detected as down; t=" + getSeconds(startTime) + ", url=" + url);
                }

                // Note that we did detect Splunk down
                detectedDown = true;
            }

            // Handle the case where we want to see Splunk go down first before it comes back up
            else if(isUp && detectedDown){
                log("Splunk is back up; t=" + getSeconds(startTime) + ", url=" + url);
                return;
            }

            // Handle the case where we want to see Splunk go down first before it comes back up and it has not gone down
            else if(isUp && !detectedDown && waitForBounce){
                // Wait longer
            }

            // Handle the case where Splunk is detected down
            else if(isUp && !waitForBounce){
                log("Splunk is up; t=" + getSeconds(startTime) + ", url=" + url);
                return;
            }

            // Sleep a bit and try again
            try{
                Thread.sleep(sleepTime);
            }
            catch(InterruptedException e){
                // Continue
            }
            
        }
        
        // If we got here, then something has gone wrong. Lets figure out what.

        // If the host is up, then we failed to see the host go down
        if(isUp && requireBounce){
            throw new BuildException("Splunk was never detected in a down state and thus may not have restarted; t=" + String.valueOf(maxSeconds));
        }

        // Looks like Splunk failed to come up 
        if(!isUp){
            throw new BuildException("Splunk failed to come backup in time; t=" + String.valueOf(maxSeconds));
        }

    }

    /**
     * Tell Splunk to restart.
     */
    public void restartSplunk(String url, String username, String password) throws NoSuchAlgorithmException, ProtocolException, MalformedURLException, IOException, KeyManagementException {

        log("Attempting to restart Splunk; username=" + username + ", url=" + url);

    	// Remove the trailing slash on the URL if necessary
        url = removeTrailingSlash(url);
    	
        installSSLValidator();
        
        try{

            // Do the restart
            HttpURLConnection restartConnection = (HttpURLConnection) new URL(url + "/services/server/control/restart").openConnection();
            restartConnection.setRequestMethod("POST");
            String userCredentials = username + ":" + password;
            
            String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userCredentials.getBytes()));
            restartConnection.setRequestProperty ("Authorization", basicAuth);

            restartConnection.setDoOutput(true);
            
            int responseCode = restartConnection.getResponseCode();

            if(responseCode == 200){
                log("Restart request successfully sent");

                // Poll Splunk until it is back up
                waitUntilURLIsUp(url, 240, true, false);
            }
            else if(responseCode == 401){
                throw new BuildException("Restart request could not be done since the account could not be authenticated (returned " + responseCode + "); make sure the account \"" + username + "\" can authenticate to " + url);
            }
            else if(responseCode == 403){
                throw new BuildException("Restart request could not be done since it isn't authorized (returned " + responseCode + "); make sure the account \"" + username + "\" has the following capability: restart_splunkd");
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