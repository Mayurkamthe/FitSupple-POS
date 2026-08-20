package com.fitsupplepos.exception;

public class ExpiredBatchException extends BusinessException {
    public ExpiredBatchException(String productName, String batchNumber) {
        super("Cannot sell \"" + productName + "\" — batch " + batchNumber + " is expired.");
    }
}
