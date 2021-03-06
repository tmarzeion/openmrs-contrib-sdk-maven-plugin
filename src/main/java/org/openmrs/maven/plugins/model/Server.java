package org.openmrs.maven.plugins.model;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.openmrs.maven.plugins.utility.Project;
import org.openmrs.maven.plugins.utility.SDKConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Class for Server model
 */
public class Server {
    // attributes
    public static final String PROPERTY_SERVER_ID = "server.id";
    public static final String PROPERTY_DB_DRIVER = "connection.driver_class";
    public static final String PROPERTY_DB_USER = "connection.username";
    public static final String PROPERTY_DB_PASS = "connection.password";
    public static final String PROPERTY_DB_URI = "connection.url";
    public static final String PROPERTY_VERSION = "openmrs.version";
    public static final String PROPERTY_PLATFORM = "openmrs.platform.version";
    public static final String PROPERTY_DB_NAME = "database_name";
    public static final String PROPERTY_USER_MODULES = "user_modules";
    public static final String PROPERTY_DEMO_DATA = "add_demo_data";
    public static final String PROPERTY_DISTRO_ARTIFACT_ID = "distro.artifactId";
    public static final String PROPERTY_DISTRO_GROUP_ID = "distro.groupId";
    private static final String OLD_PROPERTIES_FILENAME = "backup.properties";

    public static final String COMMA = ",";

    public static final String SLASH = "/";

    private Properties properties;

    private File propertiesFile;

    private File serverDirectory;

    private String interactiveMode;

    public static class ServerBuilder {
        private Server server = new Server();

        public ServerBuilder(Server server){
            this.server.properties = server.properties;
        }

        public ServerBuilder(){}

        public ServerBuilder setInteractiveMode(String nestedInteractiveMode) {
            server.interactiveMode = nestedInteractiveMode;
            return this;
        }

        public ServerBuilder setVersion(String version) {
            server.setVersion(version);
            return this;
        }

        public ServerBuilder setServerId(String serverId) {
            server.setServerId(serverId);
            return this;
        }

        public ServerBuilder setDbDriver(String DbDriver) {
            server.setDbDriver(DbDriver);
            return this;
        }

        public ServerBuilder setDbUri(String DbUri) {
            server.setDbUri(DbUri);
            return this;
        }

        public ServerBuilder setDbUser(String DbUser) {
            server.setDbUser(DbUser);
            return this;
        }

        public ServerBuilder setDbPassword(String DbPassword) {
            server.setDbPassword(DbPassword);
            return this;
        }

        public ServerBuilder setDemoData(boolean is) {
            server.setIncludeDemoData(is);
            return this;
        }

        public Server build() {
            return server;
        }
    }

    private Server() {
        properties = new Properties();
    };

    private Server(File file, Properties properties) {
        if (file != null) {
            this.propertiesFile = new File(file, SDKConstants.OPENMRS_SERVER_PROPERTIES);
            this.serverDirectory = file;
        }
        this.properties = properties;
    }

    public static File getDefaultServersPath(){
        return new File(System.getProperty("user.home"), SDKConstants.OPENMRS_SERVER_PATH);
    }

    public static boolean hasServerConfig(File dir) {
        if (dir.exists()) {
            File properties = new File(dir, SDKConstants.OPENMRS_SERVER_PROPERTIES);
            return properties.exists();
        }

        return false;
    }

    public static Server createServer(File dir) {
        Properties properties = new Properties();
        return new Server(dir, properties);
    }

    public static Server loadServer(String serverId) throws MojoExecutionException {
        File serversPath = getDefaultServersPath();
        File serverPath = new File(serversPath, serverId);
        return loadServer(serverPath);
    }

    public static Server loadServer(File dir) throws MojoExecutionException {
        if (!hasServerConfig(dir)) {
            throw new IllegalArgumentException(SDKConstants.OPENMRS_SERVER_PROPERTIES + " propertiesFile is missing");
        }

        Properties properties = new Properties();
        File config = new File(dir, SDKConstants.OPENMRS_SERVER_PROPERTIES);

        FileInputStream in = null;
        try {
            in = new FileInputStream(config);
            properties.load(in);
            in.close();
        }
        catch (IOException e) {
            throw new MojoExecutionException(e.getMessage());
        }
        finally {
            IOUtils.closeQuietly(in);
        }

        return new Server(dir, properties);
    }

