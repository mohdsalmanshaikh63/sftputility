package com.aceprogrammer.sftputil.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.Vector;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.aceprogrammer.sftputil.config.SftpConfig;
import com.aceprogrammer.sftputil.constants.ConfigFields;
import com.aceprogrammer.sftputil.constants.ConfigValues;
import com.aceprogrammer.sftputil.constants.FileTransferResults;
import com.aceprogrammer.sftputil.constants.SftpConstants;
import com.aceprogrammer.sftputil.exception.ChangeDirectoryException;
import com.aceprogrammer.sftputil.exception.MoveFileException;
import com.aceprogrammer.sftputil.exception.FileDeletionException;
import com.aceprogrammer.sftputil.exception.LsCommandException;
import com.aceprogrammer.sftputil.exception.SftpConfigException;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import static com.aceprogrammer.sftputil.constants.SftpConstants.*;

/**
 * @author Mohammed Salman Shaikh
 */
public class SftpServiceImpl implements SftpService {

    private final Log logger = LogFactory.getLog(this.getClass());

    private SftpConfig sftpConfig;

    @Override
    public void initialize(SftpConfig sftpConfig) throws SftpConfigException {

        if (Objects.nonNull(sftpConfig)) {

            // check all config parameters are present
            // if any erroneous parameters then throw sftpConfigException
            String host = sftpConfig.getHost();
            String user = sftpConfig.getUserName();
            String password = sftpConfig.getPassword();
            String homePath = sftpConfig.getHomePath();

            boolean emptyCheck = StringUtils.isNoneEmpty(host, user, password, homePath);
            if (!emptyCheck) {
                StringBuilder builder = new StringBuilder();
                builder.append("All parameters of SftpConfig are mandatory!").append("Please check your SftpConfig")
                        .append("Rejected object:").append(sftpConfig.toString());
                String errorMsg = builder.toString();
                logger.error(errorMsg);
                throw new SftpConfigException(errorMsg);
            }

            this.sftpConfig = sftpConfig;
        } else {
            logger.error("SftpConfig is null");
            throw new SftpConfigException("Please provide sftpConfiguration!");
        }

    }

    @Override
    public void uploadFile(String destRelativePath, File file) throws JSchException, SftpException, IOException {

        // if destination path is empty string that means we need to upload to home
        // folder itself
        if (!ObjectUtils.allNotNull(destRelativePath, file)) {
            logger.info("No file uploaded!");
            logger.info("Destination path was " + destRelativePath);
            logger.info("File was:" + file);
            return;
        } else {
            Path destRelPath = Paths.get(destRelativePath);
            String destFileName;
            // if filename is present in path then get parent path
            if (Objects.nonNull(destRelPath) && destRelPath.toFile().isFile()) {
                Path parentPath = destRelPath.getParent();
                destFileName = destRelPath.getFileName().toString();
                if (Objects.isNull(parentPath)) {
                    destRelativePath = "";
                } else {
                    destRelativePath = parentPath.toString();
                }
            } else {
                destFileName = file.getName();
            }
            // declare necessary stuff
            Session session = null;
            Channel channel = null;
            try {
                // connect session and channel
                session = getSession();
                session.connect();
                logger.debug(SESSION_CONNECT);
                channel = session.openChannel(ConfigFields.SFTP_CHANNEL);
                channel.connect();
                logger.debug(CHANNEL_CONNECT);
                ChannelSftp sftp = (ChannelSftp) channel;

                String remoteAbsolutePath = createDirectories(destRelativePath, sftp);
                sftp.cd(remoteAbsolutePath);

                try (InputStream inputStream = new FileInputStream(file)) {
                    logger.debug("Trying to upload File:" + file.getName() + " to: " + remoteAbsolutePath);
                    // if given path doesn't have file name then use the file name of the given file
                    // itself
                    sftp.put(inputStream, destFileName);
                    logger.info("File:" + file.getName() + " was uploaded successfully to: " + remoteAbsolutePath);
                }
            } finally {
                if (channel != null) {
                    channel.disconnect();
                }
                if (session != null) {
                    session.disconnect();
                }
            }

        }
    }

    @Override
    public void uploadFile(String destRelativePath, String localFilePath)
            throws IOException, SftpException, JSchException {
        uploadFile(destRelativePath, new File(localFilePath));
    }

