package com.aceprogrammer.sftputil.exception;

/**
 * Exception class used to denote mv command exception
 * in sftp operations
 */
public class MoveFileException extends Exception {

    public MoveFileException(String errorMessage) {
        super(errorMessage);
    }
}
