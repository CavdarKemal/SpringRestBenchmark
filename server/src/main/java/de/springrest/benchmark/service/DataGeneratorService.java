package de.springrest.benchmark.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Befuellt die Tabelle {@code measurements} mit synthetischen Testdaten.
 *
 * <p>Dies ist <strong>Infrastruktur</strong>, kein Benchmark: Damit Studenten schnell
 * Millionen Zeilen erzeugen koennen, nutzt der Generator selbst bereits die effiziente
 * Technik des JDBC-Batch-Inserts. Die schrittweise Entwicklung dieser Technik ist Thema
 * der Write-Stufen (W0..W3); hier geht es nur um zuegiges Seeding.</p>
 */
@Service
public class DataGeneratorService {

    /** Feste Kategorien mit niedriger Kardinalitaet (gut fuer Gruppierung/Caching). */
    private static final String[] CATEGORIES = {"TEMP", "PRESSURE", "HUMIDITY", "VIBRATION", "FLOW"};

    /** Anzahl Sensoren -> bestimmt die Kardinalitaet von sensor_id. */
    private static final int SENSOR_COUNT = 500;

    /** Zeilen pro JDBC-Batch. Bewusster Kompromiss aus Speicher und Round-Trip-Ersparnis. */
    private static final int BATCH_SIZE = 1_000;

    private static final String INSERT_SQL = """
            INSERT INTO measurements (ts, sensor_id, category, v1, v2, v3, v4, v5, v6, v7, v8, payload)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public DataGeneratorService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Erzeugt {@code rows} zufaellige Messungen.
     *
     * @param rows            Anzahl zu erzeugender Zeilen
     * @param clearBefore     wenn {@code true}, wird die Tabelle vorher geleert
     * @param payloadLength   Laenge des optionalen Text-Payloads je Zeile (0 = kein Payload)
     * @return Anzahl tatsaechlich eingefuegter Zeilen
     */
    @Transactional
    public long generate(long rows, boolean clearBefore, int payloadLength) {
        if (clearBefore) {
            clear();
        }

        long inserted = 0;
        int batchCount = 0;
        // Wir sammeln bis BATCH_SIZE Zeilen und schicken sie als ein Batch an die DB.
        Object[][] buffer = new Object[BATCH_SIZE][];

        for (long i = 0; i < rows; i++) {
            buffer[batchCount++] = randomRow(payloadLength);
            if (batchCount == BATCH_SIZE) {
                inserted += flush(buffer, batchCount);
                batchCount = 0;
            }
        }
        if (batchCount > 0) {
            inserted += flush(buffer, batchCount);
        }
        return inserted;
    }

    /** Loescht alle Zeilen (schnelles TRUNCATE inkl. Reset der ID-Sequenz). */
    @Transactional
    public void clear() {
        jdbcTemplate.execute("TRUNCATE TABLE measurements RESTART IDENTITY");
    }

    /** Aktuelle Zeilenanzahl. */
    public long count() {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM measurements", Long.class);
        return c != null ? c : 0L;
    }

    // --- intern --------------------------------------------------------------

    private long flush(Object[][] buffer, int size) {
        int[] updated = jdbcTemplate.batchUpdate(INSERT_SQL, new java.util.AbstractList<Object[]>() {
            @Override public Object[] get(int index) { return buffer[index]; }
            @Override public int size() { return size; }
        });
        long sum = 0;
        for (int u : updated) {
            // pgjdbc liefert bei reWriteBatchedInserts teils Statement.SUCCESS_NO_INFO (-2);
            // in diesem Fall zaehlen wir die Zeile trotzdem als eingefuegt.
            sum += (u >= 0) ? u : 1;
        }
        return sum;
    }

    private Object[] randomRow(int payloadLength) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        // Zeitstempel innerhalb der letzten ~365 Tage streuen.
        long secondsBack = rnd.nextLong(0, 365L * 24 * 3600);
        OffsetDateTime ts = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(secondsBack);

        return new Object[]{
                Timestamp.from(ts.toInstant()),
                rnd.nextInt(SENSOR_COUNT),
                CATEGORIES[rnd.nextInt(CATEGORIES.length)],
                rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble(),
                rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble(),
                payloadLength > 0 ? randomText(payloadLength, rnd) : null
        };
    }

    private String randomText(int length, ThreadLocalRandom rnd) {
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = (char) ('a' + rnd.nextInt(26));
        }
        return new String(chars);
    }
}
