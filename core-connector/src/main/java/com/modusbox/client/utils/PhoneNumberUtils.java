package com.modusbox.client.utils;

public class PhoneNumberUtils {

    public static boolean isPhoneNumberMatch(String walletPhoneNumber, String mfiPhoneNumber) {
        return walletPhoneNumber.equals(mfiPhoneNumber) ||
                walletPhoneNumber.substring(1).equals(mfiPhoneNumber) ||
                walletPhoneNumber.substring(2).equals(mfiPhoneNumber);
    }
}
