package com.aceprogrammer.sftputil.config;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * @author Mohammed Salman Shaikh
 *
 */
@Getter
@ToString
@Builder
public class SftpConfig {

    private String host;

    private String userName;

    private String password;

    private int port;

    private String homePath;
}
