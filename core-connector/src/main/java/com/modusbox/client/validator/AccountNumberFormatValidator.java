package com.modusbox.client.validator;


import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.enums.ErrorCode;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.regex.Pattern;

public class AccountNumberFormatValidator implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        String idType = (String) exchange.getIn().getHeader("idType");
        String loanAccount;
        if(idType.equalsIgnoreCase("ACCOUNT_ID")) {
            loanAccount = (String) exchange.getIn().getHeader("idValue");
            String regex = exchange.getProperty("accountNumberFormat", "[0-9]+", String.class);
            Pattern pattern = Pattern.compile(regex);

            if(!pattern.matcher(loanAccount).matches()) {
                throw new CCCustomException(ErrorCode.getErrorResponse(
                        ErrorCode.MALFORMED_SYNTAX,
                        "Invalid Account Number Format"));
            }
        }
    }
}