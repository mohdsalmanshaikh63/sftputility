# sftputility
**A simple java utility which provides a wrapper over JSCH library to perform some useful sftp operations.**

#### Note:
* Uses [JSCH](http://www.jcraft.com/jsch/) version **0.1.54**
* Uses *commons-logging* version **1.2**
* Uses [lombok][1] with builder pattern

#### Requirements
* Java 8
* Lombok plugin installed as per your **IDE** (Check **Install** section of [lombok][1])

#### Usage with spring boot
Read the mandatory properties into a bean:
```java
@Getter
@Setter
@ToString
@Configuration
public class SftpParams {
	
	@Value("${sftp.host}")
	private String host;
	
	@Value("${sftp.username}")
	private String userName;
	
	@Value("${sftp.password}")
	private String password;
	
	@Value("${sftp.port}")
	private int port;
	
	@Value("${sftp.homepath}")
	private String homePath;

}
```  

Initialize the `SftpService` bean using spring's `@Configuration` :
```java
@Configuration
public class SftpConfigurator {

	@Autowired
	private SftpParams sftpParams;

	@Bean
	public SftpService getSftpService() throws SftpConfigException {
		SftpConfig sftpConfig = SftpConfig.builder().host(sftpParams.getHost()).userName(sftpParams.getUserName())
				.password(sftpParams.getPassword()).port(22).homePath(sftpParams.getHomePath()).build();
		SftpService sftpService = new SftpServiceImpl();
		sftpService.initialize(sftpConfig);
		return sftpService;
	}

}
```

Now you can use the `SftpService` interface methods to perform sftp operations.

Currently, the library supports the following **functions** on remote server:
1. Create a single directory
2. Create multiple directories like Java's `Files.createDirectories(Path)` method if the given path
contains directories not created till terminal directory.
3. **cd** into *home directory*(explained in the next section).
4. Upload a *single file* using **File** object or **local** file path.
5. Upload a list of Files (`List<String> fileList` but not `List<File> fileList`).
6. Upload multiple folderwise files i.e.
    * Folder A - abc.txt, bcd.txt
    * Folder B - def.txt, xyz.txt
7. Download all files from specified remote folder to given local path.
8. Delete a single file with given absolute remote path.
9. Delete multiple files with given *list* of absolute remote file path. 
10. Move multiple files with their absolute file path to a given remote path.

### Points to keep in mind while using this utility:
* `homepath` property should follow sftp protocol file separator i.e. */* and should not end in */*  
i.e. */home/user*
* If the `destRelativePath` variable is passed as null, then it will default to `homepath` in all
the functions.
* After being connected to remote by sftp, each operation will be performed relative to the `homepath`. Hence,
you should pass relative paths to functions with `destRelativePath` arguments.
* All remote paths should always contain sftp file separator i.e. */* since sftp protocol is independent of remote 
operating system.
* Local file paths can use their operating system dependent file separator i.e. *\\* for Windows
and */* for Linux/Mac based operating system since internally Java's NIO `Path` has been used to
handle local file paths which makes this library perform operations to and from Windows/Linux Operating systems.


### Improvements / Suggestions
Due to time constraints, I could not avoid the duplication of creating session and channel code
across all functions. This might be refactored in one way such as:  
Call a common method say `doSftpOperation(OperationType, RequiredArguments)` which internally  
creates session and channel once, then call the specific method based on operation type
with the required arguments along with the `ChannelSftp`. But this will create either lot's of 
`if/switch-case` statements which will create a lot of mess if the functions performed by `SftpService`
increase overtime in future. Hence, any suggestions to improve/tackle this challenge are welcome.  

Currently, in some functions, Session and Channel are created at most twice due to the above mentioned
logic. If the suggested refactoring is done, then this will be avoided.

The `SftpService` interface currently throws exceptions which are tied to JSCH library. This can
be generalized by throwing some common `SftpException` from the interface methods. While the implementation
should take care of catching internal library specific exceptions and throwing the
common `SftpException` instead to hide underlying library.

Also, I'm a big fan of Java 7's 
[try-with-resource](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html)
but unfortunately JSCH objects do not implement `AutoCloseable` interface hence had to `try/finally` blocks to close
resources. While searching for alternatives, I found an awesome library
[Apache MINA SSHD](https://github.com/apache/mina-sshd/blob/master/README.md#apache-mina-sshd) which 
supports try-with-resource. I struggled a lot even to find a simple code to connect to remote server using 
this library. But this might be mostly due to my lacking experience or time. Another implementation or library using
Apache Mina would be welcome!

Feel free to tinker around this code and improve this further!  


[1]: https://projectlombok.org/
