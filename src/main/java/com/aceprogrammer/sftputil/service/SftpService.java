package com.aceprogrammer.sftputil.service;

import com.aceprogrammer.sftputil.config.SftpConfig;
import com.aceprogrammer.sftputil.exception.ChangeDirectoryException;
import com.aceprogrammer.sftputil.exception.FileDeletionException;
import com.aceprogrammer.sftputil.exception.LsCommandException;
import com.aceprogrammer.sftputil.exception.SftpConfigException;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author Mohammed Salman Shaikh
 *
 */
public interface SftpService {

    /**
     * @param sftpConfig the configuration with all mandatory params
     * @throws SftpConfigException in case any of mandatory params not provided
     */
    void initialize(SftpConfig sftpConfig) throws SftpConfigException;

    /**
     * @param destRelativePath the relative path on destination server
     * @param file             the file to be copied
     * @throws IOException
     */
    void uploadFile(String destRelativePath, File file) throws JSchException, SftpException, IOException;

    /**
     * @param destRelativePath the relative path on destination server
     * @param localFilePath    filePath on source machine
     * @throws IOException
     */
    void uploadFile(String destRelativePath, String localFilePath) throws IOException, SftpException, JSchException;

    /**
     * Use this method when subdirectories also need to be created for the given path
     *
     * @param destRelativePath the path containing subdirectories which
     *                         need to be created as well
     * @return the absolute remote path of the directory created
     * @throws JSchException
     */
    String createDirectories(String destRelativePath) throws JSchException, SftpException;

    /**
     * Use this method to upload multiple files to a single remote directory
     *
     * @param destRelativePath the relative path on destination server
     * @param fileList         list of absolute local file path to be uploded
     * @return list of uploaded remote file path
     * @throws SftpException
     * @throws JSchException
     * @throws IOException
     */
    Map<String, List<String>> uploadMultipleFiles(String destRelativePath, List<String> fileList) throws SftpException, JSchException, IOException;

    /**
     * Use this method to upload list of related files to a particular folder
     *
     * @param folderWiseFiles map containing files to be uploaded to folder i.e. key
     * @return a map of map - OuterMap.key -> Foldername
     * InnerMap.key -> either SUCESS or FAILURE
     * InnerMap.value -> List of filePath
     */
    Map<String, Map<String, List<String>>> uploadMultipleFiles(Map<String, List<String>> folderWiseFiles) throws JSchException, SftpException, IOException;

    /**
     * Use this method when only a single directory needs to be created
     *
     * @param dirName the name of the directory to be created
     * @return the absolute remote path of the directory created
     * @throws JSchException
     */
    String createDirectory(String dirName) throws JSchException, SftpException;

    Map<String, List<String>> downloadAllFiles(String sourcePath, String remotePath)throws ChangeDirectoryException, JSchException, LsCommandException;

	/**
	 * Use this method to change to home directory as per sftpconfig
	 * @throws ChangeDirectoryException
	 * @throws JSchException 
	 */
	void changeToHomeDirectory() throws ChangeDirectoryException, JSchException;

	/**
	 * @param fileList
	 * @return list of files which failed to be deleted
	 * Note caller can easily determine success files by using set operations
	 * of collections on the list passed by caller
	 * @throws JSchException
	 */
	List<String> deleteMultipleFiles(List<String> fileList) throws JSchException;

	/**
	 * @param filePath the remote absolute path of the file to be deleted
	 * @throws FileDeletionException
	 * @throws JSchException
	 */
	void deleteFile(String filePath) throws FileDeletionException, JSchException;

	/**
	 * @param fileList List of absolute path of the files to be moved on remote server
	 * @param destRelativePath relative folder path from home directory where the files need to be moved
	 * @return List of absolute file path of the files which failed to be moved
	 * @throws JSchException
	 */
	List<String> moveRemoteFiles(List<String> fileList, String destRelativePath) throws JSchException;
}
