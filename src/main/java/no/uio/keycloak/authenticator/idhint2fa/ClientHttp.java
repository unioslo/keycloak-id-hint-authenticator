package no.uio.keycloak.authenticator.idhint2fa;

import org.jboss.logging.Logger;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Duration;
import java.net.URI;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

class ClientHttp {
  private static String url;
//  private static final Logger logger = Logger.getLogger(UiO2FAAuthenticator.class);
  private static final AtomicBoolean isRefreshing = new AtomicBoolean(false);
  private static volatile String authToken = "";
  private static String username = "";
  private static String password = "";
  private static final Logger logger = Logger.getLogger(ClientHttp.class);






  public static String triggerChallenge(String url,String user, String type){

    String query = "{\"user\" : \""+user+"\", \"type\" : \""+type+"\"}";
    JSONObject response = sendRequest(url,"post","/startmfa", query);
    try {

      // check if there are tokens
      // If not, default to otp
      String status = response.getString("status"); 
      String transaction_id = "radius";
      String state = "";
      if (type.equals("push")) {
         transaction_id = response.getString("transaction_id");
         return transaction_id;
      }else {
         state = response.getString("state");
         return state;
      }
    }catch(Exception e){return null;}
    
  }

  // Check if the challenge is right.
  // For push tokens, just send anything as pass.
  // For OTP, send the 6-digit as pass.
  public static Boolean check(String url,String user, String state, String token){

    String query = "{ \"user\" : \""+user+"\", \"state\" : \""+state+"\", \"type\" : \"otp\", \"token\" : \""+token+"\"}";
    logger.debug(query);
    JSONObject response = sendRequest(url,"post", "/startmfa",query);

    String result = "";
    try {
      result = response.getString("status");
      logger.debug("username=\""+user+"\", check=\""+result+"\"");
      if (result.equals("accepted")) {
         return true;
      }else {
         return false;
      }
    }catch (Exception e){
       logger.error("username=\""+user+"\", error=\"Failed to check the challenge.\"");
    }
    return false;

  }

  // Poll transactions
  // While here we could already get the result of the user's action,
  // ie, if the authentication was approved or not, we'd rather use the
  // check function for that purpose, so that eduMFA can mark the transaction_id
  // as used.
  // Here, false means that the user still didn't reply to the challenge.
  public static String pollTransaction(String url,String transactionId){
    JSONObject response = sendRequest(url,"get","/poll?transaction_id="+transactionId,"");
    String result = "";
    try {
        result  = response.getString("status");
        return result;
    }catch (Exception e){
       logger.error("Failed to poll the result");
       return "failed";
    }
  }




  private static JSONObject  sendRequest(String url,String method, String path, String query){
    HttpClient client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(60))
                            .build();
    String serviceUrl = url+path;
    //String serviceUrl = config.getConfig().get("uio2fa.host")+"/"+path;
    logger.info(query); 
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                         .uri(URI.create(serviceUrl))
                         .timeout(Duration.ofSeconds(70))
                          .POST(HttpRequest.BodyPublishers.ofString(query));



    if (method.equals("post")){
       requestBuilder.POST(HttpRequest.BodyPublishers.ofString(query));
       requestBuilder.header("Content-Type", "application/json");
    }else {
       requestBuilder.GET();
    }

    HttpRequest request = requestBuilder.build();

    try {
         logger.debug("Sending a request to the 2FA web server");
         java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
         logger.info("The request was sent");
         if (response.statusCode() == 401){
            logger.warn("The request to the 2FA web server returned a 401 error.");
            return null;
         }
         logger.debug("The request to the 2FA web server returned the following error code:");
         logger.debug(response.statusCode());
         logger.debug("The request to the 2FA web server returned the following response:");
         logger.debug(response.body());
         if (response.statusCode() == 200) {
           JSONObject jsonResponse = new JSONObject(response.body());
           return jsonResponse;
         }else {
           if (response.statusCode() == 404) {
              JSONObject resp = new JSONObject();
              resp.put("status", "timeout");
              return resp;
           }
           return null;
         }

	  }catch (Exception e){
      logger.error("Failed to send a request to the webserver");
      logger.error(e);
      return null;}

  }


}
