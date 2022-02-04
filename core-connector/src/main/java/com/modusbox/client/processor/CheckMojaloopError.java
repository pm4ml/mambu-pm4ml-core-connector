package com.modusbox.client.processor;

import com.google.gson.Gson;
import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.enums.ErrorCode;
import com.modusbox.log4j2.message.CustomJsonMessage;
import com.modusbox.log4j2.message.CustomJsonMessageImpl;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;

public class CheckMojaloopError implements Processor {

    CustomJsonMessage customJsonMessage = new CustomJsonMessageImpl();

    public void process(Exchange exchange) throws Exception {
        Gson gson = new Gson();
        String s = gson.toJson(exchange.getIn().getBody(), LinkedHashMap.class);
        JSONObject respObject = new JSONObject(s);
        int errorCode = 0;
        String errorMessage = "";

        try {
            errorCode = respObject.getInt("statusCode");
            errorMessage = respObject.getString("message");
            if (errorCode == 3208) {
                customJsonMessage.logJsonMessage("error", String.valueOf(exchange.getIn().getHeader("X-CorrelationId")),
                        "Processing the exception at CheckMojaloopError", null, null, respObject.toString());
                throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.TRANSFER_ID_NOT_FOUND));
            }
            else if (errorCode == 400) {
                customJsonMessage.logJsonMessage("error", String.valueOf(exchange.getIn().getHeader("X-CorrelationId")),
                        "Processing the exception at CheckMojaloopError", null, null, respObject.toString());
                if (errorMessage.startsWith(".path.transferId should match pattern")) {
                    throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.MALFORMED_SYNTAX));
                }
                else {
                    throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.GENERIC_VALIDATION_ERROR));
                }
            }            
            else {
                customJsonMessage.logJsonMessage("error", String.valueOf(exchange.getIn().getHeader("X-CorrelationId")),
                        "Processing the exception at CheckMojaloopError, unhandled error code", null, null, respObject.toString());
                throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR, "Error while retrieving transfer state, please retry later."));
            }
        } catch (JSONException e) {
            System.out.println("Problem extracting error code from Mojaloop error response occurred.");
            throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR));
        }

    }

}
