package de.springrest.benchmark.common;

import com.zaxxer.hikari.HikariDataSource;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

import javax.sql.DataSource;

/**
 * Hilfsfunktion, um eine reaktive R2DBC-{@link ConnectionFactory} aus einer bestehenden JDBC-DataSource
 * abzuleiten.
 *
 * <p><strong>Warum kein Spring-Bean?</strong> Boots {@code DataSourceAutoConfiguration} ist
 * {@code @ConditionalOnMissingBean(ConnectionFactory)} — eine ConnectionFactory-Bean wuerde die JDBC-DataSource
 * (und damit JPA/Flyway) abschalten. Deshalb bauen die Stufen W8/R8 ihre ConnectionFactory als privates Feld
 * ueber diese Utility, statt sie als Bean zu registrieren.</p>
 */
public final class R2dbcSupport {

    private R2dbcSupport() {
    }

    /**
     * Baut einen R2DBC-Verbindungspool aus der JDBC-URL der uebergebenen DataSource:
     * aus {@code jdbc:postgresql://host:port/db?..} wird {@code r2dbc:postgresql://host:port/db}.
     *
     * @param dataSource die (Hikari-)JDBC-DataSource, aus der URL/Zugangsdaten stammen
     * @param maxSize    maximale Poolgroesse
     * @param poolName   Name des Pools (fuer Logs/Metriken)
     */
    public static ConnectionFactory pooledConnectionFactory(DataSource dataSource, int maxSize, String poolName) {
        HikariDataSource source = (HikariDataSource) dataSource;
        String jdbcUrl = source.getJdbcUrl();
        String withoutPrefix = jdbcUrl.substring("jdbc:".length());
        int queryIndex = withoutPrefix.indexOf('?');
        if (queryIndex >= 0) {
            withoutPrefix = withoutPrefix.substring(0, queryIndex);
        }
        String r2dbcUrl = "r2dbc:" + withoutPrefix;

        ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
                .from(ConnectionFactoryOptions.parse(r2dbcUrl))
                .option(ConnectionFactoryOptions.USER, source.getUsername())
                .option(ConnectionFactoryOptions.PASSWORD, source.getPassword())
                .build();
        ConnectionFactory base = ConnectionFactories.get(options);

        ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration.builder(base)
                .name(poolName)
                .maxSize(maxSize)
                .build();
        return new ConnectionPool(configuration);
    }
}
