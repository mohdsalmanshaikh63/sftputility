package com.aceprogrammer.sftputil.exception;

/**
 * Exception class used to denote cd command exception
 * in sftp operations
 */
public class ChangeDirectoryException extends Exception {

    public ChangeDirectoryException(String errorMessage) {
        super(errorMessage);
    }
}
