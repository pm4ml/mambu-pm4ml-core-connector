package com.modusbox.client.validator;

import com.modusbox.client.customexception.CCCustomException;
import com.modusbox.client.enums.ErrorCode;

public class DataValidator {

    public void validateZeroAmount(String Amount) throws Exception {
        System.out.println("Amount in validateZeroAmount method:"+ Amount);
        if (Float.parseFloat(Amount) == 0) {
            throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.PAYEE_LIMIT_ERROR, "Transfer amount cannot be zero value."));
        }
    }
    public void validateInvalidAmount(String Amount) throws Exception {
        System.out.println("Amount in validateInvalidAmount method:"+ Amount);
        try {
            Integer intAmount = Integer.parseInt(Amount);
        } catch (NumberFormatException e) {
            throw new CCCustomException(ErrorCode.getErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR, "Invalid transfer amount."));
        }
    }
}