    @Override
    public Map <String, List <String>> uploadMultipleFiles(String destRelativePath, List <String> localFileList)
            throws SftpException, JSchException {

        if (Objects.isNull(localFileList) || localFileList.isEmpty()) {
            logger.info("No file uploaded");
            logger.info("Destination path was " + destRelativePath);
            logger.info("FileList was " + localFileList);
            return Collections.emptyMap();
        } else {
            Session session = null;
            Channel channel = null;

            Map <String, List <String>> fileUploadStatus = new TreeMap <>();
            List <String> uploadedFiles;
            List <String> failedFiles;

            try {
                session = getSession();
                session.connect();
                logger.debug(SESSION_CONNECT);
                channel = session.openChannel(ConfigFields.SFTP_CHANNEL);
                channel.connect();
                logger.debug(CHANNEL_CONNECT);
                ChannelSftp sftp = (ChannelSftp) channel;

                // create the destination directory first
                String remoteFolder = createDirectories(destRelativePath, sftp);

                sftp.cd(remoteFolder);

                // then upload all files to that directory

                // loop through each file and keep uploading
                // will fail if any of the filepath doesn't contain a filename
                logger.info("Uploading files:" + localFileList + " to " + remoteFolder);
                failedFiles = uploadMultipleFilesToFolder(sftp, localFileList, remoteFolder);

                // subtract uploadedFiles from original list
                localFileList.removeAll(failedFiles);
                uploadedFiles = localFileList;

                // put them in map
                fileUploadStatus.put(FileTransferResults.SUCCESS, uploadedFiles);
                fileUploadStatus.put(FileTransferResults.FAILURE, failedFiles);
                return fileUploadStatus;
            } finally {
                if (channel != null) {
                    channel.disconnect();
                }
                if (session != null) {
                    session.disconnect();
                }
            }
        }
    }

    @Override
    public Map <String, Map <String, List <String>>> uploadMultipleFiles(Map <String, List <String>> folderWiseFiles)
            throws JSchException {

        if (!Objects.isNull(folderWiseFiles)) {

            Map <String, Map <String, List <String>>> finalMap = new TreeMap <>();
            Session session = null;
            Channel channel = null;
            try {
                session = getSession();
                session.connect();
                logger.debug(SESSION_CONNECT);
                channel = session.openChannel(ConfigFields.SFTP_CHANNEL);
                channel.connect();
                logger.debug(CHANNEL_CONNECT);
                ChannelSftp sftp = (ChannelSftp) channel;

                for (Map.Entry <String, List <String>> entry : folderWiseFiles.entrySet()) {

                    String folder = entry.getKey();
                    List <String> localFileList = entry.getValue();

                    Map <String, List <String>> fileUploadStatus = new TreeMap <>();
                    List <String> uploadedFiles = null;
                    List <String> failedFiles = null;
                    try {
                        if (!localFileList.isEmpty()) {
                            // create folder first
                            String remoteFolder = createDirectories(folder, sftp);

                            // move to the folder
                            sftp.cd(remoteFolder);

                            // loop list of files and upload them
                            logger.info("Uploading files:" + localFileList + " to " + remoteFolder);
                            failedFiles = uploadMultipleFilesToFolder(sftp, localFileList, remoteFolder);

                            // subtract uploadedFiles from original list
                            localFileList.removeAll(failedFiles);
                            uploadedFiles = localFileList;
                        }
                    } catch (Exception e) {
                        logger.error("Error while uploading files:" + localFileList + " to relative path:" + folder);
                        logger.error(e);
                    }

                    logger.info("Successfully uploaded files list for folder " + folder + "is " + uploadedFiles);
                    logger.info("Failed files list for folder " + folder + "is " + failedFiles);

                    // put them in map
                    fileUploadStatus.put(FileTransferResults.SUCCESS, uploadedFiles);
                    fileUploadStatus.put(FileTransferResults.FAILURE, failedFiles);

                    // update final map
                    finalMap.put(folder, fileUploadStatus);
                }
                return finalMap;
            } finally {
                if (channel != null) {
                    channel.disconnect();
                }
                if (session != null) {
                    session.disconnect();
                }
            }
        }

        return null;
    }

