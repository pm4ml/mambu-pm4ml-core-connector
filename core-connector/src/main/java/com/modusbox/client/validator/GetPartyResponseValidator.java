package com.modusbox.client.validator;

import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.customexception.WrittenOffAccountException;
import com.modusbox.client.enums.ErrorCode;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.json.JSONObject;

public class GetPartyResponseValidator implements Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        String body = exchange.getIn().getBody(String.class);
        JSONObject respObject = new JSONObject(body);

        String errorCode = "";
        String errorReason = "";

        if(respObject.has("statusCode")) {
            errorCode = respObject.getString("statusCode");
            errorReason = respObject.getString("message");
            if(errorCode.equals("3203")) {
                throw new WrittenOffAccountException(errorReason);
            }
        }
    }
}