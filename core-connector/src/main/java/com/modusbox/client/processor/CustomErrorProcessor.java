package com.modusbox.client.processor;

import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.enums.ErrorCode;
import com.modusbox.log4j2.message.CustomJsonMessage;
import com.modusbox.log4j2.message.CustomJsonMessageImpl;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.http.conn.ConnectTimeoutException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import javax.ws.rs.InternalServerErrorException;
import java.net.SocketTimeoutException;
import java.util.stream.*;

@Component("customErrorProcessor")
public class CustomErrorProcessor implements Processor {

    CustomJsonMessage customJsonMessage = new CustomJsonMessageImpl();

    @Override
    public void process(Exchange exchange) throws Exception {

        String reasonText = "{ \"statusCode\": \"5000\"," +
                "\"message\": \"Unknown\" }";
        String statusCode = "5000";
        int httpResponseCode = 500;

        JSONObject errorResponse = null;

        boolean errorFlag = false;

        String errorDescription = "Downstream API failed.";
        // The exception may be in 1 of 2 places
        Exception exception = exchange.getException();
        if (exception == null) {
            exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        }

        if (exception != null) {
            if (exception instanceof HttpOperationFailedException) {
                HttpOperationFailedException e = (HttpOperationFailedException) exception;
                try {
                    if (null != e.getResponseBody()) {
                        /* Below if block needs to be changed as per the error object structure specific to
                            CBS back end API that is being integrated in Core Connector. */
                        JSONObject respObject = new JSONObject(e.getResponseBody());
                        if (respObject.has("returnStatus")) {
                            statusCode = String.valueOf(respObject.getInt("returnCode"));
                            errorDescription = respObject.getString("returnStatus");
                        }
                        if(e.getStatusCode() == 404 && statusCode.equals("301")) {
                            errorFlag = true;
                            errorResponse = new JSONObject(ErrorCode.getErrorResponse(ErrorCode.GENERIC_ID_NOT_FOUND));
                        }
                        if (respObject.has("errors")) {
                            JSONArray jsonArray = respObject.getJSONArray("errors");
                            statusCode = String.valueOf(IntStream.range(0, jsonArray.length())
                                    .mapToObj(index -> ((JSONObject)jsonArray.get(index)).optString("errorCode"))
                                    .collect(Collectors.toList()));
                            errorDescription = String.valueOf(IntStream.range(0, jsonArray.length())
                                    .mapToObj(index -> ((JSONObject)jsonArray.get(index)).optString("errorReason"))
                                    .collect(Collectors.toList()));
                            statusCode = statusCode.substring(1,statusCode.length()-1);
                            errorDescription = errorDescription.substring(1,errorDescription.length()-1);
                            if(statusCode.equals("110")) {
                                errorFlag = true;
                                errorResponse = new JSONObject(ErrorCode.getErrorResponse(ErrorCode.PAYEE_LIMIT_ERROR,errorDescription));
                            }
                        }
                    }
                } finally {
                    if (errorFlag) {
                        httpResponseCode = errorResponse.getInt("errorCode");
                        errorResponse = errorResponse.getJSONObject("errorInformation");
                        statusCode = String.valueOf(errorResponse.getInt("statusCode"));
                        errorDescription = errorResponse.getString("description");
                    }
                    reasonText = "{ \"statusCode\": \"" + statusCode + "\"," +
                            "\"message\": \"" + errorDescription + "\"} ";
                }
            } else {
                try {
                    if(exception instanceof CCCustomException) {
                        errorResponse = new JSONObject(exception.getMessage());
                    } else if(exception instanceof InternalServerErrorException) {
                        errorResponse = new JSONObject(ErrorCode.getErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR));
                    } else if(exception instanceof ConnectTimeoutException || exception instanceof SocketTimeoutException) {
                        errorResponse = new JSONObject(ErrorCode.getErrorResponse(ErrorCode.SERVER_TIMED_OUT));
                    }
                } finally {
                    httpResponseCode = errorResponse.getInt("errorCode");
                    errorResponse = errorResponse.getJSONObject("errorInformation");
                    statusCode = String.valueOf(errorResponse.getInt("statusCode"));
                    errorDescription = errorResponse.getString("description");
                    reasonText = "{ \"statusCode\": \"" + statusCode + "\"," +
                            "\"message\": \"" + errorDescription + "\"} ";
                }
            }
            customJsonMessage.logJsonMessage("error", String.valueOf(exchange.getIn().getHeader("X-CorrelationId")),
                    "Processing the exception at CustomErrorProcessor", null, null,
                    exception.getMessage());
        }

        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, httpResponseCode);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
        exchange.getMessage().setBody(reasonText);
    }
}