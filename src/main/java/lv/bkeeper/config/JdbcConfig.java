package lv.bkeeper.config;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.relational.core.dialect.AnsiDialect;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.data.relational.core.dialect.LimitClause;
import org.springframework.data.relational.core.dialect.LockClause;
import org.springframework.data.relational.core.sql.render.SelectRenderContext;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;

@Configuration
public class JdbcConfig extends AbstractJdbcConfiguration {

    /**
     * SQLite is not natively recognised by Spring Data JDBC.
     * ANSI semantics cover all the SQL we use.
     */
    @Override
    @Bean
    public JdbcDialect jdbcDialect(NamedParameterJdbcOperations operations) {
        return SqliteJdbcDialect.INSTANCE;
    }

    /**
     * Minimal JdbcDialect for SQLite, delegating SQL rendering to ANSI defaults.
     * Spring Data JDBC 4.x requires a JdbcDialect (not just a Dialect).
     */
    static final class SqliteJdbcDialect implements JdbcDialect {

        static final SqliteJdbcDialect INSTANCE = new SqliteJdbcDialect();

        private static final Dialect ANSI = AnsiDialect.INSTANCE;

        @Override
        public LockClause lock() {
            return ANSI.lock();
        }

        @Override
        public LimitClause limit() {
            return ANSI.limit();
        }

        @Override
        public SelectRenderContext getSelectContext() {
            return ANSI.getSelectContext();
        }
    }

    /**
     * Register converters that handle SQLite's datetime format.
     *
     * SQLite stores datetime as "2026-08-16 18:32:58" (space between date and time).
     * Spring Data's default converter expects "2026-08-16T18:32:58" (ISO 8601 with T).
     */
    @Override
    @Bean
    public JdbcCustomConversions jdbcCustomConversions() {
        return new JdbcCustomConversions(List.of(
                new SqliteStringToLocalDateTimeConverter(),
                new LocalDateTimeToSqliteStringConverter(),
                new SqliteStringToLocalDateConverter(),
                new LocalDateToStringConverter(),
                new IntegerToBooleanConverter(),
                new BooleanToIntegerConverter()
        ));
    }

    // ── String → LocalDateTime ────────────────────────────────────────────────

    @ReadingConverter
    static class SqliteStringToLocalDateTimeConverter implements Converter<String, LocalDateTime> {

        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .appendOptional(DateTimeFormatter.ISO_LOCAL_DATE_TIME)          // "2026-08-16T18:32:58"
                .appendOptional(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) // "2026-08-16 18:32:58"
                .appendOptional(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
                .toFormatter();

        @Override
        public LocalDateTime convert(@NonNull String source) {
            return LocalDateTime.parse(source, FORMATTER);
        }
    }

    // ── LocalDateTime → String ────────────────────────────────────────────────

    @WritingConverter
    static class LocalDateTimeToSqliteStringConverter implements Converter<LocalDateTime, String> {

        private static final DateTimeFormatter FORMATTER =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public String convert(@NonNull LocalDateTime source) {
            return source.format(FORMATTER);
        }
    }

    // ── String → LocalDate ────────────────────────────────────────────────────

    @ReadingConverter
    static class SqliteStringToLocalDateConverter implements Converter<String, LocalDate> {

        @Override
        public LocalDate convert(@NonNull String source) {
            String s = source.trim();
            // SQLite may store dates as epoch milliseconds (e.g. "1787086800000")
            if (s.matches("-?\\d{10,}")) {
                return java.time.Instant.ofEpochMilli(Long.parseLong(s))
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate();
            }
            // SQLite may store dates as epoch seconds (e.g. "1787086800")
            if (s.matches("-?\\d{1,10}")) {
                try {
                    return java.time.Instant.ofEpochSecond(Long.parseLong(s))
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate();
                } catch (Exception ignored) {}
            }
            // Strip any time portion SQLite might attach
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        }
    }

    // ── LocalDate → String ────────────────────────────────────────────────────

    @WritingConverter
    static class LocalDateToStringConverter implements Converter<LocalDate, String> {

        @Override
        public String convert(@NonNull LocalDate source) {
            return source.toString(); // ISO-8601: "2026-08-16"
        }
    }

    // ── Integer → boolean (SQLite stores booleans as 0/1) ────────────────────

    @ReadingConverter
    static class IntegerToBooleanConverter implements Converter<Integer, Boolean> {

        @Override
        public Boolean convert(@NonNull Integer source) {
            return source != 0;
        }
    }

    // ── boolean → Integer ─────────────────────────────────────────────────────

    @WritingConverter
    static class BooleanToIntegerConverter implements Converter<Boolean, Integer> {

        @Override
        public Integer convert(@NonNull Boolean source) {
            return source ? 1 : 0;
        }
    }
}