    @Override
    public String createDirectory(String dirName) throws JSchException, SftpException {
        Session session = null;
        Channel channel = null;
        try {
            session = getSession();
            session.connect();
            logger.debug(SESSION_CONNECT);
            channel = session.openChannel(ConfigFields.SFTP_CHANNEL);
            channel.connect();
            logger.debug(CHANNEL_CONNECT);
            ChannelSftp sftp = (ChannelSftp) channel;
            return createDirectory(dirName, sftp);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    @Override
    public Map <String, List <String>> downloadAllFiles(String sourcePath, String destRelativePath)
            throws ChangeDirectoryException, JSchException, LsCommandException {
        Session session = null;
        Channel channel = null;
        try {
            session = getSession();
            session.connect();
            logger.debug(SESSION_CONNECT);
            channel = session.openChannel(ConfigFields.SFTP_CHANNEL);
            channel.connect();
            logger.debug(CHANNEL_CONNECT);
            ChannelSftp sftp = (ChannelSftp) channel;
            return downloadAllFiles(sourcePath, destRelativePath, sftp);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    @Override
    public String createDirectories(String destRelativePath) throws JSchException, SftpException {
        Session session = null;
        Channel channel = null;
        try {
            session = getSession();
            session.connect();
            logger.debug(SESSION_CONNECT);
            channel = session.openChannel(ConfigFields.SFTP_CHANNEL);
            channel.connect();
            logger.debug(CHANNEL_CONNECT);
            ChannelSftp sftp = (ChannelSftp) channel;
            return createDirectories(destRelativePath, sftp);

        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    @Override
    public void changeToHomeDirectory() throws ChangeDirectoryException, JSchException {

        Session session = null;
        Channel channel = null;
        try {
            session = getSession();
            session.connect();
            logger.debug(SESSION_CONNECT);
            channel = session.openChannel(ConfigFields.SFTP_CHANNEL);
            channel.connect();
            logger.debug(CHANNEL_CONNECT);
            ChannelSftp sftp = (ChannelSftp) channel;
            changeToHomeDirectory(sftp);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }

    }

    @Override
    public List <String> deleteMultipleFiles(List <String> fileList) throws JSchException {

        Session session = null;
        Channel channel = null;
        try {
            session = getSession();
            session.connect();
            logger.debug(SESSION_CONNECT);
            channel = session.openChannel(ConfigFields.SFTP_CHANNEL);
            channel.connect();
            logger.debug(CHANNEL_CONNECT);
            ChannelSftp sftp = (ChannelSftp) channel;
            return deleteMultipleFiles(fileList, sftp);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    @Override
    public void deleteFile(String filePath) throws FileDeletionException, JSchException {

        Session session = null;
        Channel channel = null;
        try {
            session = getSession();
            session.connect();
            logger.debug(SESSION_CONNECT);
            channel = session.openChannel(ConfigFields.SFTP_CHANNEL);
            channel.connect();
            logger.debug(CHANNEL_CONNECT);
            ChannelSftp sftp = (ChannelSftp) channel;
            deleteFile(filePath, sftp);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }

    }

    @Override
    public List <String> moveRemoteFiles(List <String> fileList, String destRelativePath) throws JSchException {

        List <String> failedFiles = new ArrayList <>();

        // if path is empty then move files to home directory

        // use rename command to move each file to given directory
        Session session = null;
        Channel channel = null;
        try {
            session = getSession();
            session.connect();
            logger.debug(SESSION_CONNECT);
            channel = session.openChannel(ConfigFields.SFTP_CHANNEL);
            channel.connect();
            logger.debug(CHANNEL_CONNECT);
            ChannelSftp sftp = (ChannelSftp) channel;

            // get absolute remotepath
            String remotePath = getAbsoluteRemotePath(destRelativePath);
            for (String file : fileList) {
                try {
                    moveRemoteFile(file, remotePath, sftp);
                } catch (MoveFileException e) {
                    logger.error("Exception while moving remote file:" + file + " to" + remotePath, e);
                    failedFiles.add(file);
                }
            }

            return failedFiles;

        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    private void moveRemoteFile(String oldFilePath, String remotePath, ChannelSftp sftp) throws MoveFileException {
        // first get file name from full path
        Path path = Paths.get(oldFilePath);

        String fileName = path.getFileName().toString();

        // then add filename to remote path
        String newFilePath = getDestinationFilePath(remotePath, fileName);

        // then try renaming f
        // ile trick
        try {
            sftp.rename(oldFilePath, newFilePath);
            logger.info("Successfully moved file from " + oldFilePath + " to " + newFilePath);
        } catch (SftpException e) {
            String errorMsg = "Could not move file from " + oldFilePath + " to " + newFilePath;
            logger.error(errorMsg, e);
            throw new MoveFileException(errorMsg);
        }

    }

    private String createDirectory(String dirName, ChannelSftp sftp) throws SftpException {
        String absoluteRemotePath = getDestinationFilePath(sftpConfig.getHomePath(), dirName);
        checkAndCreateDirectory(sftp, dirName);
        return absoluteRemotePath;
    }

    private List <String> deleteMultipleFiles(List <String> fileList, ChannelSftp sftp) {

        List <String> failedFiles = new ArrayList <>();

        for (String filePath : fileList) {

            try {
                // check if file exists first
                boolean fileExists = checkIfFileOrFolderExists(filePath, sftp);

                // if not then add it to failed files list
                // then delete file
                if (fileExists) {
                    failedFiles.add(filePath);
                } else {
                    deleteFile(filePath, sftp);
                }
            } catch (Exception e) {
                logger.error("Exception while deleting file:" + filePath, e);
                failedFiles.add(filePath);
            }

        }
        return failedFiles;
    }

    private void deleteFile(String filePath, ChannelSftp sftp) throws FileDeletionException {
        try {
            logger.info("Trying to delete file:" + filePath + " from remote");
            sftp.rm(filePath);
            logger.info("File:" + filePath + " deleted successfully from remote!");
        } catch (SftpException e) {
            String errorMsg = "Error while deleting file:" + filePath + " from remote";
            logger.error(errorMsg, e);
            throw new FileDeletionException(errorMsg);
        }

    }

    private boolean checkIfFileOrFolderExists(String path, ChannelSftp sftp) {
        boolean fileExists = false;

        try {
            logger.info("Checking if path:" + path + " exists on remote.");
            sftp.stat(path);
        } catch (Exception e) {
            logger.error("Path:" + path + " already exists");
            fileExists = true;
        }

        return fileExists;
    }

    /**
     * @param sftp
     * @param localFileList local files to be uploaded
     * @param remoteFolder
     * @return list of files which failed to be uploaded
     */
    private List <String> uploadMultipleFilesToFolder(ChannelSftp sftp, List <String> localFileList,
                                                      String remoteFolder) {

        // add logic to upload files to SFTP HOME FOLDER if it is null

        List <String> failedFiles = new ArrayList <>();
        for (String localFilePath : localFileList) {
            try {
                logger.info("Trying to upload file with local path:" + localFilePath);
                Path filePath = Paths.get(localFilePath);
                String fileName = filePath.getFileName().toString();
                try (InputStream inputStream = new FileInputStream(localFilePath)) {
                    sftp.put(inputStream, fileName);
                }
                logger.info("File:" + fileName + " was uploaded successfully to: " + remoteFolder);
            } catch (Exception e) {
                logger.error("Failed to upload file:" + localFilePath);
                logger.error(e);
                failedFiles.add(localFilePath);
            }
        }
        return failedFiles;
    }

    private String createDirectories(String destRelativePath, ChannelSftp sftp) throws SftpException {
        // get list of folders which need to be created at remote
        String[] folders = destRelativePath.split(Pattern.quote(File.separator));
        logger.debug("Folders list is:" + folders);

        StringBuilder remoteRelativePath = new StringBuilder();
        // create directories if not created already
        for (String folder : folders) {
            remoteRelativePath.append(SftpConstants.FILE_SEPARATOR).append(folder);
            checkAndCreateDirectory(sftp, sftpConfig.getHomePath() + remoteRelativePath.toString());
        }
        return sftpConfig.getHomePath() + remoteRelativePath.toString();
    }

    private void checkAndCreateDirectory(ChannelSftp sftp, String path) throws SftpException {
        try {
            logger.info("Checking if path:" + path + " exists on remote.");
            sftp.stat(path);
        } catch (Exception e) {
            logger.info("Trying to create directory for path:" + path);
            sftp.mkdir(path);
            logger.info("Created path:" + path + "successfully");
        }
    }

    private Map <String, List <String>> downloadAllFiles(String sourcePath, String destRelativePath, ChannelSftp sftp)
            throws ChangeDirectoryException, LsCommandException {

        String remotePath = null;
        // if destination path is null then that means we need to download all files
        // from SFTP HOME
        if (StringUtils.isEmpty(destRelativePath)) {
            changeToHomeDirectory(sftp);
            remotePath = sftpConfig.getHomePath();
        } else {
            // change to the given directory
            remotePath = getAbsoluteRemotePath(destRelativePath);
            changeDirectory(sftp, remotePath);
        }

        // now get all files by ls command ****NOTE USING *.* TO ONLY GET FILES
        // may provide another method in future to download all files within subfolders
        // as well
        try {
            @SuppressWarnings("unchecked")
            Vector <ChannelSftp.LsEntry> fileList = sftp.ls("*.*");
            List <String> sucessFiles = new ArrayList <>();
            List <String> failedFiles = new ArrayList <>();

            if (fileList.isEmpty()) {
                logger.warn("No files found to download on remotePath:" + remotePath);
            }

            for (ChannelSftp.LsEntry file : fileList) {
                String fileName = file.getFilename();
                if (!StringUtils.isEmpty(fileName)) {
                    // ********* ALSO PROVIDE ANOTHER ARGUEMENT SAY OVERRITE FLAG
                    // IF THAT IS TRUE THEN PROCEED TO BELOW OPERATION ELSE ADD THE FILE TO FAILED
                    // LIST
                    downloadAllFiles(sourcePath, remotePath, sftp, sucessFiles, failedFiles, fileName);
                }
            }

            Map <String, List <String>> downloadStatus = new HashMap <>();
            downloadStatus.put(FileTransferResults.SUCCESS, sucessFiles);
            downloadStatus.put(FileTransferResults.FAILURE, failedFiles);
            return downloadStatus;

        } catch (SftpException e) {
            String errorMsg = "Exception while trying to list all files from directory:" + remotePath;
            logger.error(errorMsg);
            logger.error(e);
            throw new LsCommandException(errorMsg);
        }
    }

    private void downloadAllFiles(String sourcePath, String remotePath, ChannelSftp sftp, List <String> sucessFiles,
                                  List <String> failedFiles, String fileName) {
        try {
            String remoteFilePath = remotePath + SftpConstants.FILE_SEPARATOR + fileName;
            downloadFile(sourcePath, fileName, remoteFilePath, sftp);
            sucessFiles.add(remoteFilePath);
        } catch (Exception e) {
            logger.error("Error while downloading remote file" + fileName);
            logger.error(e);
            failedFiles.add(fileName);
        }
    }

    private void downloadFile(String sourcePath, String fileName, String remoteFilePath, ChannelSftp sftp)
            throws SftpException, IOException {
        Path localFilePath = Paths.get(sourcePath, fileName);
        logger.info("Starting download of remote file:" + fileName + " to localPath:" + localFilePath);
        sftp.get(remoteFilePath, Files.newOutputStream(localFilePath));
        logger.info("Downloaded remote file:" + fileName + " successfully to localPath:" + localFilePath);
    }

    /**
     * @param destRelativePath
     * @return absolute remote path relative to the given path which has local os
     * file separator
     */
    private String getAbsoluteRemotePath(String destRelativePath) {

        String[] folders = destRelativePath.split(Pattern.quote(File.separator));
        logger.debug("Relative Folders list is:" + folders);

        StringBuilder remoteRelativePath = new StringBuilder();
        for (String folder : folders) {
            remoteRelativePath.append(SftpConstants.FILE_SEPARATOR).append(folder);
        }
        return sftpConfig.getHomePath() + remoteRelativePath.toString();
    }

    private void changeDirectory(ChannelSftp sftp, String remoteAbsolutePath) throws ChangeDirectoryException {
        try {
            sftp.cd(remoteAbsolutePath);
        } catch (Exception e) {
            String errorMsg = "Error while changing directory to:" + remoteAbsolutePath;
            logger.error(errorMsg);
            logger.error(e);
            throw new ChangeDirectoryException(errorMsg);
        }
    }

    private void changeToHomeDirectory(ChannelSftp sftp) throws ChangeDirectoryException {
        try {
            sftp.cd(sftpConfig.getHomePath());
        } catch (Exception e) {
            String errorMsg = "Error while changing directory to home directory:" + sftpConfig.getHomePath();
            logger.error(errorMsg);
            logger.error(e);
            throw new ChangeDirectoryException(errorMsg);
        }
    }

    private Session getSession() throws JSchException {
        JSch jSch = new JSch();
        Session session = jSch.getSession(sftpConfig.getUserName(), sftpConfig.getHost(), sftpConfig.getPort());
        session.setPassword(sftpConfig.getPassword());
        session.setConfig(ConfigFields.STRICT_HOSTKEY_CHECKING, ConfigValues.DISABLE_STRICT_HOSTKEY_CHECKING);
        return session;
    }

    private String getDestinationFilePath(String destinationPath, String fileName) {
        return destinationPath + SftpConstants.FILE_SEPARATOR + fileName;
    }

    /*
     * Session session = null; Channel channel = null; try { session = getSession();
     * session.connect(); logger.debug(SESSION_CONNECT); channel =
     * session.openChannel(ConfigFields.SFTP_CHANNEL); channel.connect();
     * logger.debug(CHANNEL_CONNECT); ChannelSftp sftp = (ChannelSftp) channel; }
     * finally { if (channel != null) { channel.disconnect(); } if (session != null)
     * { session.disconnect(); } }
     */
}