    /**
     * Write properties to propertiesFile
     *
     * @param path
     */
    public void saveTo(File path) throws MojoExecutionException {
        replaceDbNameInDbUri();
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(path);
            properties.store(out, null);
            out.close();
        }
        catch (IOException e) {
            throw new MojoExecutionException(e.getMessage());
        }
        finally {
            IOUtils.closeQuietly(out);
        }
    }

    /**
     * Save properties
     */
    public void save() throws MojoExecutionException {
        saveTo(propertiesFile);
    }

    /**
     * saves current properties in backup file to make restoring server available
     * in case of error occurence during upgrading
     *
     * @throws MojoExecutionException
     */
    public void saveBackupProperties() throws MojoExecutionException {
        File backupProperties = new File(getServerDirectory(), OLD_PROPERTIES_FILENAME);
        saveTo(backupProperties);
    }
    public void deleteBackupProperties() throws MojoExecutionException {
        File backupProperties = new File(getServerDirectory(), OLD_PROPERTIES_FILENAME);
        backupProperties.delete();
    }

    public void delete() {
        propertiesFile.delete();
    }

    public void setUnspecifiedToDefault() {
        if(getDbDriver()!=null){
            setPropertyIfNotSpecified("connection.url",
                    "jdbc:h2:@APPLICATIONDATADIR@/database/@DBNAME@;AUTO_RECONNECT=TRUE;DB_CLOSE_DELAY=-1");
            setPropertyIfNotSpecified("connection.driver_class", "org.h2.Driver");
            setPropertyIfNotSpecified("connection.username", "sa");
            setPropertyIfNotSpecified("connection.password", "sa");
            setPropertyIfNotSpecified("database_name", "openmrs");
            setPropertyIfNotSpecified("has_current_openmrs_database", "true");
            setPropertyIfNotSpecified("create_database_user", "false");
            setPropertyIfNotSpecified("create_tables", "true");
            setPropertyIfNotSpecified("add_demo_data", "false");
            setPropertyIfNotSpecified("auto_update_database", "false");
        }
        setPropertyIfNotSpecified("module_web_admin", "true");
        setPropertyIfNotSpecified("install_method", "auto");
        setPropertyIfNotSpecified("admin_user_password", "Admin123");
    }
    private void setPropertyIfNotSpecified(String key, String value){
        if(properties.getProperty(key)==null){
            properties.setProperty(key, value);
        }
    }

    /**
     * It's a quick fix for OpenMRS, which doesn't pick up the database_name property correctly and
     * doesn't replace DBNAME with the specified value.
     */
    private void replaceDbNameInDbUri() {
        if(getDbUri() != null){
            String dbUri = getDbUri();
            dbUri = dbUri.replace("@DBNAME@", getParam(Server.PROPERTY_DB_NAME));
            setParam(Server.PROPERTY_DB_URI, dbUri);
        }
    }

    public boolean addWatchedProject(Project project) {
        Set<Project> watchedProjects = getWatchedProjects();
        if (watchedProjects.add(project)) {
            setWatchedProjects(watchedProjects);
            return true;
        } else {
            return false;
        }
    }

    private void setWatchedProjects(Set<Project> watchedProjects) {
        List<String> list = new ArrayList<String>();
        for (Project watchedProject : watchedProjects) {
            list.add(String.format("%s,%s,%s", watchedProject.getGroupId(),
                    watchedProject.getArtifactId(), watchedProject.getPath()));
        }
        properties.setProperty("watched.projects", StringUtils.join(list.iterator(), ";"));
    }

    public void clearWatchedProjects() {
        setWatchedProjects(new LinkedHashSet<Project>());
    }

    public Project removeWatchedProjectByExample(Project project) {
        Set<Project> watchedProjects = getWatchedProjects();
        if (watchedProjects.remove(project)) {
            return project;
        } else {
            for (Iterator<Project> it = watchedProjects.iterator(); it.hasNext();) {
                Project candidate = it.next();
                if (candidate.matches(project)) {
                    it.remove();
                    setWatchedProjects(watchedProjects);

                    return candidate;
                }
            }
            return null;
        }
    }

    public Set<Project> getWatchedProjects() {
        String watchedProjectsProperty = properties.getProperty("watched.projects");
        if (StringUtils.isBlank(watchedProjectsProperty)) {
            return new LinkedHashSet<Project>();
        }

        Set<Project> watchedProjects = new LinkedHashSet<Project>();
        for (String watchedProjectProperty : watchedProjectsProperty.split(";")) {
            if (StringUtils.isBlank(watchedProjectProperty)) {
                continue;
            }

            String[] watchedProject = watchedProjectProperty.split(",");
            Project project = new Project(watchedProject[0], watchedProject[1], null, watchedProject[2]);
            watchedProjects.add(project);
        }
        return watchedProjects;
    }

    public boolean hasWatchedProjects() {
        return !getWatchedProjects().isEmpty();
    }

    /**
     * adds artifact to user modules list in installation.properties file
     */
    public void saveUserModule(Artifact artifact) throws MojoExecutionException {
        String[] params = {artifact.getGroupId(), StringUtils.removeEnd(artifact.getArtifactId(), "-omod"), artifact.getVersion()};
        String module = StringUtils.join(params, "/");
        addToValueList(Server.PROPERTY_USER_MODULES, module);
    }

    /**
     * removes artifact from user modules list in installation.properties
     */
    public boolean removeUserModule(Artifact artifact) throws MojoExecutionException {
        List<Artifact> userModules = getUserModules();
        if(userModules.contains(artifact)){
            userModules.remove(artifact);
            setUserModules(userModules);
            return true;
        } else {
            return false;
        }
    }

    public void setUserModules(Collection<Artifact> artifacts) throws MojoExecutionException {
        properties.remove(Server.PROPERTY_USER_MODULES);
        for(Artifact artifact : artifacts){
            saveUserModule(artifact);
        }
    }

    public List<Artifact> getUserModules() throws MojoExecutionException {
        String values = getParam(Server.PROPERTY_USER_MODULES);
        List<Artifact> result = new ArrayList<>();
        if (values != null && !values.equals("")) {
            String[] modules = values.split(Server.COMMA);
            for (String mod: modules) {
                if(!mod.isEmpty()){
                    String[] params = mod.split(Server.SLASH);
                    // check
                    if (params.length == 3) {
                        result.add(new Artifact(params[1], params[2], params[0]));
                    }
                    else throw new MojoExecutionException("Properties file parse error - cannot read user modules list");
                }
            }
        }
        return result;
    }

    /**
     * Get artifacts of core and all modules on server
     */
    public List<Artifact> getServerModules() {
        List<Artifact> artifacts = new ArrayList<>();
        File mainDir = serverDirectory;
        File modulesDir = new File(serverDirectory, SDKConstants.OPENMRS_SERVER_MODULES);
        artifacts.addAll(getArtifactsFromDir(mainDir));
        artifacts.addAll(getArtifactsFromDir(modulesDir));
        return artifacts;
    }
    private List<Artifact> getArtifactsFromDir(File dir){
        List<Artifact> artifacts = new ArrayList<>();
        if(dir.listFiles()!=null){
            for (File file: dir.listFiles()) {
                String type = getArtifactType(file.getName());
                if (SDKConstants.isExtensionSupported(type)) {
                    String artifactId = getArtifactId(file.getName());
                    String version = Version.parseVersionFromFile(file.getName());
                    Artifact artifact = new Artifact(artifactId, version);
                    if(type.equals(Artifact.TYPE_WAR)){
                        artifact.setGroupId(Artifact.GROUP_WEB);
                    }
                    artifact.setType(type);
                    artifact.setFileExtension(type);
                    artifact.setDestFileName(file.getName());
                    artifacts.add(artifact);
                }
            }
        }
        return artifacts;
    }
    public String getArtifactId(String filename) {
        int index = filename.indexOf('-');
        if (index == -1){
            return filename;
        } else {
            String id = filename.substring(0, index);
            if ("openmrs".equals(id)) {
                return SDKConstants.WEBAPP_ARTIFACT_ID;
            }
            else return id;
        }
    }

    public String getArtifactType(String filename) {
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    /**
     * Get param from properties
     *
     * @param key - property key
     * @return - property value
     */
    public String getParam(String key) {
        return properties.getProperty(key);
    }

    /**
     * Set param to properties object (without applying)
     *
     * @param key - property key
     * @param value - value to set
     */
    public void setParam(String key, String value) {
        if(key != null && value != null)
        properties.setProperty(key, value);
    }

    /**
     * Add value to value list for a selected key
     *
     * @param key
     * @param value
     */
    public void addToValueList(String key, String value) {
        String beforeValue = properties.getProperty(key);
        if (StringUtils.isBlank(beforeValue))
            beforeValue = value;
        else {
            List<String> values = new ArrayList<String>(edu.emory.mathcs.backport.java.util.Arrays.asList(beforeValue.split(COMMA)));
            for (String val : values) {
                if (val.equals(value))
                    return;
            }
            values.add(value);
            beforeValue = StringUtils.join(values.toArray(), COMMA);
        }
        properties.setProperty(key, beforeValue);
    }

    /**
     * Remove value from value list for a selected key
     *
     * @param key
     * @param artifactId
     */
    public void removeFromValueList(String key, String artifactId) {
        String beforeValue = properties.getProperty(key);
        if (beforeValue == null)
            return;
        else {
            List<String> values = new ArrayList<String>(edu.emory.mathcs.backport.java.util.Arrays.asList(beforeValue.split(COMMA)));
            int indx = -1;
            for (String val : values) {
                String[] params = val.split(SLASH);
                if (params[1].equals(artifactId)) {
                    indx = values.indexOf(val);
                    break;
                }
            }
            if (indx != -1)
                values.remove(indx);
            if (values.size() == 0)
                properties.remove(key);
            else {
                beforeValue = StringUtils.join(values.toArray(), COMMA);
                properties.setProperty(key, beforeValue);
            }

        }
    }

    public String getServerId() {
        return getParam(PROPERTY_SERVER_ID);
    }

    public void setServerId(String serverId) {
        setParam(PROPERTY_SERVER_ID, serverId);
    }

    public String getDbDriver() {
        return getParam(PROPERTY_DB_DRIVER);
    }

    public void setDbDriver(String dbDriver) {
        setParam(PROPERTY_DB_DRIVER, dbDriver);
    }

    public String getDbUri() {
        return getParam(PROPERTY_DB_URI);
    }

    public void setDbUri(String dbUri) {
        setParam(PROPERTY_DB_URI, dbUri);
    }

    public String getDbUser() {
        return getParam(PROPERTY_DB_USER);
    }

    public void setDbUser(String dbUser) {
        setParam(PROPERTY_DB_USER, dbUser);
    }

    public String getDbPassword() {
        return getParam(PROPERTY_DB_PASS);
    }

    public void setDbPassword(String dbPassword) {
        setParam(PROPERTY_DB_PASS, dbPassword);
    }

    public String getInteractiveMode() { return interactiveMode; }

    public void setInteractiveMode(String interactiveMode) { this.interactiveMode = interactiveMode; }

    public String getVersion() { return getParam(PROPERTY_VERSION); }

    public void setVersion(String version) { setParam(PROPERTY_VERSION, version); }

    public boolean isIncludeDemoData() {
        return Boolean.parseBoolean(getParam(PROPERTY_DEMO_DATA));
    }

    public void setIncludeDemoData(boolean includeDemoData) {
        setParam(PROPERTY_DEMO_DATA, String.valueOf(includeDemoData));
    }

    public String getPlatformVersion(){
        return getParam(PROPERTY_PLATFORM);
    }
    public void setPlatformVersion(String platformVersion){
        setParam(PROPERTY_PLATFORM, platformVersion);
    }

    public String getDbName(){
        return getParam(PROPERTY_DB_NAME);
    }
    public void setDbName(String dbName){
        setParam(PROPERTY_DB_NAME, dbName);
    }

    public void setServerDirectory(File dir) {
        this.serverDirectory = dir;
        this.propertiesFile = new File(dir, SDKConstants.OPENMRS_SERVER_PROPERTIES);
    }

    public File getPropertiesFile() {
        return propertiesFile;
    }

    public File getServerDirectory() {
        return serverDirectory;
    }

    public  void setDistroArtifactId(String artifactId){
        setParam(PROPERTY_DISTRO_ARTIFACT_ID, artifactId);
    }

    public void setDistroGroupId(String groupId){
        setParam(PROPERTY_DISTRO_GROUP_ID, groupId);
    }

    public String getDistroArtifactId(){
        return getParam(PROPERTY_DISTRO_ARTIFACT_ID);
    }

    public String getDistroGroupId(){
        return getParam(PROPERTY_DISTRO_GROUP_ID);
    }

    public File getServerTmpDirectory() {
        return new File(serverDirectory, "tmp");
    }

    public void deleteServerTmpDirectory() {
        File tmpDirectory = getServerTmpDirectory();
        if (tmpDirectory.exists()) {
            try {
                FileUtils.deleteDirectory(tmpDirectory);
            } catch (IOException e) {
                System.out.println("Could not delete tmp directory");
            }
        }
    }
}
