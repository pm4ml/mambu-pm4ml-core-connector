package com.modusbox.client.validator;

import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.enums.ErrorCode;
import com.modusbox.client.utils.PhoneNumberUtils;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class PhoneNumberValidation implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        String mfiPhoneNumber = "";
        String body = exchange.getProperty("getClientByIdResponse").toString();
        body = body.substring(1, body.length() - 1);
        String[] keyValues = body.split(",");
        Map<String, String> keyPairs = new HashMap<String, String>();
        for(int ctr=0; ctr < keyValues.length; ctr++) {
           String[] keyValue = keyValues[ctr].trim().split("=", 2);
            keyPairs.put(keyValue[0].trim(), keyValue[1].trim());
        }

        if(keyPairs.containsKey("mobilePhone1")) {
            mfiPhoneNumber =
                    keyPairs.get("mobilePhone1");
        }

        String walletPhoneNumber =
                (String) exchange.getIn().getHeader("idSubValue");
        if(!PhoneNumberUtils.isPhoneNumberMatch(walletPhoneNumber.trim(), mfiPhoneNumber.trim())) {
            throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.PHONE_NUMBER_MISMATCH));
        }
    }
}