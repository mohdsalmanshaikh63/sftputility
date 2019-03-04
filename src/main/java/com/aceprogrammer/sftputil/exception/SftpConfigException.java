package com.aceprogrammer.sftputil.exception;

/**
 * Exception class used to denote improper initialization
 * or configuration parameters
 */
public class SftpConfigException extends Exception {

    public SftpConfigException(String errorMessage) {
        super(errorMessage);
    }
}
