package com.github.ambry.admin;

import com.github.ambry.rest.RestRequestInfo;
import com.github.ambry.rest.RestRequestMetadata;
import com.github.ambry.rest.RestResponseHandler;
import com.github.ambry.rest.RestServiceErrorCode;
import com.github.ambry.rest.RestServiceException;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Performs the custom {@link AdminOperationType#echo} operation supported by the admin.
 */
class EchoHandler {
  protected static String TEXT_KEY = "text";
  private static Logger logger = LoggerFactory.getLogger(EchoHandler.class);

  /**
   * Handles {@link AdminOperationType#echo} operations.
   * <p/>
   * Extracts the parameters from the {@link RestRequestMetadata}, performs an echo if possible and writes the response
   * to the client via a {@link RestResponseHandler}.
   * <p/>
   * Flushes the written data and closes the connection on receiving an end marker (the last part of
   * {@link com.github.ambry.rest.RestRequestContent} in the request). Any other content is ignored.
   * @param restRequestInfo
   * @throws RestServiceException
   */
  public static void handleRequest(RestRequestInfo restRequestInfo, AdminMetrics adminMetrics)
      throws RestServiceException {
    RestResponseHandler responseHandler = restRequestInfo.getRestResponseHandler();
    if (restRequestInfo.isFirstPart()) {
      logger.trace("Handling echo - {}", restRequestInfo.getRestRequestMetadata().getUri());
      adminMetrics.echoRate.mark();
      long startTime = System.currentTimeMillis();
      String echoStr = echo(restRequestInfo.getRestRequestMetadata(), adminMetrics).toString();
      responseHandler.setContentType("application/json");
      responseHandler.addToResponseBody(echoStr.getBytes(), true);
      responseHandler.flush();
      adminMetrics.echoTimeInMs.update(System.currentTimeMillis() - startTime);
    } else if (restRequestInfo.getRestRequestContent().isLast()) {
      responseHandler.onRequestComplete(null, false);
      logger.trace("Finished handling echo - {}", restRequestInfo.getRestRequestMetadata().getUri());
    }
  }

  /**
   * Refers to the text provided by the client in the URI and returns a {@link JSONObject} representation of the echo.
   * @param restRequestMetadata
   * @return - A {@link JSONObject} that wraps the echoed string.
   * @throws RestServiceException
   */
  private static JSONObject echo(RestRequestMetadata restRequestMetadata, AdminMetrics adminMetrics)
      throws RestServiceException {
    Map<String, List<String>> parameters = restRequestMetadata.getArgs();
    if (parameters != null && parameters.containsKey(TEXT_KEY)) {
      try {
        // TODO: opportunity for batch get here.
        String text = parameters.get(TEXT_KEY).get(0);
        return packageResult(text);
      } catch (JSONException e) {
        logger.error("While trying to construct response for echo GET: Exception - ", e);
        adminMetrics.echoGetResponseBuildingError.inc();
        throw new RestServiceException("Unable to construct result object - ", e,
            RestServiceErrorCode.ResponseBuildingFailure);
      }
    } else {
      logger.debug("While trying to handle echo GET: Request missing parameter - {}", TEXT_KEY);
      adminMetrics.echoGetMissingParameter.inc();
      throw new RestServiceException("Request missing parameter - " + TEXT_KEY, RestServiceErrorCode.MissingArgs);
    }
  }

  /**
   * Packages the echoed string into a {@link JSONObject}.
   * @param text - the echoed text.
   * @return - A {@link JSONObject} that wraps the text.
   * @throws JSONException
   */
  private static JSONObject packageResult(String text)
      throws JSONException {
    JSONObject result = new JSONObject();
    result.put(TEXT_KEY, text);
    return result;
  }
}
