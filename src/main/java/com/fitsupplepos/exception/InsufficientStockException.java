package com.fitsupplepos.exception;

public class InsufficientStockException extends BusinessException {
    public InsufficientStockException(String productName, int requested, int available) {
        super("Insufficient stock for \"" + productName + "\": requested " + requested
                + " but only " + available + " available.");
    }
}
