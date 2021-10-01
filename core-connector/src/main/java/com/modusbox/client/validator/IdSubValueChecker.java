package com.modusbox.client.validator;

import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.enums.ErrorCode;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class IdSubValueChecker implements Processor {


    @Override
    public void process(Exchange exchange) throws Exception {
        boolean isIdSubValueRequired = false;
        String idType = (String) exchange.getIn().getHeader("idType");
        String idSubValue = (String) exchange.getIn().getHeader("idSubValue");
        if(idType.equalsIgnoreCase("ACCOUNT_ID")) {
            isIdSubValueRequired = true;
        }

        if(isIdSubValueRequired && (idSubValue == null || idSubValue.trim().isEmpty())) {
            throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR));
        }
    }
}
