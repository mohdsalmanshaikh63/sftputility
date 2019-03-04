package com.aceprogrammer.sftputil.exception;

/**
 * Exception class used to denote rm command exception
 * in sftp operations
 */
public class FileDeletionException extends Exception {

    public FileDeletionException(String errorMessage) {
        super(errorMessage);
    }
}
