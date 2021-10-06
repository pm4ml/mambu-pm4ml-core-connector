package com.modusbox.client.validator;

import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.enums.ErrorCode;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class GetPartyResponseValidator implements Processor {
    @Override
    public void process(Exchange exchange) throws Exception {

            throw new CCCustomException(ErrorCode.getErrorResponse(
                    ErrorCode.GENERIC_ID_NOT_FOUND
            ));

    }
}