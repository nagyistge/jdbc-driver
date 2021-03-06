package foundation.stack.jdbc;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public class DelegatingDriver implements Driver {
    private static final Logger logger = Logger.getLogger(DelegatingDriver.class.getName());
    private static final String PREFIX = "jdbc:sf";
    private static final int PREFIX_LENGTH = PREFIX.length();

    private static final int MAJOR = 1;
    private static final int MINOR = 0;

    private static void deregistered() {
        logger.log(Level.INFO, "stack.foundation JDBC Driver de-registered");
    }

    static {
        try {
            logger.log(Level.INFO, "stack.foundation JDBC Driver registered");
            DriverManager.registerDriver(new DelegatingDriver(), () -> deregistered());
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error registering stack.foundation JDBC Driver", e);
        }
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return url.length() > PREFIX_LENGTH && url.startsWith(PREFIX);
    }

    private Connection delegatedConnect(ConnectionLookupResult lookupResult, Properties info) throws SQLException {
        logger.log(Level.INFO, "Attempting to delegate to {0}", lookupResult.getConnectionString());

        fillInDefaultCredentialsIfRequired(lookupResult, info);

        return DriverManager.getConnection(lookupResult.getConnectionString(), info);
    }

    private void fillInDefaultCredentialsIfRequired(ConnectionLookupResult lookupResult, Properties info) {
        if (!info.containsKey("user")) {
            ConnectionLookup lookup = lookupResult.getLookup();

            String user = lookup.getDefaultUsername();
            if (user != null) {
                info.put("user", user);
            }
        }

        if (!info.containsKey("password")) {
            ConnectionLookup lookup = lookupResult.getLookup();

            String password = lookup.getDefaultPassword();
            if (password != null) {
                info.put("password", password);
            }
        }
    }

    private ConnectionLookupResult lookup(String url) {
        logger.log(Level.INFO, "Finding connection string to use for delegating URL {0}", url);

        String query = url.substring(PREFIX_LENGTH + 1);

        try {
            ConnectionLookupResult lookupResult = ConnectionLookupRegistry.getRegistry().lookupConnection(query);
            if (lookupResult != null) {
                return lookupResult;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error occurred looking up connection to delegate {0} to", url);
            logger.log(Level.FINE, "Error details:", e);
        }

        logger.log(Level.WARNING, "Unable to find a connection string to use for delegating URL {0}", url);
        return null;
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (acceptsURL(url)) {
            ConnectionLookupResult lookupResult = lookup(url);
            if (lookupResult != null) {
                return delegatedConnect(lookupResult, info);
            }
        }

        return null;
    }

    @Override
    public int getMajorVersion() {
        return MAJOR;
    }

    @Override
    public int getMinorVersion() {
        return MINOR;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return logger.getLogger(DelegatingDriver.class.getPackage().getName());
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        if (acceptsURL(url)) {
            ConnectionLookupResult lookupResult = lookup(url);
            if (lookupResult != null) {
                return DriverManager.getDriver(url).getPropertyInfo(url, info);
            }
        }

        return new DriverPropertyInfo[0];
    }
}
