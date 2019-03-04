package com.aceprogrammer.sftputil.exception;

/**
 * Exception class used to denote ls command exception
 * in sftp operations
 */
public class LsCommandException extends Exception {

    public LsCommandException(String errorMessage) {
        super(errorMessage);
    }
}
